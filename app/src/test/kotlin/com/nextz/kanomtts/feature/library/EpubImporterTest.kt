package com.nextz.kanomtts.feature.library

import com.nextz.kanomtts.feature.library.util.FakeBookRepository
import com.nextz.kanomtts.feature.library.util.FakeMetadataExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EpubImporterTest {
    private fun importer(repo: FakeBookRepository, extractor: FakeMetadataExtractor, tmp: File) =
        EpubImporter(extractor, repo, coversDir = tmp, now = { 1000L })

    @Test fun imports_new_book_with_extracted_title() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("My Book", "Auth", "th", null))
        val tmp = createTempDir()
        val r = importer(repo, ext, tmp).import("content://x", fileSize = 123, displayName = "x.epub")
        assertTrue(r is ImportResult.Imported)
        assertEquals("My Book", repo.books.value.single().title)
        assertEquals(123L, repo.books.value.single().fileSize)
    }

    @Test fun duplicate_is_not_added_twice() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("Dup", null, null, null))
        val tmp = createTempDir()
        val imp = importer(repo, ext, tmp)
        imp.import("content://same", 50, "a.epub")
        val r2 = imp.import("content://same", 50, "a.epub")
        assertTrue(r2 is ImportResult.Duplicate)
        assertEquals(1, repo.books.value.size)
    }

    @Test fun restored_book_can_be_relinked_without_changing_its_stable_id() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("Same Book", "Author", "th", null))
        val imp = importer(repo, ext, createTempDir())
        val first = imp.import("content://old-grant", 2048, "book.epub") as ImportResult.Imported
        val relinked = imp.import("content://new-grant", 2048, "book.epub")
        assertTrue(relinked is ImportResult.Relinked)
        assertEquals(first.bookId, (relinked as ImportResult.Relinked).bookId)
        assertEquals("content://new-grant", repo.books.value.single().uri)
        assertEquals(1, repo.books.value.size)
    }

    @Test fun unreadable_epub_returns_unreadable() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(null)
        val r = importer(repo, ext, createTempDir()).import("content://bad", 10, "bad.epub")
        assertTrue(r is ImportResult.Unreadable)
        assertEquals(0, repo.books.value.size)
    }

    @Test fun stable_id_is_deterministic_for_same_uri() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("T", null, null, null))
        importer(repo, ext, createTempDir()).import("content://fixed", 1, null)
        val id1 = repo.books.value.single().id
        assertEquals(id1, EpubImporter.stableId("content://fixed"))
    }

    @Test fun cover_bytes_are_written_to_coversDir() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("T", null, null, bytes))
        val tmp = createTempDir()
        val r = importer(repo, ext, tmp).import("content://img", fileSize = 5, displayName = null)
        val id = (r as ImportResult.Imported).bookId
        val coverFile = java.io.File(tmp, "$id.png")
        assertTrue(coverFile.exists())
        assertArrayEquals(bytes, coverFile.readBytes())
        assertEquals(coverFile.absolutePath, repo.books.value.single().coverPath)
    }
}
