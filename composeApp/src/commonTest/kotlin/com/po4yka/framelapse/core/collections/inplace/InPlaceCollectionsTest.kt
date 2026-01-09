package com.po4yka.framelapse.core.collections.inplace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class InPlaceCollectionsTest {

    // ==================== InPlaceSubList Tests ====================

    @Test
    fun `InPlaceSubList returns correct size`() {
        val list = listOf(1, 2, 3, 4, 5)
        val subList = InPlaceSubList(list, 1, 4)

        assertEquals(3, subList.size)
    }

    @Test
    fun `InPlaceSubList get returns correct elements`() {
        val list = listOf("a", "b", "c", "d", "e")
        val subList = InPlaceSubList(list, 1, 4)

        assertEquals("b", subList[0])
        assertEquals("c", subList[1])
        assertEquals("d", subList[2])
    }

    @Test
    fun `InPlaceSubList throws on invalid index`() {
        val list = listOf(1, 2, 3, 4, 5)
        val subList = InPlaceSubList(list, 1, 4)

        assertFailsWith<IndexOutOfBoundsException> {
            subList[3]
        }
        assertFailsWith<IndexOutOfBoundsException> {
            subList[-1]
        }
    }

    @Test
    fun `InPlaceSubList fastForEach iterates correctly`() {
        val list = listOf(1, 2, 3, 4, 5)
        val subList = InPlaceSubList(list, 1, 4)
        val collected = mutableListOf<Int>()

        subList.fastForEach { collected.add(it) }

        assertEquals(listOf(2, 3, 4), collected)
    }

    @Test
    fun `InPlaceSubList fastForEachIndexed provides correct indices`() {
        val list = listOf("a", "b", "c", "d", "e")
        val subList = InPlaceSubList(list, 2, 5)
        val collected = mutableListOf<Pair<Int, String>>()

        subList.fastForEachIndexed { index, value ->
            collected.add(index to value)
        }

        assertEquals(
            listOf(0 to "c", 1 to "d", 2 to "e"),
            collected
        )
    }

    @Test
    fun `InPlaceSubList reflects changes in source list`() {
        val list = mutableListOf(1, 2, 3, 4, 5)
        val subList = InPlaceSubList(list, 1, 4)

        assertEquals(2, subList[0])
        list[1] = 20
        assertEquals(20, subList[0])
    }

    // ==================== forEachWindow Tests ====================

    @Test
    fun `forEachWindow iterates with correct window size`() {
        val list = listOf(1, 2, 3, 4, 5)
        val windows = mutableListOf<List<Int>>()

        list.forEachWindow(windowSize = 3) { window ->
            windows.add(window.toList())
        }

        assertEquals(
            listOf(
                listOf(1, 2, 3),
                listOf(2, 3, 4),
                listOf(3, 4, 5)
            ),
            windows
        )
    }

    @Test
    fun `forEachWindow respects step parameter`() {
        val list = listOf(1, 2, 3, 4, 5, 6)
        val windows = mutableListOf<List<Int>>()

        list.forEachWindow(windowSize = 2, step = 2) { window ->
            windows.add(window.toList())
        }

        assertEquals(
            listOf(
                listOf(1, 2),
                listOf(3, 4),
                listOf(5, 6)
            ),
            windows
        )
    }

    @Test
    fun `forEachWindow includes partial windows when enabled`() {
        val list = listOf(1, 2, 3, 4, 5)
        val windows = mutableListOf<List<Int>>()

        list.forEachWindow(windowSize = 3, step = 2, partialWindows = true) { window ->
            windows.add(window.toList())
        }

        assertEquals(
            listOf(
                listOf(1, 2, 3),
                listOf(3, 4, 5),
                listOf(5)
            ),
            windows
        )
    }

    @Test
    fun `forEachWindow excludes partial windows by default`() {
        val list = listOf(1, 2, 3, 4, 5)
        val windows = mutableListOf<List<Int>>()

        list.forEachWindow(windowSize = 3, step = 2) { window ->
            windows.add(window.toList())
        }

        assertEquals(
            listOf(
                listOf(1, 2, 3),
                listOf(3, 4, 5)
            ),
            windows
        )
    }

    @Test
    fun `forEachWindow handles empty list`() {
        val list = emptyList<Int>()
        var called = false

        list.forEachWindow(windowSize = 3) { called = true }

        assertEquals(false, called)
    }

    @Test
    fun `forEachWindow handles list smaller than window size`() {
        val list = listOf(1, 2)
        val windows = mutableListOf<List<Int>>()

        list.forEachWindow(windowSize = 5) { window ->
            windows.add(window.toList())
        }

        assertTrue(windows.isEmpty())
    }

    @Test
    fun `forEachWindow handles list smaller than window size with partial windows`() {
        val list = listOf(1, 2)
        val windows = mutableListOf<List<Int>>()

        list.forEachWindow(windowSize = 5, partialWindows = true) { window ->
            windows.add(window.toList())
        }

        assertEquals(listOf(listOf(1, 2)), windows)
    }

    @Test
    fun `forEachWindow throws on invalid window size`() {
        val list = listOf(1, 2, 3)

        assertFailsWith<IllegalArgumentException> {
            list.forEachWindow(windowSize = 0) { }
        }
        assertFailsWith<IllegalArgumentException> {
            list.forEachWindow(windowSize = -1) { }
        }
    }

    @Test
    fun `forEachWindow throws on invalid step`() {
        val list = listOf(1, 2, 3)

        assertFailsWith<IllegalArgumentException> {
            list.forEachWindow(windowSize = 2, step = 0) { }
        }
    }

    @Test
    fun `forEachWindowIndexed provides correct indices`() {
        val list = listOf(1, 2, 3, 4, 5)
        val indices = mutableListOf<Int>()

        list.forEachWindowIndexed(windowSize = 2, step = 2) { index, _ ->
            indices.add(index)
        }

        assertEquals(listOf(0, 1), indices)
    }

    // ==================== forEachChunk Tests ====================

    @Test
    fun `forEachChunk splits list into correct chunks`() {
        val list = listOf(1, 2, 3, 4, 5, 6, 7)
        val chunks = mutableListOf<List<Int>>()

        list.forEachChunk(chunkSize = 3) { chunk ->
            chunks.add(chunk.toList())
        }

        assertEquals(
            listOf(
                listOf(1, 2, 3),
                listOf(4, 5, 6),
                listOf(7)
            ),
            chunks
        )
    }

    @Test
    fun `forEachChunk handles exact division`() {
        val list = listOf(1, 2, 3, 4, 5, 6)
        val chunks = mutableListOf<List<Int>>()

        list.forEachChunk(chunkSize = 2) { chunk ->
            chunks.add(chunk.toList())
        }

        assertEquals(
            listOf(
                listOf(1, 2),
                listOf(3, 4),
                listOf(5, 6)
            ),
            chunks
        )
    }

    @Test
    fun `forEachChunk handles single element chunks`() {
        val list = listOf("a", "b", "c")
        val chunks = mutableListOf<List<String>>()

        list.forEachChunk(chunkSize = 1) { chunk ->
            chunks.add(chunk.toList())
        }

        assertEquals(
            listOf(listOf("a"), listOf("b"), listOf("c")),
            chunks
        )
    }

    @Test
    fun `forEachChunk handles chunk size larger than list`() {
        val list = listOf(1, 2, 3)
        val chunks = mutableListOf<List<Int>>()

        list.forEachChunk(chunkSize = 10) { chunk ->
            chunks.add(chunk.toList())
        }

        assertEquals(listOf(listOf(1, 2, 3)), chunks)
    }

    @Test
    fun `forEachChunk handles empty list`() {
        val list = emptyList<Int>()
        var called = false

        list.forEachChunk(chunkSize = 3) { called = true }

        assertEquals(false, called)
    }

    @Test
    fun `forEachChunkIndexed provides correct indices`() {
        val list = listOf(1, 2, 3, 4, 5)
        val results = mutableListOf<Pair<Int, List<Int>>>()

        list.forEachChunkIndexed(chunkSize = 2) { index, chunk ->
            results.add(index to chunk.toList())
        }

        assertEquals(
            listOf(
                0 to listOf(1, 2),
                1 to listOf(3, 4),
                2 to listOf(5)
            ),
            results
        )
    }

    @Test
    fun `forEachChunk throws on invalid chunk size`() {
        val list = listOf(1, 2, 3)

        assertFailsWith<IllegalArgumentException> {
            list.forEachChunk(chunkSize = 0) { }
        }
    }

    // ==================== Zero-Allocation Verification ====================

    @Test
    fun `forEachWindow reuses same InPlaceSubList instance`() {
        val list = listOf(1, 2, 3, 4, 5)
        val instances = mutableListOf<InPlaceSubList<Int>>()

        list.forEachWindow(windowSize = 2) { window ->
            instances.add(window)
        }

        // All captured references should point to the same object
        assertTrue(instances.size > 1)
        val first = instances.first()
        instances.drop(1).forEach { instance ->
            assertTrue(first === instance, "Expected same instance to be reused")
        }
    }
}
