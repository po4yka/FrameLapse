package com.po4yka.framelapse.domain.entity

import com.po4yka.framelapse.testutil.TestFixtures
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomographyMatrixTest {

    @BeforeTest
    fun setup() {
        TestFixtures.resetCounters()
    }

    private val epsilon = 0.001f

    private fun assertFloatEquals(expected: Float, actual: Float, message: String? = null) {
        assertTrue(
            abs(expected - actual) < epsilon,
            message ?: "Expected $expected but was $actual (diff: ${abs(expected - actual)})",
        )
    }

    // ==================== Factory Methods Tests ====================

    @Test
    fun `fromFloatArray creates matrix from 9 values`() {
        // Given
        val values = floatArrayOf(
            1f, 2f, 3f,
            4f, 5f, 6f,
            7f, 8f, 9f,
        )

        // When
        val matrix = HomographyMatrix.fromFloatArray(values)

        // Then
        assertEquals(1f, matrix.h11)
        assertEquals(2f, matrix.h12)
        assertEquals(3f, matrix.h13)
        assertEquals(4f, matrix.h21)
        assertEquals(5f, matrix.h22)
        assertEquals(6f, matrix.h23)
        assertEquals(7f, matrix.h31)
        assertEquals(8f, matrix.h32)
        assertEquals(9f, matrix.h33)
    }

    @Test
    fun `fromFloatArray throws for wrong size`() {
        // Given
        val wrongSizeArray = floatArrayOf(1f, 2f, 3f, 4f, 5f)

        // When/Then
        assertFailsWith<IllegalArgumentException> {
            HomographyMatrix.fromFloatArray(wrongSizeArray)
        }
    }

    @Test
    fun `fromDoubleArray creates matrix from 9 doubles`() {
        // Given
        val values = doubleArrayOf(
            1.5, 2.5, 3.5,
            4.5, 5.5, 6.5,
            7.5, 8.5, 9.5,
        )

        // When
        val matrix = HomographyMatrix.fromDoubleArray(values)

        // Then
        assertFloatEquals(1.5f, matrix.h11)
        assertFloatEquals(2.5f, matrix.h12)
        assertFloatEquals(3.5f, matrix.h13)
        assertFloatEquals(4.5f, matrix.h21)
        assertFloatEquals(5.5f, matrix.h22)
        assertFloatEquals(6.5f, matrix.h23)
        assertFloatEquals(7.5f, matrix.h31)
        assertFloatEquals(8.5f, matrix.h32)
        assertFloatEquals(9.5f, matrix.h33)
    }

    @Test
    fun `translation creates correct translation matrix`() {
        // Given
        val tx = 10f
        val ty = 20f

        // When
        val matrix = HomographyMatrix.translation(tx, ty)

        // Then
        assertEquals(1f, matrix.h11) // No scale X
        assertEquals(0f, matrix.h12) // No shear
        assertEquals(tx, matrix.h13) // Translation X
        assertEquals(0f, matrix.h21) // No shear
        assertEquals(1f, matrix.h22) // No scale Y
        assertEquals(ty, matrix.h23) // Translation Y
        assertEquals(0f, matrix.h31) // No perspective
        assertEquals(0f, matrix.h32) // No perspective
        assertEquals(1f, matrix.h33) // Homogeneous coordinate
    }

    @Test
    fun `scale creates correct scaling matrix`() {
        // Given
        val sx = 2f
        val sy = 3f

        // When
        val matrix = HomographyMatrix.scale(sx, sy)

        // Then
        assertEquals(sx, matrix.h11) // Scale X
        assertEquals(0f, matrix.h12)
        assertEquals(0f, matrix.h13)
        assertEquals(0f, matrix.h21)
        assertEquals(sy, matrix.h22) // Scale Y
        assertEquals(0f, matrix.h23)
        assertEquals(0f, matrix.h31)
        assertEquals(0f, matrix.h32)
        assertEquals(1f, matrix.h33)
    }

    @Test
    fun `rotation creates correct rotation matrix for 90 degrees`() {
        // Given: 90 degree rotation
        val degrees = 90f
        val radians = degrees.toDouble() * kotlin.math.PI / 180.0
        val expectedCos = cos(radians).toFloat()
        val expectedSin = sin(radians).toFloat()

        // When
        val matrix = HomographyMatrix.rotation(degrees)

        // Then: cos(90) ~ 0, sin(90) ~ 1
        assertFloatEquals(expectedCos, matrix.h11, "h11 should be cos(90)")
        assertFloatEquals(-expectedSin, matrix.h12, "h12 should be -sin(90)")
        assertFloatEquals(expectedSin, matrix.h21, "h21 should be sin(90)")
        assertFloatEquals(expectedCos, matrix.h22, "h22 should be cos(90)")
    }

    @Test
    fun `IDENTITY constant is valid identity matrix`() {
        // Given
        val identity = HomographyMatrix.IDENTITY

        // Then
        assertEquals(1f, identity.h11)
        assertEquals(0f, identity.h12)
        assertEquals(0f, identity.h13)
        assertEquals(0f, identity.h21)
        assertEquals(1f, identity.h22)
        assertEquals(0f, identity.h23)
        assertEquals(0f, identity.h31)
        assertEquals(0f, identity.h32)
        assertEquals(1f, identity.h33)
        assertTrue(identity.isNearIdentity())
        assertTrue(identity.isValid())
    }

    // ==================== Transformation Tests ====================

    @Test
    fun `transformPoint on identity returns same point`() {
        // Given
        val identity = HomographyMatrix.IDENTITY
        val x = 100f
        val y = 200f

        // When
        val (resultX, resultY) = identity.transformPoint(x, y)

        // Then
        assertFloatEquals(x, resultX, "X should be unchanged")
        assertFloatEquals(y, resultY, "Y should be unchanged")
    }

    @Test
    fun `transformPoint with translation matrix`() {
        // Given
        val tx = 50f
        val ty = 100f
        val matrix = HomographyMatrix.translation(tx, ty)
        val x = 10f
        val y = 20f

        // When
        val (resultX, resultY) = matrix.transformPoint(x, y)

        // Then
        assertFloatEquals(x + tx, resultX, "X should be translated by tx")
        assertFloatEquals(y + ty, resultY, "Y should be translated by ty")
    }

    @Test
    fun `transformPoint with rotation matrix`() {
        // Given: 90 degree rotation around origin
        val matrix = HomographyMatrix.rotation(90f)
        val x = 1f
        val y = 0f

        // When: Rotate point (1, 0) by 90 degrees
        val (resultX, resultY) = matrix.transformPoint(x, y)

        // Then: Should be approximately (0, 1)
        assertFloatEquals(0f, resultX, "X should be ~0 after 90 degree rotation")
        assertFloatEquals(1f, resultY, "Y should be ~1 after 90 degree rotation")
    }

    @Test
    fun `transformPoint with scale matrix`() {
        // Given
        val sx = 2f
        val sy = 3f
        val matrix = HomographyMatrix.scale(sx, sy)
        val x = 10f
        val y = 20f

        // When
        val (resultX, resultY) = matrix.transformPoint(x, y)

        // Then
        assertFloatEquals(x * sx, resultX, "X should be scaled by sx")
        assertFloatEquals(y * sy, resultY, "Y should be scaled by sy")
    }

    @Test
    fun `transformPoint handles perspective division`() {
        // Given: Matrix with non-trivial perspective values
        val matrix = HomographyMatrix(
            h11 = 1f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 1f, h23 = 0f,
            h31 = 0.001f, h32 = 0.001f, h33 = 1f,
        )
        val x = 100f
        val y = 100f

        // When
        val (resultX, resultY) = matrix.transformPoint(x, y)

        // Then: w = 0.001*100 + 0.001*100 + 1 = 1.2
        // x' = 100 / 1.2 ~ 83.33
        // y' = 100 / 1.2 ~ 83.33
        val expectedW = 0.001f * x + 0.001f * y + 1f
        assertFloatEquals(x / expectedW, resultX, "X should be perspective-divided")
        assertFloatEquals(y / expectedW, resultY, "Y should be perspective-divided")
    }

    @Test
    fun `transformPoint handles w approaching zero`() {
        // Given: Matrix that would produce w close to zero
        val matrix = HomographyMatrix(
            h11 = 1f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 1f, h23 = 0f,
            h31 = -0.01f, h32 = 0f, h33 = 0f,
        )
        val x = 0f
        val y = 0f

        // When: w = -0.01*0 + 0*0 + 0 = 0
        val (resultX, resultY) = matrix.transformPoint(x, y)

        // Then: Should return Float.MAX_VALUE for degenerate case
        assertEquals(Float.MAX_VALUE, resultX, "X should be MAX_VALUE for w~0")
        assertEquals(Float.MAX_VALUE, resultY, "Y should be MAX_VALUE for w~0")
    }

    @Test
    fun `transformPoints batch operation`() {
        // Given
        val matrix = HomographyMatrix.translation(10f, 20f)
        val points = listOf(
            Pair(0f, 0f),
            Pair(5f, 10f),
            Pair(100f, 200f),
        )

        // When
        val results = matrix.transformPoints(points)

        // Then
        assertEquals(3, results.size)
        assertFloatEquals(10f, results[0].first)
        assertFloatEquals(20f, results[0].second)
        assertFloatEquals(15f, results[1].first)
        assertFloatEquals(30f, results[1].second)
        assertFloatEquals(110f, results[2].first)
        assertFloatEquals(220f, results[2].second)
    }

    // ==================== Validation Tests ====================

    @Test
    fun `isValid returns true for valid matrix`() {
        // Given
        val validMatrix = HomographyMatrix.IDENTITY

        // Then
        assertTrue(validMatrix.isValid())
    }

    @Test
    fun `isValid returns false for singular matrix`() {
        // Given: Matrix with all zeros (determinant = 0)
        val singularMatrix = HomographyMatrix(
            h11 = 0f, h12 = 0f, h13 = 0f,
            h21 = 0f, h22 = 0f, h23 = 0f,
            h31 = 0f, h32 = 0f, h33 = 0f,
        )

        // Then
        assertFalse(singularMatrix.isValid())
    }

    @Test
    fun `isNearIdentity detects identity-like matrices`() {
        // Given: Identity matrix with small perturbations
        val nearIdentity = HomographyMatrix(
            h11 = 1.005f, h12 = 0.005f, h13 = 0.005f,
            h21 = 0.005f, h22 = 0.995f, h23 = 0.005f,
            h31 = 0.005f, h32 = 0.005f, h33 = 1.005f,
        )

        // Then
        assertTrue(nearIdentity.isNearIdentity())
    }

    @Test
    fun `isNearIdentity rejects non-identity matrices`() {
        // Given: Matrix far from identity
        val notNearIdentity = HomographyMatrix(
            h11 = 2f, h12 = 0f, h13 = 100f,
            h21 = 0f, h22 = 2f, h23 = 100f,
            h31 = 0f, h32 = 0f, h33 = 1f,
        )

        // Then
        assertFalse(notNearIdentity.isNearIdentity())
    }

    @Test
    fun `determinant calculation is correct`() {
        // Given: Identity matrix (determinant = 1)
        val identity = HomographyMatrix.IDENTITY
        // Scale matrix with sx=2, sy=3 (determinant = 2*3*1 = 6)
        val scaleMatrix = HomographyMatrix.scale(2f, 3f)

        // Then
        assertFloatEquals(1f, identity.determinant(), "Identity determinant should be 1")
        assertFloatEquals(6f, scaleMatrix.determinant(), "Scale(2,3) determinant should be 6")
    }

    // ==================== Extraction Tests ====================

    @Test
    fun `approximateRotationDegrees extracts rotation`() {
        // Given: Pure rotation matrices
        val rotation45 = HomographyMatrix.rotation(45f)
        val rotation90 = HomographyMatrix.rotation(90f)
        val rotation0 = HomographyMatrix.rotation(0f)

        // When/Then
        assertFloatEquals(45f, rotation45.approximateRotationDegrees(), "Should extract ~45 degrees")
        assertFloatEquals(90f, rotation90.approximateRotationDegrees(), "Should extract ~90 degrees")
        assertFloatEquals(0f, rotation0.approximateRotationDegrees(), "Should extract ~0 degrees")
    }

    @Test
    fun `approximateScale extracts scale factor`() {
        // Given: Pure scale matrices
        val scale2 = HomographyMatrix.scale(2f, 2f)
        val scale05 = HomographyMatrix.scale(0.5f, 0.5f)
        val identity = HomographyMatrix.IDENTITY

        // When/Then (approximateScale uses first column: sqrt(h11^2 + h21^2))
        assertFloatEquals(2f, scale2.approximateScale(), "Should extract scale factor 2")
        assertFloatEquals(0.5f, scale05.approximateScale(), "Should extract scale factor 0.5")
        assertFloatEquals(1f, identity.approximateScale(), "Identity should have scale 1")
    }
}
