package com.nextz.kanomtts.playback
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.text.pipeline.Lang
import org.junit.Assert.assertEquals
import org.junit.Test
class VoiceRouterTest {
    private fun cfg(lang: String, voiceId: String?, speed: Float, pitch: Float) =
        VoiceConfigEntity(lang = lang, providerId = "system", voiceId = voiceId, speed = speed, pitch = pitch)
    @Test fun `unset lang falls back to defaults`() {
        val c = VoiceRouter.choose(Lang.TH, emptyMap())
        assertEquals(VoiceChoice(null, 1.0f, 1.0f), c)
    }
    @Test fun `configured lang uses its voice and prosody`() {
        val c = VoiceRouter.choose(Lang.EN, mapOf("EN" to cfg("EN", "en-voice-1", 1.5f, 0.9f)))
        assertEquals(VoiceChoice("en-voice-1", 1.5f, 0.9f), c)
    }
    @Test fun `config keyed by Lang name not value`() {
        val c = VoiceRouter.choose(Lang.JA, mapOf("ZH" to cfg("ZH", "zh", 2f, 2f)))
        assertEquals(VoiceChoice(null, 1.0f, 1.0f), c)
    }
}
