package com.nextz.kanomtts.reader

import com.nextz.kanomtts.reader.engine.ThaiZwspInjector
import com.nextz.kanomtts.text.replacement.ReplacementEngine
import com.nextz.kanomtts.text.replacement.ReplacementRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class VisualHighlightTest {
    private fun loc(h: String) = Locator(
        href = Url("c.xhtml")!!, mediaType = MediaType.XHTML,
        text = Locator.Text(highlight = h),
    )
    @Test fun `null engine or null locator returns input unchanged`() {
        val l = loc("hello")
        assertEquals(l, VisualHighlight.transformQuote(l, null))
        assertNull(VisualHighlight.transformQuote(null, ReplacementEngine(emptyList())))
    }
    @Test fun `engine rewrites the highlight quote`() {
        val engine = ReplacementEngine(listOf(ReplacementRule("hello", "hi", showInReader = true)))
        val out = VisualHighlight.transformQuote(loc("hello world"), engine)
        assertEquals("hi world", out!!.text.highlight)
    }

    @Test fun `thaiWordBreak off leaves the quote clean`() {
        val out = VisualHighlight.transformQuote(loc("สวัสดีตอนเช้า"), null, thaiWordBreak = false)
        assertEquals("สวัสดีตอนเช้า", out!!.text.highlight)
    }

    @Test fun `thaiWordBreak injects matching zwsp into the highlight quote`() {
        val out = VisualHighlight.transformQuote(loc("สวัสดีตอนเช้า"), null, thaiWordBreak = true)
        assertTrue("expected ZWSP in highlight", out!!.text.highlight!!.contains(ZWSP))
        assertEquals("สวัสดีตอนเช้า", out.text.highlight!!.replace(ZWSP.toString(), ""))
    }

    @Test fun `engine then zwsp both apply in order`() {
        val engine = ReplacementEngine(listOf(ReplacementRule("hi", "สวัสดีตอนเช้า", showInReader = true)))
        val out = VisualHighlight.transformQuote(loc("hi"), engine, thaiWordBreak = true)
        assertTrue(out!!.text.highlight!!.contains(ZWSP))
        assertEquals("สวัสดีตอนเช้า", out.text.highlight!!.replace(ZWSP.toString(), ""))
    }

    @Test fun `zwsp injected over the whole quote (before+highlight+after) for DOM parity`() {
        // A paragraph split into sentences: sentence 2 is the highlight, sentence 1 is `before`.
        // This is the page-spanning case — the highlight must carry the same ZWSP the rendered
        // node has so Readium's text search locates it (and the `before` length is correct).
        val before = "สวัสดีชาวโลก"
        val highlight = "ฉันคือบรรทัดที่คุณกำลังอ่านอยู่"
        val loc = Locator(
            href = Url("c.xhtml")!!, mediaType = MediaType.XHTML,
            text = Locator.Text(before = before, highlight = highlight, after = ""),
        )

        val out = VisualHighlight.transformQuote(loc, null, thaiWordBreak = true)!!
        val reconstructed = out.text.before.orEmpty() + out.text.highlight!! + out.text.after.orEmpty()

        // Re-split is a pure partition of injecting the whole quote at once (same boundaries as the DOM).
        assertEquals(ThaiZwspInjector.injectText(before + highlight), reconstructed)
        // Lossless: stripping ZWSP restores the originals exactly.
        assertEquals(before + highlight, reconstructed.replace(ZWSP.toString(), ""))
        assertEquals(highlight, out.text.highlight!!.replace(ZWSP.toString(), ""))
        // Both the highlight and the (positioning) prefix actually carry ZWSP now.
        assertTrue("highlight must carry ZWSP", out.text.highlight!!.contains(ZWSP))
        assertTrue("before must carry ZWSP", out.text.before!!.contains(ZWSP))
    }

    companion object {
        private const val ZWSP = '​'
    }
}
