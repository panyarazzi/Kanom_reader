package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalReadiumApi::class)
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReaderTocFocusTest {

    private fun link(href: String, children: List<Link> = emptyList()): Link =
        Link(href = Url(href)!!, title = href, children = children)

    @Test fun `matches a top-level entry`() {
        val toc = listOf(link("ch1.xhtml"), link("ch2.xhtml"), link("ch3.xhtml"))
        assertEquals(1, ReaderTocFocus.currentIndex(toc, Url("ch2.xhtml")!!))
    }

    @Test fun `nested child returns its top-level ancestor's index`() {
        // Sheet renders only top-level rows: ch1(0), ch2(1).
        // A position inside ch1-b (a child of ch1) must return 0, not the flattened position 2.
        val toc = listOf(
            link("ch1.xhtml", children = listOf(link("ch1-a.xhtml"), link("ch1-b.xhtml"))),
            link("ch2.xhtml"),
        )
        assertEquals(0, ReaderTocFocus.currentIndex(toc, Url("ch1-b.xhtml")!!))
    }

    @Test fun `nested child in second top-level entry returns index 1 not flattened index`() {
        // toc = [A (no match), B with child C (matches)]
        // Flattened depth-first would give C index 2; top-level index of B is 1.
        val toc = listOf(
            link("a.xhtml"),
            link("b.xhtml", children = listOf(link("c.xhtml"))),
        )
        assertEquals(1, ReaderTocFocus.currentIndex(toc, Url("c.xhtml")!!))
    }

    @Test fun `match ignores fragments on both sides`() {
        val toc = listOf(link("ch1.xhtml"), link("ch2.xhtml#section-3"))
        // current locator points at the same resource but a different fragment.
        assertEquals(1, ReaderTocFocus.currentIndex(toc, Url("ch2.xhtml#para-9")!!))
    }

    @Test fun `no matching resource returns null`() {
        val toc = listOf(link("ch1.xhtml"), link("ch2.xhtml"))
        assertNull(ReaderTocFocus.currentIndex(toc, Url("ch9.xhtml")!!))
    }

    @Test fun `null current href returns null`() {
        val toc = listOf(link("ch1.xhtml"), link("ch2.xhtml"))
        assertNull(ReaderTocFocus.currentIndex(toc, null))
    }
}
