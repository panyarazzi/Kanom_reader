package com.nextz.kanomtts.data.portable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.json.JSONObject
import com.nextz.kanomtts.data.db.BookTagLibraryEntity
class PortableDataPolicyTest {
    @Test fun `only metadata and cover entries are restored from zip archives`() {
        assertTrue(isRestorablePortableEntry("data.json"))
        assertTrue(isRestorablePortableEntry("covers/book-1.bin"))
        assertFalse(isRestorablePortableEntry("books/book-1.epub"))
        assertFalse(isRestorablePortableEntry("Kanom/books/book-1.epub"))
        assertFalse(isRestorablePortableEntry("portableEpub/book-1.epub"))
        assertFalse(isRestorablePortableEntry("unknown/payload.bin"))
    }
    @Test fun `legacy backup without savedTags restores an empty reusable tag library`() {
        assertTrue(parseSavedTagLibrary(JSONObject().put("formatVersion", 1)).isEmpty())
    }

    @Test fun `saved tag restore trims blanks and deduplicates without changing assigned tags`() {
        val json = JSONObject().put("savedTags", org.json.JSONArray()
            .put(JSONObject().put("tag", " Review "))
            .put(JSONObject().put("tag", "review"))
            .put(JSONObject().put("tag", ""))
            .put(JSONObject().put("tag", " แนะนำ ")))
        assertEquals(listOf("Review", "แนะนำ"), parseSavedTagLibrary(json).map { it.tag })
    }

    @Test fun `saved tag backup round trip preserves reusable tags`() {
        val original = listOf(BookTagLibraryEntity("แนะนำ"), BookTagLibraryEntity("Review"))
        val encoded = encodeSavedTagLibrary(original)
        assertEquals(original, parseSavedTagLibrary(JSONObject().put("savedTags", encoded)))
    }

    @Test fun `legacy book records without manual status remain progress-derived`() {
        assertEquals(null, parsePortableReadingStatus(JSONObject().put("id", "b1")))
    }

    @Test fun `portable reading status accepts only known shelf values`() {
        assertEquals("TO_READ", parsePortableReadingStatus(JSONObject().put("readingStatus", "TO_READ")))
        assertEquals("FINISHED", parsePortableReadingStatus(JSONObject().put("readingStatus", "FINISHED")))
    }

}
