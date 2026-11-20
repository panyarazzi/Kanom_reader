package com.nextz.kanomtts.playback

import com.nextz.kanomtts.text.pipeline.Lang

/** Fixed phrases spoken when auditioning a voice in settings. */
object VoiceSamples {
    fun sampleFor(lang: Lang): String = when (lang) {
        Lang.TH -> "สวัสดีค่ะ นี่คือตัวอย่างเสียงอ่านภาษาไทย"
        Lang.EN -> "Hello, this is a sample of the reading voice."
        Lang.JA -> "こんにちは。これは読み上げ音声のサンプルです。"
        Lang.ZH -> "你好，这是朗读语音的示例。"
    }
}
