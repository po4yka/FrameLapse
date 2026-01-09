package com.po4yka.framelapse.core.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ArrayExtensionsTest {

    // ==================== mapArray Tests ====================

    @Test
    fun `mapArray transforms elements`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val result = array.mapArray { it * 2 }

        assertEquals(3, result.size)
        assertEquals(2.0f, result[0])
        assertEquals(4.0f, result[1])
        assertEquals(6.0f, result[2])
    }

    @Test
    fun `mapArray handles empty array`() {
        val array = floatArrayOf()
        val result = array.mapArray { it * 2 }

        assertEquals(0, result.size)
    }

    @Test
    fun `mapArrayIndexed uses index in transform`() {
        val array = floatArrayOf(10.0f, 20.0f, 30.0f)
        val result = array.mapArrayIndexed { index, value -> value + index }

        assertEquals(10.0f, result[0]) // 10 + 0
        assertEquals(21.0f, result[1]) // 20 + 1
        assertEquals(32.0f, result[2]) // 30 + 2
    }

    @Test
    fun `map returns FloatList`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val result: FloatList = array.map { it + 1 }

        assertEquals(3, result.size)
        assertEquals(2.0f, result[0])
        assertEquals(3.0f, result[1])
        assertEquals(4.0f, result[2])
    }

    // ==================== filterArray Tests ====================

    @Test
    fun `filterArray returns matching elements`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val result = array.filterArray { it > 2.0f }

        assertEquals(3, result.size)
        assertEquals(3.0f, result[0])
        assertEquals(4.0f, result[1])
        assertEquals(5.0f, result[2])
    }

    @Test
    fun `filterArray returns empty array when nothing matches`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val result = array.filterArray { it > 10.0f }

        assertEquals(0, result.size)
    }

    @Test
    fun `filterArray returns all elements when all match`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val result = array.filterArray { it > 0.0f }

        assertEquals(3, result.size)
    }

    @Test
    fun `filterArray handles empty array`() {
        val array = floatArrayOf()
        val result = array.filterArray { it > 0.0f }

        assertEquals(0, result.size)
    }

    @Test
    fun `filter returns FloatList`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)
        val result: FloatList = array.filter { it % 2 == 0f }

        assertEquals(2, result.size)
        assertEquals(2.0f, result[0])
        assertEquals(4.0f, result[1])
    }

    // ==================== sum Tests ====================

    @Test
    fun `sum returns correct value`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)
        assertEquals(10.0f, array.sum())
    }

    @Test
    fun `sum of empty array is 0`() {
        val array = floatArrayOf()
        assertEquals(0f, array.sum())
    }

    @Test
    fun `sum handles negative values`() {
        val array = floatArrayOf(-1.0f, 2.0f, -3.0f)
        assertEquals(-2.0f, array.sum())
    }

    // ==================== average Tests ====================

    @Test
    fun `average returns correct value`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f)
        assertEquals(2.5f, array.average())
    }

    @Test
    fun `average of empty array is NaN`() {
        val array = floatArrayOf()
        assertTrue(array.average().isNaN())
    }

    @Test
    fun `average of single element is that element`() {
        val array = floatArrayOf(5.0f)
        assertEquals(5.0f, array.average())
    }

    // ==================== forEachIndexed Tests ====================

    @Test
    fun `forEachIndexed provides correct indices and values`() {
        val array = floatArrayOf(10.0f, 20.0f, 30.0f)
        val collected = mutableListOf<Pair<Int, Float>>()

        array.forEachIndexed { index, value ->
            collected.add(index to value)
        }

        assertEquals(
            listOf(0 to 10.0f, 1 to 20.0f, 2 to 30.0f),
            collected,
        )
    }

    @Test
    fun `forEachIndexed handles empty array`() {
        val array = floatArrayOf()
        var called = false

        array.forEachIndexed { _, _ -> called = true }

        assertEquals(false, called)
    }

    // ==================== take Tests ====================

    @Test
    fun `take returns first n elements`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val result = array.take(3)

        assertEquals(3, result.size)
        assertEquals(1.0f, result[0])
        assertEquals(2.0f, result[1])
        assertEquals(3.0f, result[2])
    }

    @Test
    fun `take 0 returns empty array`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val result = array.take(0)

        assertEquals(0, result.size)
    }

    @Test
    fun `take more than size returns all elements`() {
        val array = floatArrayOf(1.0f, 2.0f)
        val result = array.take(10)

        assertEquals(2, result.size)
        assertEquals(1.0f, result[0])
        assertEquals(2.0f, result[1])
    }

    @Test
    fun `take throws on negative n`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)

        assertFailsWith<IllegalArgumentException> {
            array.take(-1)
        }
    }

    // ==================== drop Tests ====================

    @Test
    fun `drop removes first n elements`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val result = array.drop(2)

        assertEquals(3, result.size)
        assertEquals(3.0f, result[0])
        assertEquals(4.0f, result[1])
        assertEquals(5.0f, result[2])
    }

    @Test
    fun `drop 0 returns all elements`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)
        val result = array.drop(0)

        assertEquals(3, result.size)
    }

    @Test
    fun `drop more than size returns empty array`() {
        val array = floatArrayOf(1.0f, 2.0f)
        val result = array.drop(10)

        assertEquals(0, result.size)
    }

    @Test
    fun `drop throws on negative n`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f)

        assertFailsWith<IllegalArgumentException> {
            array.drop(-1)
        }
    }

    // ==================== Combined Operations Tests ====================

    @Test
    fun `mapArray and filterArray can be chained`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val result = array
            .mapArray { it * 2 }
            .filterArray { it > 5.0f }

        assertEquals(3, result.size)
        assertEquals(6.0f, result[0]) // 3 * 2
        assertEquals(8.0f, result[1]) // 4 * 2
        assertEquals(10.0f, result[2]) // 5 * 2
    }

    @Test
    fun `take and mapArray can be chained`() {
        val array = floatArrayOf(1.0f, 2.0f, 3.0f, 4.0f, 5.0f)
        val result = array.take(3).mapArray { it + 10 }

        assertEquals(3, result.size)
        assertEquals(11.0f, result[0])
        assertEquals(12.0f, result[1])
        assertEquals(13.0f, result[2])
    }
}
