package com.nextz.kanomtts.playback

import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.text.pipeline.SpeechSegment
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFormattingTest {
    @Test fun `clampSpeed bounds to 0_5 and 3_0`() {
        assertEquals(0.5f, PlaybackFormatting.clampSpeed(0.1f))
        assertEquals(3.0f, PlaybackFormatting.clampSpeed(9f))
        assertEquals(1.5f, PlaybackFormatting.clampSpeed(1.5f))
    }

    @Test fun `clampPitch bounds to 0_5 and 2_0`() {
        assertEquals(0.5f, PlaybackFormatting.clampPitch(0f))
        assertEquals(2.0f, PlaybackFormatting.clampPitch(5f))
    }

    @Test fun `formatMultiplier uses one decimal and ascii digits`() {
        assertEquals("×1.0", PlaybackFormatting.formatMultiplier(1f))
        assertEquals("×1.5", PlaybackFormatting.formatMultiplier(1.5f))
    }

    @Test fun `formatSleepRemaining renders mm colon ss`() {
        assertEquals("", PlaybackFormatting.formatSleepRemaining(null))
        assertEquals("0:09", PlaybackFormatting.formatSleepRemaining(9_000))
        assertEquals("2:05", PlaybackFormatting.formatSleepRemaining(125_000))
        assertEquals("0:00", PlaybackFormatting.formatSleepRemaining(-5))
    }

    @Test fun `langLabel maps each language`() {
        assertEquals("ไทย", PlaybackFormatting.langLabel(Lang.TH))
        assertEquals("EN", PlaybackFormatting.langLabel(Lang.EN))
        assertEquals("日本", PlaybackFormatting.langLabel(Lang.JA))
        assertEquals("中文", PlaybackFormatting.langLabel(Lang.ZH))
    }

    @Test fun `progressFraction null is zero and clamps to unit range`() {
        assertEquals(0f, PlaybackFormatting.progressFraction(null))
        assertEquals(0.42f, PlaybackFormatting.progressFraction(0.42), 1e-6f)
        assertEquals(1f, PlaybackFormatting.progressFraction(1.7))
    }

    @Test fun `sentenceTextOf concatenates spoken segments`() {
        val segs = listOf(SpeechSegment(Lang.TH, "ผมชอบ "), SpeechSegment(Lang.EN, "coding"))
        assertEquals("ผมชอบ coding", PlaybackFormatting.sentenceTextOf(segs))
    }

    @Test fun `orderVoices sorts by quality desc then name`() {
        val a = Voice("a", "Alpha", Lang.TH, false, 300)
        val b = Voice("b", "Bravo", Lang.TH, false, 500)
        val c = Voice("c", "Charlie", Lang.TH, false, 500)
        assertEquals(listOf(b, c, a), PlaybackFormatting.orderVoices(listOf(a, c, b)))
    }

    @Test fun `effective speed and pitch fall back to defaults`() {
        assertEquals(VoiceRouter.DEFAULT_SPEED, PlaybackFormatting.effectiveSpeed(null))
        assertEquals(VoiceRouter.DEFAULT_PITCH, PlaybackFormatting.effectivePitch(null))
        val cfg = VoiceConfigEntity("TH", "system", null, 2.0f, 0.8f)
        assertEquals(2.0f, PlaybackFormatting.effectiveSpeed(cfg))
        assertEquals(0.8f, PlaybackFormatting.effectivePitch(cfg))
    }
}
