package com.nextz.kanomtts.playback
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
class SentenceLocatorsTest {
    @Test fun `forSentence narrows the locator text to the sentence range`() {
        val element = Locator(
            href = Url("ch1.xhtml")!!, mediaType = MediaType.XHTML,
            text = Locator.Text(highlight = "Hello world. Bye."),
        )
        val s = SentenceLocators.forSentence(element, 0, 12)
        assertEquals("Hello world.", s.text.highlight)
        assertEquals(element.href, s.href)
    }
}
