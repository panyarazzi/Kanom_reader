package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class YamokExpanderTest {

    @Test fun `word ending in yamok repeats the base word`() {
        assertEquals("ฉันชอบกินผักมาก มาก", YamokExpander.expand("ฉันชอบกินผักมากๆ"))
    }

    @Test fun `yamok mid-sentence repeats only the preceding word`() {
        // เด็กๆ repeats; the following word is untouched, inter-word space preserved.
        assertEquals("เด็ก เด็ก เล่น", YamokExpander.expand("เด็กๆ เล่น"))
    }

    @Test fun `double yamok repeats twice`() {
        assertEquals("ดี ดี ดี", YamokExpander.expand("ดีๆๆ"))
    }

    @Test fun `standalone yamok after a space repeats the previous word`() {
        // "นานา ๆ" → the lone ๆ repeats นานา.
        assertEquals("นานา นานา", YamokExpander.expand("นานา ๆ").replace("  ", " ").trim())
    }

    @Test fun `text without yamok is unchanged`() {
        assertEquals("ฉันชอบกินผัก", YamokExpander.expand("ฉันชอบกินผัก"))
    }

    @Test fun `leading yamok with no antecedent is left as-is`() {
        assertEquals("ๆ ของ", YamokExpander.expand("ๆ ของ"))
    }

    @Test fun `normalizer expands yamok for Thai only`() {
        assertEquals("มาก มาก", Normalizer.normalize("มากๆ", Lang.TH))
        // EN path must not touch it (no Thai words to repeat anyway).
        assertEquals("good", Normalizer.normalize("good", Lang.EN))
    }

    @Test fun `pipeline speaks the expanded repetition`() {
        val pipe = TextPipeline(primary = Lang.TH)
        val sentences = pipe.process("ฉันชอบกินผักมากๆ")
        assertEquals(1, sentences.size)
        val spoken = sentences[0].segments.joinToString("") { it.spokenText }
        assertEquals("ฉันชอบกินผักมาก มาก", spoken)
        // The locator-facing original text is preserved (highlight stays correct).
        assertEquals("ฉันชอบกินผักมากๆ", sentences[0].originalText)
    }

    @Test fun `lone yamok after space is not split into its own sentence`() {
        val sentences = SentenceTokenizer.tokenize("นานา ๆ")
        assertEquals(1, sentences.size)
        assertEquals("นานา ๆ", sentences[0].text)
    }
}
