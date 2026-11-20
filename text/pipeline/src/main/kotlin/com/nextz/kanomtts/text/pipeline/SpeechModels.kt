package com.nextz.kanomtts.text.pipeline

data class SpeechSegment(val lang: Lang, val spokenText: String)

data class SpokenSentence(
    val start: Int,
    val end: Int,
    val originalText: String,
    val segments: List<SpeechSegment>,
)

/** Transforms spoken text. Applied by [TextPipeline] per language-run (sub-sentence),
 *  so a pattern spanning a script boundary is never seen as one string. */
fun interface TextTransform { fun apply(text: String): String }
