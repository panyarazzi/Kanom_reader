package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextPipelineTest {
    @Test fun mixed_sentence_routes_to_three_segments() {
        val p = TextPipeline(primary = Lang.TH)
        val out = p.process("เปิดแอป Kanom แล้ว")
        assertEquals(1, out.size)
        val seg = out[0].segments
        assertEquals(listOf(Lang.TH, Lang.EN, Lang.TH), seg.map { it.lang })
        assertEquals("Kanom", seg[1].spokenText)
    }

    @Test fun original_range_maps_back_to_input_for_highlight() {
        val input = "First. Second."
        val out = TextPipeline(primary = Lang.EN).process(input)
        assertEquals(2, out.size)
        out.forEach { assertEquals(it.originalText, input.substring(it.start, it.end)) }
    }

    @Test fun replacement_changes_spoken_text_but_not_offsets() {
        val input = "อ่าน GPT ดู"
        val transform = TextTransform { it.replace("GPT", "จีพีที") }
        val out = TextPipeline(primary = Lang.TH, replacement = transform).process(input)
        // highlight range still covers the original input substring
        assertEquals(input, input.substring(out[0].start, out[0].end))
        // but GPT is spoken as the replacement
        assertTrue(out[0].segments.any { it.spokenText.contains("จีพีที") })
    }

    @Test fun thai_digits_normalized_in_spoken_text() {
        val out = TextPipeline(primary = Lang.TH).process("หน้า ๑๒")
        assertTrue(out.flatMap { it.segments }.any { it.spokenText.contains("12") })
    }

    @Test fun replacement_applies_per_language_run_not_across_scripts() {
        val crossScript = TextTransform { it.replace("แอป Kanom", "X") }
        val a = TextPipeline(primary = Lang.TH, replacement = crossScript).process("เปิดแอป Kanom แล้ว")
        assertTrue(a.flatMap { it.segments }.none { it.spokenText.contains("X") })

        val within = TextTransform { it.replace("Kanom", "ขนม") }
        val b = TextPipeline(primary = Lang.TH, replacement = within).process("เปิดแอป Kanom แล้ว")
        assertTrue(b.flatMap { it.segments }.any { it.spokenText.contains("ขนม") })
    }

    @Test fun offsets_reconstruct_across_multi_sentence_mixed_block() {
        val input = "เธอเปิดแอป Kanom แล้วกดเล่น เสียงเริ่มอ่าน"
        val out = TextPipeline(primary = Lang.TH, replacement = TextTransform { it.replace("Kanom", "ขนม") }).process(input)
        assertTrue(out.size >= 2)
        out.forEach { assertEquals(it.originalText, input.substring(it.start, it.end)) }
        assertTrue(out.flatMap { it.segments }.any { it.spokenText.contains("ขนม") })
    }
}
