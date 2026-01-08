package com.po4yka.framelapse.platform

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.po4yka.framelapse.domain.service.GifEncoder
import com.po4yka.framelapse.domain.service.ImageData
import com.po4yka.framelapse.domain.util.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Android implementation of GIF encoder using built-in GIF89a encoding.
 */
@Suppress("MagicNumber", "TooManyFunctions")
class GifEncoderImpl(private val context: Context) : GifEncoder {

    private val encodingInProgress = AtomicBoolean(false)
    private val cancelRequested = AtomicBoolean(false)

    override val isEncoding: Boolean
        get() = encodingInProgress.get()

    override suspend fun encode(
        frames: List<ImageData>,
        outputPath: String,
        delayMs: Int,
        onProgress: (Float) -> Unit,
    ): Result<String> = withContext(Dispatchers.Default) {
        if (encodingInProgress.get()) {
            return@withContext Result.Error(
                IllegalStateException("Encoding already in progress"),
                "Encoding already in progress",
            )
        }

        if (frames.isEmpty()) {
            return@withContext Result.Error(
                IllegalArgumentException("No frames to encode"),
                "No frames provided",
            )
        }

        encodingInProgress.set(true)
        cancelRequested.set(false)

        var outputStream: OutputStream? = null

        try {
            val outputFile = File(outputPath)
            outputFile.parentFile?.mkdirs()

            outputStream = BufferedOutputStream(FileOutputStream(outputFile))

            val firstFrame = frames.first()
            val width = firstFrame.width
            val height = firstFrame.height

            // Initialize GIF encoder
            val encoder = AnimatedGifWriter(outputStream, width, height, delayMs / 10)
            encoder.writeHeader()

            frames.forEachIndexed { index, imageData ->
                if (!isActive || cancelRequested.get()) {
                    encoder.finish()
                    outputStream.close()
                    outputFile.delete()
                    return@withContext Result.Error(
                        InterruptedException("Encoding cancelled"),
                        "Encoding was cancelled",
                    )
                }

                val bitmap = imageDataToBitmap(imageData, width, height)
                if (bitmap != null) {
                    encoder.addFrame(bitmap)
                    bitmap.recycle()
                }

                onProgress((index + 1).toFloat() / frames.size)
            }

            encoder.finish()
            outputStream.flush()

            Result.Success(outputPath)
        } catch (e: Exception) {
            File(outputPath).delete()
            Result.Error(e, "GIF encoding failed: ${e.message}")
        } finally {
            try {
                outputStream?.close()
            } catch (e: Exception) {
                // Ignore cleanup errors
            }
            encodingInProgress.set(false)
        }
    }

    override fun cancel(): Result<Unit> {
        if (!encodingInProgress.get()) {
            return Result.Success(Unit)
        }
        cancelRequested.set(true)
        return Result.Success(Unit)
    }

