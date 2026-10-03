package com.xnvalabs.smarteyex.data.memory

import org.junit.Assert.assertEquals
import org.junit.Test

class MemorySearchTest {
    private val items = listOf(
        MemoryEntry("1", MemoryType.NOTE, "Note", "Beli kopi arabika", 1L),
        MemoryEntry("2", MemoryType.NOTE, "Note", "Rapat Senin jam 9", 2L),
        MemoryEntry("3", MemoryType.PREFERENCE, "Preference", "Jawaban singkat saja", 3L),
    )

    @Test fun blankQueryReturnsEverything() {
        assertEquals(3, MemorySearch.filter(items, "   ").size)
    }

    @Test fun matchIgnoresCaseAndRequiresAllWords() {
        assertEquals(listOf("1"), MemorySearch.filter(items, "KOPI").map { it.id })
        assertEquals(listOf("2"), MemorySearch.filter(items, "rapat jam").map { it.id })
        assertEquals(emptyList<String>(), MemorySearch.filter(items, "rapat kopi").map { it.id })
    }

    @Test fun titleIsSearchableToo() {
        assertEquals(listOf("3"), MemorySearch.filter(items, "preference").map { it.id })
    }
}
