package com.nextz.kanomtts.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.Url
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalReadiumApi::class)
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReaderTocFocusTest {

    private fun link(href: String, children: List<Link> = emptyList()): Link =
        Link(href = Href(Url(href)!!), title = href, children = children)

    @Test fun `flattens H1 and H2 entries in depth-first order with indentation levels`() {
        val toc = listOf(
            link("volume1.xhtml", children = listOf(
                link("chapter1.xhtml"),
                link("chapter2.xhtml"),
            )),
            link("volume2.xhtml", children = listOf(link("chapter3.xhtml"))),
        )

        val entries = ReaderTocFocus.flatten(toc)

        assertEquals(
            listOf(
                "volume1.xhtml" to 0,
                "chapter1.xhtml" to 1,
                "chapter2.xhtml" to 1,
                "volume2.xhtml" to 0,
                "chapter3.xhtml" to 1,
            ),
            entries.map { it.link.href.toString() to it.depth },
        )
    }

    @Test fun `flattens deeper nested entries recursively`() {
        val toc = listOf(
            link("volume.xhtml", children = listOf(
                link("part.xhtml", children = listOf(link("chapter.xhtml"))),
            )),
        )

        assertEquals(
            listOf(0, 1, 2),
            ReaderTocFocus.flatten(toc).map { it.depth },
        )
    }

    @Test fun `visible rows respect collapsed and expanded parent paths`() {
        val toc = listOf(link("volume.xhtml", children = listOf(
            link("part.xhtml", children = listOf(link("chapter.xhtml"))),
            link("appendix.xhtml"),
        )))
        assertEquals(listOf("volume.xhtml", "part.xhtml", "appendix.xhtml"),
            ReaderTocFocus.visible(toc, setOf("0")).map { it.link.href.toString() })
        assertEquals(listOf("volume.xhtml", "part.xhtml", "chapter.xhtml", "appendix.xhtml"),
            ReaderTocFocus.visible(toc, setOf("0", "0/0")).map { it.link.href.toString() })
    }

    @Test fun `expand all returns every node that has children`() {
        val toc = listOf(
            link("volume1.xhtml", children = listOf(
                link("part1.xhtml", children = listOf(link("chapter1.xhtml"))),
                link("part2.xhtml"),
            )),
            link("volume2.xhtml", children = listOf(link("chapter2.xhtml"))),
        )

        assertEquals(
            setOf("0", "0/0", "1"),
            ReaderTocFocus.expandablePaths(toc),
        )
        assertEquals(
            listOf("volume1.xhtml", "part1.xhtml", "chapter1.xhtml", "part2.xhtml",
                "volume2.xhtml", "chapter2.xhtml"),
            ReaderTocFocus.visible(toc, ReaderTocFocus.expandablePaths(toc))
                .map { it.link.href.toString() },
        )
    }

    @Test fun `collapsed current child can be represented by its visible volume ancestor`() {
        val toc = listOf(
            link("volume1.xhtml", children = listOf(link("chapter1.xhtml"))),
            link("volume2.xhtml", children = listOf(link("chapter2.xhtml"))),
        )
        val current = ReaderTocFocus.flatten(toc).first { it.link.href.toString() == "chapter2.xhtml" }
        val visible = ReaderTocFocus.visible(toc, emptySet())
        val focusedAncestor = visible
            .filter { it.path.size <= current.path.size && current.path.take(it.path.size) == it.path }
            .maxByOrNull { it.path.size }

        assertEquals("1", focusedAncestor?.key)
        assertEquals("volume2.xhtml", focusedAncestor?.link?.href.toString())
    }

    @Test fun `current location expands only its ancestor path`() {
        val toc = listOf(link("volume.xhtml", children = listOf(
            link("part.xhtml", children = listOf(link("chapter.xhtml"))),
        )))
        assertEquals(setOf("0", "0/0"), ReaderTocFocus.expandedAncestors(toc, Url("chapter.xhtml")!!))
    }


    @Test fun `search results include descendants even when every branch is collapsed`() {
        val toc = listOf(link("volume.xhtml", children = listOf(
            link("part.xhtml", children = listOf(link("target-chapter.xhtml"))),
            link("appendix.xhtml"),
        )))
        val flattened = ReaderTocFocus.flatten(toc)
        val results = flattened.filter { it.link.href.toString().contains("target", ignoreCase = true) }
        assertEquals(listOf("target-chapter.xhtml"), results.map { it.link.href.toString() })
    }

    @Test fun `search result context includes all parent titles`() {
        val toc = listOf(link("volume.xhtml", children = listOf(
            link("part.xhtml", children = listOf(link("chapter.xhtml"))),
        )))
        val chapter = ReaderTocFocus.flatten(toc).last()
        assertEquals(listOf("volume.xhtml", "part.xhtml"), ReaderTocFocus.parentContext(toc, chapter.path))
    }

    @Test fun `matches a top-level entry in flattened rows`() {
        val toc = listOf(link("ch1.xhtml"), link("ch2.xhtml"), link("ch3.xhtml"))
        assertEquals(1, ReaderTocFocus.currentIndex(toc, Url("ch2.xhtml")!!))
    }

    @Test fun `nested child resolves to its own flattened row index`() {
        val toc = listOf(
            link("ch1.xhtml", children = listOf(link("ch1-a.xhtml"), link("ch1-b.xhtml"))),
            link("ch2.xhtml"),
        )
        assertEquals(2, ReaderTocFocus.currentIndex(toc, Url("ch1-b.xhtml")!!))
    }

    @Test fun `nested child in second parent resolves to its own flattened row index`() {
        val toc = listOf(
            link("a.xhtml"),
            link("b.xhtml", children = listOf(link("c.xhtml"))),
        )
        assertEquals(2, ReaderTocFocus.currentIndex(toc, Url("c.xhtml")!!))
    }

    @Test fun `deeply nested child resolves to its own row index`() {
        val toc = listOf(
            link("volume.xhtml", children = listOf(
                link("part.xhtml", children = listOf(link("chapter.xhtml"))),
            )),
        )
        assertEquals(2, ReaderTocFocus.currentIndex(toc, Url("chapter.xhtml")!!))
    }

    @Test fun `fragment match focuses nested section in the same XHTML resource`() {
        val toc = listOf(
            link("book.xhtml", children = listOf(
                link("book.xhtml#chapter-one"),
                link("book.xhtml#chapter-two"),
            )),
        )
        assertEquals(2, ReaderTocFocus.currentIndex(toc, Url("book.xhtml#chapter-two")!!))
    }

    @Test fun `match ignores fragments on both sides`() {
        val toc = listOf(link("ch1.xhtml"), link("ch2.xhtml#section-3"))
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
