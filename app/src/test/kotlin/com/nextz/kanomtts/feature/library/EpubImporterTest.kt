package com.nextz.kanomtts.feature.library
import com.nextz.kanomtts.feature.library.util.FakeBookRepository
import com.nextz.kanomtts.feature.library.util.FakeMetadataExtractor
import kotlinx.coroutines.test.runTest
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class EpubImporterTest {
    private fun importer(repo: FakeBookRepository, extractor: FakeMetadataExtractor, tmp: File,
        hasher: suspend (String) -> String? = { null }) =
        EpubImporter(extractor, repo, coversDir = tmp, now = { 1000L }, contentHasher = hasher)

    @Test fun `imports new book with metadata fingerprint and original filename`() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("My Book", "Auth", "th", null, "The Saga", 2.0))
        val result = importer(repo, ext, createTempDir()) { "sha-256-value" }
            .import("content://x", 123, "original-name.epub", "Series/Volume 1")
        assertTrue(result is ImportResult.Imported)
        val saved = repo.books.value.single()
        assertEquals("My Book", saved.title)
        assertEquals(123L, saved.fileSize)
        assertEquals("sha-256-value", saved.sha256)
        assertEquals("Series/Volume 1", saved.sourceFolder)
        assertEquals("original-name.epub", saved.sourceFileName)
        assertEquals("The Saga", saved.seriesName)
        assertEquals(2.0, saved.seriesPosition!!, 0.0)
    }
    @Test fun `cancellation during fingerprinting propagates and does not add a book`() = runTest {
        val repo = FakeBookRepository()
        val imp = importer(
            repo,
            FakeMetadataExtractor(BookMetadata("Cancelled", null, null, null)),
            createTempDir(),
        ) { throw CancellationException("cancelled") }

        try {
            imp.import("content://cancelled", 10, "cancelled.epub")
            fail("Expected cancellation to propagate")
        } catch (_: CancellationException) {
            // Expected: cancellation must not be converted into a successful import.
        }
        assertTrue(repo.books.value.isEmpty())
    }

    @Test fun `duplicate source uri is not added twice`() = runTest {
        val repo = FakeBookRepository()
        val extractor = FakeMetadataExtractor(BookMetadata("Dup", null, null, null))
        val imp = importer(repo, extractor, createTempDir())
        imp.import("content://same", 50, "a.epub")
        // A repeated import should be recognized before trying to reopen a now-unreadable URI.
        extractor.result = null
        assertTrue(imp.import("content://same", 50, "a.epub") is ImportResult.Duplicate)
        assertEquals(1, repo.books.value.size)
    }
    @Test fun `folder import attaches a previously individual import without replacing its fingerprint`() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("Existing", null, "th", null))
        val imp = importer(repo, ext, createTempDir()) { "existing-sha" }
        val first = imp.import("content://already-indexed", 100, "existing.epub") as ImportResult.Imported

        val folderResult = imp.import(
            "content://already-indexed",
            100,
            "existing.epub",
            "Books/ePub Normal Style/Series A",
        )

        assertTrue(folderResult is ImportResult.Duplicate)
        val saved = repo.books.value.single()
        assertEquals(first.bookId, saved.id)
        assertEquals("existing-sha", saved.sha256)
        assertEquals("Books/ePub Normal Style/Series A", saved.sourceFolder)
        assertEquals("existing.epub", saved.sourceFileName)
    }

    @Test fun `folder import reconciles same EPUB from a different SAF uri by fingerprint`() = runTest {
        val repo = FakeBookRepository()
        val ext = FakeMetadataExtractor(BookMetadata("Same content", "Author", "th", null))
        val imp = importer(repo, ext, createTempDir()) { "same-content-sha" }
        val first = imp.import("content://individual/document/1", 2048, "same.epub") as ImportResult.Imported

        val folderResult = imp.import(
            "content://tree/Books/document/99",
            2048,
            "same.epub",
            "Books/ePub Normal Style/Series B",
        )

        assertTrue(folderResult is ImportResult.Duplicate)
        assertEquals(1, repo.books.value.size)
        val saved = repo.books.value.single()
        assertEquals(first.bookId, saved.id)
        assertEquals("content://tree/Books/document/99", saved.uri)
        assertEquals("same-content-sha", saved.sha256)
        assertEquals("Books/ePub Normal Style/Series B", saved.sourceFolder)
    }

    @Test fun `same title and size from different source files are not silently relinked or suppressed`() = runTest {
        val repo = FakeBookRepository()
        val imp = importer(repo, FakeMetadataExtractor(BookMetadata("Same Book", "Author", "th", null)), createTempDir())
        val first = imp.import("content://old-grant", 2048, "book.epub") as ImportResult.Imported
        val second = imp.import("content://new-grant", 2048, "book.epub") as ImportResult.Imported
        assertNotEquals(first.bookId, second.bookId)
        assertEquals(2, repo.books.value.size)
    }
    @Test fun `blank metadata title falls back to uppercase epub filename without extension`() = runTest {
        val repo = FakeBookRepository()
        val result = importer(
            repo,
            FakeMetadataExtractor(BookMetadata("", null, "th", null)),
            createTempDir(),
        ).import("content://uppercase", 10, "Novel.EPUB")
        assertTrue(result is ImportResult.Imported)
        assertEquals("Novel", repo.books.value.single().title)
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
