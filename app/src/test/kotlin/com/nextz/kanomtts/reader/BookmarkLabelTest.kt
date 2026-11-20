package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class BookmarkLabelTest {

    private fun locator(
        title: String? = null,
        totalProgression: Double? = null,
    ) = Locator(
        href = Url("c.xhtml")!!,
        mediaType = MediaType.XHTML,
        title = title,
        locations = Locator.Locations(totalProgression = totalProgression),
    )

    @Test fun `snippet drives the primary line with percent and chapter as secondary`() {
        val label = resolveBookmarkLabel(
            snippet = "a clean excerpt",
            locator = locator(title = "บทที่ 1", totalProgression = 0.42),
        )
        assertEquals("a clean excerpt", label.primary)
        assertEquals("อ่านแล้ว 42% · บทที่ 1", label.secondary)
    }

    @Test fun `falls back to chapter title and drops it from the secondary line`() {
        val label = resolveBookmarkLabel(
            snippet = "",
            locator = locator(title = "บทที่ 2", totalProgression = 0.5),
        )
        assertEquals("บทที่ 2", label.primary)
        assertEquals("อ่านแล้ว 50%", label.secondary)
    }

    @Test fun `falls back to percent and clears the secondary line`() {
        val label = resolveBookmarkLabel(
            snippet = "",
            locator = locator(title = null, totalProgression = 0.07),
        )
        assertEquals("ตำแหน่ง 7%", label.primary)
        assertEquals("", label.secondary)
    }

    @Test fun `generic primary when nothing is known`() {
        val label = resolveBookmarkLabel(snippet = "", locator = null)
        assertEquals("ที่คั่นหน้า", label.primary)
        assertEquals("", label.secondary)
    }

    @Test fun `null locator with a snippet still yields the excerpt and empty secondary`() {
        val label = resolveBookmarkLabel(snippet = "remembered text", locator = null)
        assertEquals("remembered text", label.primary)
        assertEquals("", label.secondary)
    }

    @Test fun `percent only secondary when chapter title is absent`() {
        val label = resolveBookmarkLabel(
            snippet = "excerpt",
            locator = locator(title = null, totalProgression = 0.99),
        )
        assertEquals("excerpt", label.primary)
        assertEquals("อ่านแล้ว 99%", label.secondary)
    }
}
