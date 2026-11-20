package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.TextPipeline
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
class TtsUtteranceBuilderTest {
    private val pipe = TextPipeline(primary = Lang.TH)
    private fun loc(text: String) = Locator(
        href = Url("ch1.xhtml")!!, mediaType = MediaType.XHTML,
        text = Locator.Text(highlight = text),
    )

    @Test fun `paragraph mode emits one utterance with the element locator`() {
        val text = "สวัสดีชาวโลก ฉันชอบกินผัก"
        val out = TtsUtteranceBuilder.build(text, loc(text), byParagraph = true, pipeline = pipe, maxRunChars = 4000)
        assertEquals(1, out.size)
        assertEquals(loc(text), out[0].locator)
        assertEquals(1, out[0].segments.size)   // pure-Thai → one run
    }

    @Test fun `sentence mode emits one utterance per sentence`() {
        val text = "สวัสดีชาวโลก ฉันชอบกินผัก"   // space between Thai clauses → 2 sentences
        val out = TtsUtteranceBuilder.build(text, loc(text), byParagraph = false, pipeline = pipe, maxRunChars = 4000)
        assertEquals(2, out.size)
    }

    @Test fun `paragraph mode with no speakable text emits nothing`() {
        val out = TtsUtteranceBuilder.build("   ", loc("   "), byParagraph = true, pipeline = pipe, maxRunChars = 4000)
        assertEquals(emptyList<SentenceUtterance>(), out)
    }
}
