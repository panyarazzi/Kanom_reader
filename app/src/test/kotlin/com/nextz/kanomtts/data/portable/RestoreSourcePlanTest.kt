package com.nextz.kanomtts.data.portable

import com.nextz.kanomtts.data.db.BookEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RestoreSourcePlanTest {
    private fun book(
        id: String,
        title: String = id,
        fileName: String? = "$id.epub",
        size: Long = 100L,
        sha: String? = "hash-$id",
    ) = BookEntity(
        id = id,
        uri = "content://old/$id.epub",
        title = title,
        author = null,
        lang = "th",
        coverPath = null,
        fileSize = size,
        addedAt = 1L,
        lastOpenedAt = null,
        sha256 = sha,
        sourceFileName = fileName,
    )

    private fun candidate(
        name: String,
        uri: String = "content://new/$name",
        size: Long = 100L,
        sha: String? = null,
        title: String? = null,
    ) = RestoreSourceCandidate(uri, name, size, sha, "Library", title)

    @Test fun `one exact fingerprint becomes a unique match`() {
        val original = book("A")
        val plan = buildRestoreSourcePlan(
            listOf(original),
            listOf(candidate("renamed.epub", sha = "hash-A")),
        )
        assertEquals(listOf(original), plan.uniqueMatches.map { it.first })
        assertEquals("renamed.epub", plan.uniqueMatches.single().second.name)
        assertTrue(plan.ambiguousMatches.isEmpty())
        assertTrue(plan.notFound.isEmpty())
    }

    @Test fun `multiple exact candidates remain ambiguous`() {
        val original = book("A")
        val plan = buildRestoreSourcePlan(
            listOf(original),
            listOf(
                candidate("A-copy.epub", sha = "hash-A"),
                candidate("A.epub", uri = "content://new/A.epub", sha = "hash-A"),
            ),
        )
        assertEquals(1, plan.ambiguousMatches.size)
        assertEquals(2, plan.ambiguousMatches.single().candidates.size)
        assertTrue(plan.uniqueMatches.isEmpty())
    }

    @Test fun `legacy match requires exact size and filename or title evidence`() {
        val original = book("Legacy", title = "A Good Book", fileName = "source.epub", sha = null)
        val plan = buildRestoreSourcePlan(
            listOf(original),
            listOf(
                candidate("source.epub", size = 100L),
                candidate("A Good Book.epub", uri = "content://new/title.epub", size = 101L),
                candidate("unrelated.epub", uri = "content://new/unrelated.epub", size = 100L),
            ),
        )
        assertEquals("source.epub", plan.uniqueMatches.single().second.name)
    }

    @Test fun `legacy match with unknown size is shown for explicit selection not auto matched`() {
        val original = book("LegacyUnknown", title = "A Good Book", fileName = "source.epub", size = 0L, sha = null)
        val plan = buildRestoreSourcePlan(
            listOf(original),
            listOf(candidate("source.epub", size = 0L)),
        )
        assertTrue(plan.uniqueMatches.isEmpty())
        assertEquals(1, plan.ambiguousMatches.size)
        assertEquals(1, plan.ambiguousMatches.single().candidates.size)
        assertTrue(plan.notFound.isEmpty())
    }

    @Test fun `legacy title evidence with unknown size remains ambiguous`() {
        val original = book("LegacyTitle", title = "A Good Book", fileName = null, size = 100L, sha = null)
        val plan = buildRestoreSourcePlan(
            listOf(original),
            listOf(candidate("renamed.epub", size = 0L, title = "A Good Book")),
        )
        assertTrue(plan.uniqueMatches.isEmpty())
        assertEquals(1, plan.ambiguousMatches.size)
    }

    @Test fun `same candidate claimed by two books makes both ambiguous`() {
        val first = book("A", sha = "shared")
        val second = book("B", sha = "shared")
        val plan = buildRestoreSourcePlan(
            listOf(first, second),
            listOf(candidate("shared.epub", sha = "shared")),
        )
        assertEquals(2, plan.ambiguousMatches.size)
        assertTrue(plan.uniqueMatches.isEmpty())
    }

    @Test fun `no evidence is kept in not found group`() {
        val original = book("A")
        val plan = buildRestoreSourcePlan(listOf(original), listOf(candidate("other.epub", sha = "wrong")))
        assertEquals(listOf(original), plan.notFound)
        assertTrue(plan.uniqueMatches.isEmpty())
        assertTrue(plan.ambiguousMatches.isEmpty())
    }
}
