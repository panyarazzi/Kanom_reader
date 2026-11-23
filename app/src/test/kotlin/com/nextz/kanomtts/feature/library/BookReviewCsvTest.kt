package com.nextz.kanomtts.feature.library
import org.junit.Assert.assertEquals
import org.junit.Test
class BookReviewCsvTest {
    @Test fun `exports book list with tags notes and quoted commas safely`() {
        val csv = buildBookReviewCsv(listOf(
            BookAnnotationRow("1", "Title, One", "Author", "source.epub", "Series", listOf("recommended"), "Good, read it"),
        ))
        assertEquals(
            "title,author,filename,folder,tags,note\n\"Title, One\",\"Author\",\"source.epub\",\"Series\",\"recommended\",\"Good, read it\"\n",
            csv,
        )
    }
}
