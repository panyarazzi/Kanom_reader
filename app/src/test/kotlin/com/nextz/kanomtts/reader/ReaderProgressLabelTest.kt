package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.junit.Test

class ReaderProgressLabelTest {
    @Test fun buildsIndependentProgressFieldsWithoutWholeBookPageCounts() {
        val content = buildProgressContent(
            pageNumber = 7,
            totalPages = 18,
            chapterTitle = "บทที่ 2",
            bookTitle = "หนังสือทดสอบ",
            totalProgression = 0.6527,
            time = "12:30",
            batteryPercent = 76,
            charging = true,
        )
        assertEquals("7/18", content.chapterPage)
        assertEquals("65.27%", content.bookPercent)
        assertEquals("หนังสือทดสอบ", content.bookTitle)
        assertEquals("76% ⚡", progressSlotValue("CHARGING", content))
        assertEquals("12:30 76%", progressSlotValue("TIME_BATTERY", content))
    }

    @Test fun centersProgressAndSuppressesDuplicateValues() {
        val content = buildProgressContent(7, 18, "บทที่ 2", "หนังสือทดสอบ", 0.6527, "12:30", 76, false)
        val slots = progressSlotValues(
            ProgressLayout("BOOK_PERCENT", "BOOK_PERCENT", "TIME"),
            content,
        )
        assertEquals("65.27%", slots.left)
        assertNull(slots.center)
        assertEquals("12:30", slots.right)
    }

    @Test fun formatsPageAndChapterTitle() {
        assertEquals("1/24 บทที่ 2", formatChapterProgress(1, 24, "บทที่ 2"))
    }

    @Test fun formatsWholeBookProgressWithTwoDecimalPlaces() {
        assertEquals("1/24 65.27% บทที่ 2", formatChapterProgress(1, 24, "บทที่ 2", 0.6527))
        assertEquals("24/24 100.00%", formatChapterProgress(24, 24, " ", 1.0))
    }

    @Test fun ignoresInvalidWholeBookProgress() {
        assertEquals("1/24", formatChapterProgress(1, 24, null, Double.NaN))
    }

    @Test fun formatsPageWithoutChapterTitle() {
        assertEquals("24/24", formatChapterProgress(24, 24, "  "))
    }

    @Test fun clampsPageNumberToChapterRange() {
        assertEquals("24/24 บทสุดท้าย", formatChapterProgress(27, 24, "บทสุดท้าย"))
    }

    @Test fun resolvesNestedTocTitleByResourceHref() {
        val child = Link(
            href = Href(Url("OPS/Text/chapter%2002.xhtml")!!),
            title = "บทที่ 2 น้ำยาปรับสภาพยีน",
        )
        val parent = Link(
            href = Href(Url("OPS/Text/part.xhtml")!!),
            title = "ภาคหนึ่ง",
            children = listOf(child),
        )

        assertEquals(
            "บทที่ 2 น้ำยาปรับสภาพยีน",
            resolveChapterTitle(
                locatorHref = "OPS/Text/chapter%2002.xhtml#section-3",
                locatorTitle = "chapter",
                toc = listOf(parent),
            ),
        )
    }

    @Test fun rejectsGenericOrFabricatedLocatorTitles() {
        assertNull(resolveChapterTitle("OPS/a.xhtml", "เล่มที่ 2", emptyList()))
        assertNull(resolveChapterTitle("OPS/a.xhtml", "chapter", emptyList()))
    }

    @Test fun hidesUntilReadiumProvidesPagination() {
        assertNull(formatChapterProgress(null, 24, "บทที่ 2"))
        assertNull(formatChapterProgress(1, 0, "บทที่ 2"))
    }
}
