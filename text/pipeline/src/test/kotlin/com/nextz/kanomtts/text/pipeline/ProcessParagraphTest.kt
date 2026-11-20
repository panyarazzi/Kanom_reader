package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProcessParagraphTest {
    private val pipe = TextPipeline(primary = Lang.TH)

    @Test fun `pure thai multi-sentence paragraph is one segment`() {
        val segs = pipe.processParagraph("สวัสดีชาวโลก ฉันชอบกินผัก", maxRunChars = 4000)
        assertEquals(1, segs.size)
        assertEquals(Lang.TH, segs[0].lang)
        assertTrue(segs[0].spokenText.contains("สวัสดีชาวโลก"))
        assertTrue(segs[0].spokenText.contains("ฉันชอบกินผัก"))
    }

    @Test fun `mixed language paragraph yields runs in order`() {
        val segs = pipe.processParagraph("สวัสดี Kanom จบ", maxRunChars = 4000)
        assertEquals(listOf(Lang.TH, Lang.EN, Lang.TH), segs.map { it.lang })
    }

    @Test fun `mai yamok is expanded in paragraph mode`() {
        val segs = pipe.processParagraph("ฉันชอบกินผักมากๆ", maxRunChars = 4000)
        assertEquals(1, segs.size)
        assertEquals("ฉันชอบกินผักมาก มาก", segs[0].spokenText)
    }

    @Test fun `over-long run is chunked to the limit`() {
        val long = "ก".repeat(50)
        val segs = pipe.processParagraph(long, maxRunChars = 10)
        assertTrue("expected multiple chunks", segs.size > 1)
        assertTrue("each chunk within limit", segs.all { it.spokenText.length <= 10 })
        assertEquals(long, segs.joinToString("") { it.spokenText })
    }

    @Test fun `chunking never splits a Thai grapheme cluster mid-mark`() {
        // "ก่" = base ก (U+0E01) + tone mark ่ (U+0E48) → one grapheme cluster (2 UTF-16 units).
        // A naive cut at maxRunChars=3 would land mid-cluster, orphaning the tone mark.
        val text = "ก่".repeat(10)          // 20 units, 10 clusters
        val segs = pipe.processParagraph(text, maxRunChars = 3)
        assertTrue("expected multiple chunks", segs.size > 1)
        assertTrue("each chunk within limit", segs.all { it.spokenText.length <= 3 })
        assertTrue(
            "a chunk started with an orphaned combining mark",
            segs.none { it.spokenText.startsWith('่') },
        )
        assertEquals(text, segs.joinToString("") { it.spokenText })   // still lossless
    }

    @Test fun `blank input yields no segments`() {
        assertEquals(emptyList<SpeechSegment>(), pipe.processParagraph("   ", maxRunChars = 4000))
    }

    @Test fun `non-positive maxRunChars does not loop and returns the run whole`() {
        val segs = pipe.processParagraph("กขคง", maxRunChars = 0)
        assertEquals(1, segs.size)
        assertEquals("กขคง", segs[0].spokenText)
    }
}
