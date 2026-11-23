package com.nextz.kanomtts.feature.library
import com.nextz.kanomtts.data.db.BookEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
class RelinkMatchingTest {
    private val original = BookEntity(
        id = "stable-id", uri = "content://old/source.epub", title = "Same Title",
        author = "Author", lang = "th", coverPath = null, fileSize = 2048L,
        addedAt = 1L, lastOpenedAt = 99L, sha256 = "abc123",
    )
    @Test fun `fingerprint is authoritative when available`() {
        assertTrue(relinkMatches(original, RelinkCandidate("content://new/a.epub", "a.epub", 2048L, "abc123", null), null))
        // A verified fingerprint remains authoritative even if provider size metadata is inaccurate.
        assertTrue(relinkMatches(original, RelinkCandidate("content://new/a.epub", "a.epub", 1L, "abc123", null), null))
        assertFalse(relinkMatches(original, RelinkCandidate("content://new/a.epub", "Same Title.epub", 2048L, "different", null), "Same Title"))
    }
    @Test fun `legacy records fall back to title and size`() {
        val legacy = original.copy(sha256 = null)
        assertTrue(relinkMatches(legacy, RelinkCandidate("content://new/a", "other.epub", 2048L, null, null), "Same Title"))
        assertTrue(relinkMatches(legacy, RelinkCandidate("content://new/a", "Same Title.epub", 2048L, null, null), null))
        assertFalse(relinkMatches(legacy, RelinkCandidate("content://new/a", "Same Title.epub", 4096L, null, null), null))
    }
}