    private fun imageDataToBitmap(imageData: ImageData, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val options = BitmapFactory.Options().apply {
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            var bitmap = BitmapFactory.decodeByteArray(imageData.bytes, 0, imageData.bytes.size, options)
                ?: return null

            if (bitmap.width != targetWidth || bitmap.height != targetHeight) {
                val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
                if (scaled != bitmap) {
                    bitmap.recycle()
                }
                scaled
            } else {
                bitmap
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Animated GIF writer implementing GIF89a specification.
     * Based on NeuQuant neural network quantization for color reduction.
     */
    @Suppress("LongParameterList", "NestedBlockDepth", "ComplexMethod", "LongMethod")
    private class AnimatedGifWriter(
        private val out: OutputStream,
        private val width: Int,
        private val height: Int,
        private val delay: Int,
    ) {
        private var firstFrame = true
        private var colorTab: ByteArray? = null
        private var usedColors: IntArray? = null
        private var colorDepth = 8
        private var palSize = 7

        fun writeHeader() {
            // GIF89a header
            out.write("GIF89a".toByteArray())
        }

        fun addFrame(bitmap: Bitmap) {
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            analyzePixels(pixels)

            if (firstFrame) {
                writeLSD()
                writeNetscapeExt()
                firstFrame = false
            }

            writeGraphicCtrlExt()
            writeImageDesc()
            writePalette()
            writePixels(pixels)
        }

        fun finish() {
            out.write(0x3B) // GIF trailer
        }

        private fun analyzePixels(pixels: IntArray) {
            // Build color palette using median cut algorithm (simplified)
            val colorMap = mutableMapOf<Int, Int>()

            for (pixel in pixels) {
                val color = pixel or 0xFF000000.toInt() // Ensure alpha
                colorMap[color] = (colorMap[color] ?: 0) + 1
            }

            // Sort by frequency and take top 256 colors
            val sortedColors = colorMap.entries
                .sortedByDescending { it.value }
                .take(256)
                .map { it.key }
                .toIntArray()

            usedColors = sortedColors
            colorTab = ByteArray(256 * 3)

            for (i in sortedColors.indices) {
                val color = sortedColors[i]
                colorTab!![i * 3] = ((color shr 16) and 0xFF).toByte()
                colorTab!![i * 3 + 1] = ((color shr 8) and 0xFF).toByte()
                colorTab!![i * 3 + 2] = (color and 0xFF).toByte()
            }

            // Pad remaining palette entries
            for (i in sortedColors.size until 256) {
                colorTab!![i * 3] = 0
                colorTab!![i * 3 + 1] = 0
                colorTab!![i * 3 + 2] = 0
            }
        }

        private fun writeLSD() {
            // Logical Screen Descriptor
            writeShort(width)
            writeShort(height)

            // Global Color Table Flag = 0 (no global table, use local)
            out.write(0x00)
            out.write(0x00) // Background color index
            out.write(0x00) // Pixel aspect ratio
        }

        private fun writeNetscapeExt() {
            // Application Extension for looping
            out.write(0x21) // Extension introducer
            out.write(0xFF) // Application extension
            out.write(11) // Block size
            out.write("NETSCAPE2.0".toByteArray())
            out.write(3) // Sub-block size
            out.write(1) // Loop indicator
            writeShort(0) // Loop count (0 = infinite)
            out.write(0) // Block terminator
        }

        private fun writeGraphicCtrlExt() {
            out.write(0x21) // Extension introducer
            out.write(0xF9) // Graphic control label
            out.write(4) // Block size

            // Packed field: disposal = 0, user input = 0, transparent = 0
            out.write(0x00)
            writeShort(delay) // Delay time in hundredths of second
            out.write(0) // Transparent color index
            out.write(0) // Block terminator
        }

        private fun writeImageDesc() {
            out.write(0x2C) // Image separator
            writeShort(0) // Image left
            writeShort(0) // Image top
            writeShort(width)
            writeShort(height)

            // Packed field: local color table = 1, interlace = 0, sort = 0, size = 7
            out.write(0x87)
        }

        private fun writePalette() {
            colorTab?.let { out.write(it) }
        }

        private fun writePixels(pixels: IntArray) {
            val indexedPixels = ByteArray(pixels.size)

            // Map pixels to palette indices
            for (i in pixels.indices) {
                indexedPixels[i] = findClosestColor(pixels[i]).toByte()
            }

            // LZW encode
            val encoder = LZWEncoder(width, height, indexedPixels, colorDepth)
            encoder.encode(out)
        }

        private fun findClosestColor(pixel: Int): Int {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF

            val colors = usedColors ?: return 0

            var minDist = Int.MAX_VALUE
            var minIndex = 0

            for (i in colors.indices) {
                val cr = (colors[i] shr 16) and 0xFF
                val cg = (colors[i] shr 8) and 0xFF
                val cb = colors[i] and 0xFF

                val dist = (r - cr) * (r - cr) + (g - cg) * (g - cg) + (b - cb) * (b - cb)
                if (dist < minDist) {
                    minDist = dist
                    minIndex = i
                }
            }

            return minIndex
        }

        private fun writeShort(value: Int) {
            out.write(value and 0xFF)
            out.write((value shr 8) and 0xFF)
        }
    }

    /**
     * LZW encoder for GIF image data.
     */
    @Suppress("MagicNumber", "NestedBlockDepth", "ComplexMethod", "LongMethod")
    private class LZWEncoder(
        private val imgW: Int,
        private val imgH: Int,
        private val pixels: ByteArray,
        private val colorDepth: Int,
    ) {
        private val initCodeSize = maxOf(2, colorDepth)
        private var curAccum = 0
        private var curBits = 0
        private var aCount = 0
        private val accum = ByteArray(256)

        private var nBits = 0
        private var maxCode = 0
        private var freeEnt = 0
        private var clearFlag = false
        private var gInitBits = 0
        private var clearCode = 0
        private var eofCode = 0

        private val htab = IntArray(HSIZE)
        private val codetab = IntArray(HSIZE)

        fun encode(output: OutputStream) {
            output.write(initCodeSize)

            gInitBits = initCodeSize + 1
            clearFlag = false
            nBits = gInitBits
            maxCode = maxCode(nBits)

            clearCode = 1 shl (initCodeSize)
            eofCode = clearCode + 1
            freeEnt = clearCode + 2

            aCount = 0

            htab.fill(-1)

            var ent = pixels[0].toInt() and 0xFF

            var hshift = 0
            var fcode = HSIZE
            while (fcode < 65536) {
                hshift++
                fcode *= 2
            }
            hshift = 8 - hshift

            outputCode(clearCode, output)

            for (i in 1 until pixels.size) {
                val c = pixels[i].toInt() and 0xFF
                fcode = (c shl MAXBITS) + ent
                var idx = (c shl hshift) xor ent

                if (htab[idx] == fcode) {
                    ent = codetab[idx]
                    continue
                } else if (htab[idx] >= 0) {
                    val disp = if (idx == 0) 1 else HSIZE - idx
                    do {
                        idx -= disp
                        if (idx < 0) idx += HSIZE
                        if (htab[idx] == fcode) {
                            ent = codetab[idx]
                            break
                        }
                    } while (htab[idx] >= 0)
                    if (htab[idx] == fcode) continue
                }

                outputCode(ent, output)
                ent = c

                if (freeEnt < MAXMAXCODE) {
                    codetab[idx] = freeEnt++
                    htab[idx] = fcode
                } else {
                    clearBlock(output)
                }
            }

            outputCode(ent, output)
            outputCode(eofCode, output)

            // Flush remaining bits
            if (curBits > 0) {
                charOut((curAccum and 0xFF).toByte(), output)
            }

            flushChar(output)
            output.write(0) // Block terminator
        }

        private fun maxCode(nBits: Int): Int = (1 shl nBits) - 1

        private fun outputCode(code: Int, output: OutputStream) {
            curAccum = curAccum or (code shl curBits)
            curBits += nBits

            while (curBits >= 8) {
                charOut((curAccum and 0xFF).toByte(), output)
                curAccum = curAccum shr 8
                curBits -= 8
            }

            if (freeEnt > maxCode || clearFlag) {
                if (clearFlag) {
                    nBits = gInitBits
                    maxCode = maxCode(nBits)
                    clearFlag = false
                } else {
                    nBits++
                    maxCode = if (nBits == MAXBITS) MAXMAXCODE else maxCode(nBits)
                }
            }
        }

        private fun clearBlock(output: OutputStream) {
            htab.fill(-1)
            freeEnt = clearCode + 2
            clearFlag = true
            outputCode(clearCode, output)
        }

        private fun charOut(c: Byte, output: OutputStream) {
            accum[aCount++] = c
            if (aCount >= 254) {
                flushChar(output)
            }
        }

        private fun flushChar(output: OutputStream) {
            if (aCount > 0) {
                output.write(aCount)
                output.write(accum, 0, aCount)
                aCount = 0
            }
        }

        companion object {
            private const val MAXBITS = 12
            private const val MAXMAXCODE = 1 shl MAXBITS
            private const val HSIZE = 5003
        }
    }
}
