package com.nextz.kanomtts.reader
import com.nextz.kanomtts.reader.engine.LocatorSerializer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.mediatype.MediaType
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class ReaderHighlightFactoryTest {
    @Test fun storesOriginalLocatorAndCleanDisplayQuote() {
        val locator = Locator(href = Url("chapter.xhtml")!!, mediaType = MediaType.XHTML,
            text = Locator.Text(before = "ก่อน​หน้า", highlight = "กรุง​เทพมหานคร", after = "ข้อความ​ถัดไป"))
        val entity = ReaderHighlightFactory.build("book-1", locator, 123L)!!
        assertEquals("กรุงเทพมหานคร", entity.selectedText)
        assertEquals(locator.text.before, entity.beforeText)
        assertEquals(locator.text.after, entity.afterText)
        assertEquals(locator, LocatorSerializer.decode(entity.locator))
    }
    @Test fun refusesSelectionWithoutARealTextQuote() {
        val locator = Locator(href = Url("chapter.xhtml")!!, mediaType = MediaType.XHTML)
        assertNull(ReaderHighlightFactory.build("book-1", locator, 123L))
    }
}
