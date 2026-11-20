package com.nextz.kanomtts.text.pipeline

import org.junit.Assert.assertEquals
import org.junit.Test

class ScriptRunSplitterTest {
    @Test fun pure_latin_is_one_run() {
        val runs = ScriptRunSplitter.split("Kanom TTS 2024!")
        assertEquals(1, runs.size)
        assertEquals(Script.LATIN, runs[0].script)
        assertEquals("Kanom TTS 2024!", runs[0].text)
        assertEquals(0, runs[0].start); assertEquals(15, runs[0].end)
    }

    @Test fun thai_with_embedded_english_splits_into_three_runs() {
        // "เปิดแอป Kanom แล้ว" -> [Thai][Latin][Thai]
        val runs = ScriptRunSplitter.split("เปิดแอป Kanom แล้ว")
        assertEquals(3, runs.size)
        assertEquals(Script.THAI, runs[0].script)
        assertEquals(Script.LATIN, runs[1].script)
        assertEquals("Kanom", runs[1].text.trim())
        assertEquals(Script.THAI, runs[2].script)
    }

    @Test fun offsets_reconstruct_the_input() {
        val input = "ABCひらがな漢字"
        val runs = ScriptRunSplitter.split(input)
        assertEquals(input, runs.joinToString("") { it.text })
        runs.forEach { assertEquals(it.text, input.substring(it.start, it.end)) }
    }

    @Test fun leading_space_attaches_to_following_run_not_a_neutral_run() {
        val runs = ScriptRunSplitter.split(" Hello")
        assertEquals(1, runs.size)
        assertEquals(Script.LATIN, runs[0].script)
    }
}
