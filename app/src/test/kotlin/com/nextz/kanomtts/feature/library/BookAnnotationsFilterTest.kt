package com.nextz.kanomtts.feature.library

import org.junit.Assert.assertEquals
import org.junit.Test

class BookAnnotationsFilterTest {
    private val rows = listOf(
        BookAnnotationRow("1", "The River", "Ann", "river.epub", "Series/River",
            listOf("Fantasy", "Thai"), "A note about water"),
        BookAnnotationRow("2", "The Garden", "Ben", "garden.epub", "Series/Garden",
            listOf("Nonfiction"), ""),
        BookAnnotationRow("3", "The River Returns", null, "returns.epub", "Series/River",
            listOf("Fantasy"), "Second note"),
    )

    @Test fun tagFilterIsCaseInsensitiveAndKeepsMatchingBooks() {
        assertEquals(listOf("1", "3"), filterBookAnnotationRows(rows, "", "fantasy", false).map { it.id })
    }

    @Test fun notesOnlyFilterExcludesBooksWithoutNotes() {
        assertEquals(listOf("1", "3"), filterBookAnnotationRows(rows, "", null, true).map { it.id })
    }

    @Test fun querySearchesTitleAuthorFilenameTagsAndNotes() {
        assertEquals(listOf("1", "3"), filterBookAnnotationRows(rows, "river", null, false).map { it.id })
        assertEquals(listOf("2"), filterBookAnnotationRows(rows, "garden.epub", null, false).map { it.id })
        assertEquals(listOf("1"), filterBookAnnotationRows(rows, "water", null, false).map { it.id })
    }

    @Test fun filtersCanBeCombined() {
        assertEquals(listOf("3"), filterBookAnnotationRows(rows, "returns", "Fantasy", true).map { it.id })
    }
}
