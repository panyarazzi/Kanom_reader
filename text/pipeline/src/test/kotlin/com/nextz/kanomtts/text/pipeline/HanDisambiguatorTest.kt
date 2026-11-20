package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class HanDisambiguatorTest {
    private fun langs(input: String, primary: Lang): List<Lang> =
        HanDisambiguator.assign(ScriptRunSplitter.split(input), primary).map { it.lang }

    @Test fun han_next_to_kana_is_japanese() {
        // 日本語のテスト : Han + Hiragana + Katakana all -> JA, merged to one run
        val runs = HanDisambiguator.assign(ScriptRunSplitter.split("日本語のテスト"), Lang.ZH)
        assertEquals(listOf(Lang.JA), runs.map { it.lang })
        assertEquals(1, runs.size)
    }

    @Test fun lone_han_uses_primary_chinese() {
        assertEquals(listOf(Lang.ZH), langs("中文测试", Lang.ZH))
    }

    @Test fun lone_han_uses_primary_japanese_when_book_is_japanese() {
        assertEquals(listOf(Lang.JA), langs("漢字", Lang.JA))
    }

    @Test fun thai_latin_routing_is_independent_of_primary() {
        assertEquals(listOf(Lang.TH, Lang.EN, Lang.TH), langs("เปิดแอป Kanom แล้ว", Lang.TH))
    }

    @Test fun adjacent_same_lang_runs_merge() {
        // Hiragana run then Katakana run -> both JA -> merged
        val runs = HanDisambiguator.assign(ScriptRunSplitter.split("あいウエ"), Lang.JA)
        assertEquals(1, runs.size)
        assertEquals(Lang.JA, runs[0].lang)
    }
}
