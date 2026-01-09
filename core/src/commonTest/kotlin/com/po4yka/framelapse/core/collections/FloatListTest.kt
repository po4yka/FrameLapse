package com.po4yka.framelapse.core.collections

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FloatListTest {

    // ==================== Basic Properties Tests ====================

    @Test
    fun `empty list has size 0`() {
        val list = emptyFloatList()
        assertEquals(0, list.size)
        assertTrue(list.isEmpty())
        assertFalse(list.isNotEmpty())
    }

    @Test
    fun `list with elements has correct size`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(3, list.size)
        assertFalse(list.isEmpty())
        assertTrue(list.isNotEmpty())
    }

    @Test
    fun `lastIndex returns correct value`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(2, list.lastIndex)
    }

    @Test
    fun `indices returns correct range`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(0 until 3, list.indices)
    }

    // ==================== Access Tests ====================

    @Test
    fun `get returns correct element`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(1.0f, list[0])
        assertEquals(2.0f, list[1])
        assertEquals(3.0f, list[2])
    }

    @Test
    fun `get throws on invalid index`() {
        val list = floatListOf(1.0f, 2.0f)
        assertFailsWith<IndexOutOfBoundsException> { list[2] }
        assertFailsWith<IndexOutOfBoundsException> { list[-1] }
    }

    @Test
    fun `getOrElse returns element or default`() {
        val list = floatListOf(1.0f, 2.0f)
        assertEquals(1.0f, list.getOrElse(0) { -1f })
        assertEquals(-1f, list.getOrElse(5) { -1f })
    }

    @Test
    fun `first returns first element`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(1.0f, list.first())
    }

    @Test
    fun `first throws on empty list`() {
        val list = emptyFloatList()
        assertFailsWith<NoSuchElementException> { list.first() }
    }

    @Test
    fun `last returns last element`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(3.0f, list.last())
    }

    // ==================== Search Tests ====================

    @Test
    fun `contains finds element`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertTrue(2.0f in list)
        assertFalse(4.0f in list)
    }

    @Test
    fun `indexOf returns correct index`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f, 2.0f)
        assertEquals(1, list.indexOf(2.0f))
        assertEquals(-1, list.indexOf(5.0f))
    }

    @Test
    fun `indexOfFirst finds first matching element`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f, 4.0f)
        assertEquals(2, list.indexOfFirst { it > 2.0f })
        assertEquals(-1, list.indexOfFirst { it > 10.0f })
    }

    // ==================== Quantifier Tests ====================

    @Test
    fun `any returns true when element matches`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertTrue(list.any { it > 2.0f })
        assertFalse(list.any { it > 10.0f })
    }

    @Test
    fun `all returns true when all elements match`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertTrue(list.all { it > 0.0f })
        assertFalse(list.all { it > 1.0f })
    }

    @Test
    fun `count returns correct value`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f, 4.0f)
        assertEquals(4, list.count())
        assertEquals(2, list.count { it > 2.0f })
    }

    // ==================== Aggregation Tests ====================

    @Test
    fun `sum returns correct value`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(6.0f, list.sum())
    }

    @Test
    fun `sum of empty list is 0`() {
        val list = emptyFloatList()
        assertEquals(0f, list.sum())
    }

    @Test
    fun `average returns correct value`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(2.0f, list.average())
    }

    @Test
    fun `average of empty list is NaN`() {
        val list = emptyFloatList()
        assertTrue(list.average().isNaN())
    }

    @Test
    fun `min returns minimum value`() {
        val list = floatListOf(3.0f, 1.0f, 2.0f)
        assertEquals(1.0f, list.min())
    }

    @Test
    fun `max returns maximum value`() {
        val list = floatListOf(1.0f, 3.0f, 2.0f)
        assertEquals(3.0f, list.max())
    }

    // ==================== Iteration Tests ====================

    @Test
    fun `forEach iterates in order`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        val collected = mutableListOf<Float>()
        list.forEach { collected.add(it) }
        assertEquals(listOf(1.0f, 2.0f, 3.0f), collected)
    }

    @Test
    fun `forEachIndexed provides correct indices`() {
        val list = floatListOf(10.0f, 20.0f, 30.0f)
        val collected = mutableListOf<Pair<Int, Float>>()
        list.forEachIndexed { i, v -> collected.add(i to v) }
        assertEquals(listOf(0 to 10.0f, 1 to 20.0f, 2 to 30.0f), collected)
    }

    @Test
    fun `forEachReversed iterates in reverse order`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        val collected = mutableListOf<Float>()
        list.forEachReversed { collected.add(it) }
        assertEquals(listOf(3.0f, 2.0f, 1.0f), collected)
    }

    @Test
    fun `fold accumulates correctly`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        val result = list.fold(0f) { acc, value -> acc + value }
        assertEquals(6.0f, result)
    }

    // ==================== Transform Tests ====================

    @Test
    fun `filter returns matching elements`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f, 4.0f)
        val filtered = list.filter { it > 2.0f }
        assertEquals(floatListOf(3.0f, 4.0f), filtered)
    }

    @Test
    fun `map transforms elements`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        val mapped = list.map { it * 2 }
        assertEquals(floatListOf(2.0f, 4.0f, 6.0f), mapped)
    }

    @Test
    fun `copy creates independent list`() {
        val original = mutableFloatListOf(1.0f, 2.0f, 3.0f)
        val copy = original.copy()
        original[0] = 10.0f
        assertEquals(1.0f, copy[0])
    }

    // ==================== Conversion Tests ====================

    @Test
    fun `toFloatArray returns correct array`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        val array = list.toFloatArray()
        assertEquals(3, array.size)
        assertEquals(1.0f, array[0])
        assertEquals(2.0f, array[1])
        assertEquals(3.0f, array[2])
    }

    @Test
    fun `toList returns correct boxed list`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(listOf(1.0f, 2.0f, 3.0f), list.toList())
    }

    @Test
    fun `joinToString formats correctly`() {
        val list = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals("1.0, 2.0, 3.0", list.joinToString())
        assertEquals("[1.0; 2.0; 3.0]", list.joinToString("; ", "[", "]"))
    }

    @Test
    fun `toString returns bracket format`() {
        val list = floatListOf(1.0f, 2.0f)
        assertEquals("[1.0, 2.0]", list.toString())
    }

    // ==================== MutableFloatList Tests ====================

    @Test
    fun `add appends element`() {
        val list = mutableFloatListOf()
        list.add(1.0f)
        list.add(2.0f)
        assertEquals(2, list.size)
        assertEquals(1.0f, list[0])
        assertEquals(2.0f, list[1])
    }

    @Test
    fun `add at index inserts element`() {
        val list = mutableFloatListOf(1.0f, 3.0f)
        list.add(1, 2.0f)
        assertEquals(floatListOf(1.0f, 2.0f, 3.0f), list)
    }

    @Test
    fun `plusAssign adds element`() {
        val list = mutableFloatListOf(1.0f)
        list += 2.0f
        assertEquals(floatListOf(1.0f, 2.0f), list)
    }

    @Test
    fun `addAll adds array`() {
        val list = mutableFloatListOf(1.0f)
        list.addAll(floatArrayOf(2.0f, 3.0f))
        assertEquals(floatListOf(1.0f, 2.0f, 3.0f), list)
    }

    @Test
    fun `addAll adds FloatList`() {
        val list = mutableFloatListOf(1.0f)
        list.addAll(floatListOf(2.0f, 3.0f))
        assertEquals(floatListOf(1.0f, 2.0f, 3.0f), list)
    }

    @Test
    fun `remove removes first occurrence`() {
        val list = mutableFloatListOf(1.0f, 2.0f, 1.0f)
        assertTrue(list.remove(1.0f))
        assertEquals(floatListOf(2.0f, 1.0f), list)
        assertFalse(list.remove(5.0f))
    }

    @Test
    fun `removeAt removes element at index`() {
        val list = mutableFloatListOf(1.0f, 2.0f, 3.0f)
        val removed = list.removeAt(1)
        assertEquals(2.0f, removed)
        assertEquals(floatListOf(1.0f, 3.0f), list)
    }

    @Test
    fun `set updates element`() {
        val list = mutableFloatListOf(1.0f, 2.0f, 3.0f)
        val old = list.set(1, 20.0f)
        assertEquals(2.0f, old)
        assertEquals(20.0f, list[1])
    }

    @Test
    fun `clear removes all elements`() {
        val list = mutableFloatListOf(1.0f, 2.0f, 3.0f)
        list.clear()
        assertTrue(list.isEmpty())
        assertEquals(0, list.size)
    }

    @Test
    fun `ensureCapacity grows internal array`() {
        val list = mutableFloatListOf()
        list.ensureCapacity(100)
        assertTrue(list.capacity >= 100)
    }

    // ==================== Factory Function Tests ====================

    @Test
    fun `floatListOf with no args returns empty list`() {
        val list = floatListOf()
        assertTrue(list.isEmpty())
    }

    @Test
    fun `mutableFloatListOf creates mutable list`() {
        val list = mutableFloatListOf(1.0f, 2.0f)
        list.add(3.0f)
        assertEquals(3, list.size)
    }

    @Test
    fun `buildFloatList builds list correctly`() {
        val list = buildFloatList {
            add(1.0f)
            add(2.0f)
            add(3.0f)
        }
        assertEquals(floatListOf(1.0f, 2.0f, 3.0f), list)
    }

    @Test
    fun `buildFloatList with capacity builds list correctly`() {
        val list = buildFloatList(10) {
            add(1.0f)
            add(2.0f)
        }
        assertEquals(floatListOf(1.0f, 2.0f), list)
    }

    // ==================== Equality Tests ====================

    @Test
    fun `equals returns true for same content`() {
        val list1 = floatListOf(1.0f, 2.0f, 3.0f)
        val list2 = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(list1, list2)
    }

    @Test
    fun `equals returns false for different content`() {
        val list1 = floatListOf(1.0f, 2.0f, 3.0f)
        val list2 = floatListOf(1.0f, 2.0f, 4.0f)
        assertFalse(list1.equals(list2))
    }

    @Test
    fun `equals returns false for different size`() {
        val list1 = floatListOf(1.0f, 2.0f)
        val list2 = floatListOf(1.0f, 2.0f, 3.0f)
        assertFalse(list1.equals(list2))
    }

    @Test
    fun `hashCode is consistent for equal lists`() {
        val list1 = floatListOf(1.0f, 2.0f, 3.0f)
        val list2 = floatListOf(1.0f, 2.0f, 3.0f)
        assertEquals(list1.hashCode(), list2.hashCode())
    }
}
