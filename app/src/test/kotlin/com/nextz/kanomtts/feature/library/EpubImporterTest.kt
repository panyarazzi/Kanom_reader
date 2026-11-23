package com.nextz.kanomtts.feature.library
import com.nextz.kanomtts.feature.library.util.FakeBookRepository
import com.nextz.kanomtts.feature.library.util.FakeMetadataExtractor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class EpubImporterTest {
    private fun importer(repo: FakeBookRepository, extractor: FakeMetadataExtractor, tmp: File,
        hasher: suspend (String) -> String? = { null }) =
        EpubImporter(extractor, repo, coversDir = tmp, now = { 1000L }, contentHasher = hasher)

    @Test fun `imports new book with metadata fingerprint and original filename`() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("My Book", "Auth", "th", null))
        val result = importer(repo, ext, createTempDir()) { "sha-256-value" }
            .import("content://x", 123, "original-name.epub", "Series/Volume 1")
        assertTrue(result is ImportResult.Imported)
        val saved = repo.books.value.single()
        assertEquals("My Book", saved.title)
        assertEquals(123L, saved.fileSize)
        assertEquals("sha-256-value", saved.sha256)
        assertEquals("Series/Volume 1", saved.sourceFolder)
        assertEquals("original-name.epub", saved.sourceFileName)
    }
    @Test fun `duplicate source uri is not added twice`() = runTest {
        val repo = FakeBookRepository()
        val imp = importer(repo, FakeMetadataExtractor(BookMetadata("Dup", null, null, null)), createTempDir())
        imp.import("content://same", 50, "a.epub")
        assertTrue(imp.import("content://same", 50, "a.epub") is ImportResult.Duplicate)
        assertEquals(1, repo.books.value.size)
    }
    @Test fun `same title and size from different source files are not silently relinked or suppressed`() = runTest {
        val repo = FakeBookRepository()
        val imp = importer(repo, FakeMetadataExtractor(BookMetadata("Same Book", "Author", "th", null)), createTempDir())
        val first = imp.import("content://old-grant", 2048, "book.epub") as ImportResult.Imported
        val second = imp.import("content://new-grant", 2048, "book.epub") as ImportResult.Imported
        assertNotEquals(first.bookId, second.bookId)
        assertEquals(2, repo.books.value.size)
    }
    @Test fun `unreadable epub returns unreadable`() = runTest {
        val repo = FakeBookRepository()
        val result = importer(repo, FakeMetadataExtractor(null), createTempDir()).import("content://bad", 10, "bad.epub")
        assertTrue(result is ImportResult.Unreadable)
        assertEquals(0, repo.books.value.size)
    }
    @Test fun `stable id is deterministic for same uri`() = runTest {
        val repo = FakeBookRepository()
        importer(repo, FakeMetadataExtractor(BookMetadata("T", null, null, null)), createTempDir()).import("content://fixed", 1, null)
        assertEquals(repo.books.value.single().id, EpubImporter.stableId("content://fixed"))
    }
    @Test fun `cover bytes are written to covers directory`() = runTest {
        val bytes = byteArrayOf(1, 2, 3)
        val repo = FakeBookRepository()
        val tmp = createTempDir()
        val result = importer(repo, FakeMetadataExtractor(BookMetadata("T", null, null, bytes)), tmp).import("content://img", 5, null)
        val id = (result as ImportResult.Imported).bookId
        val coverFile = File(tmp, "$id.png")
        assertTrue(coverFile.exists())
        assertArrayEquals(bytes, coverFile.readBytes())
        assertEquals(coverFile.absolutePath, repo.books.value.single().coverPath)
    }
}
