package com.nextz.kanomtts.text.pipeline

enum class Script { THAI, LATIN, HAN, HIRAGANA, KATAKANA, NEUTRAL }

/** Classifies a single Unicode code point into a [Script]. Digits, punctuation,
 * whitespace and symbols are NEUTRAL and get absorbed into the surrounding run. */
fun scriptOf(codePoint: Int): Script = when (Character.UnicodeScript.of(codePoint)) {
    Character.UnicodeScript.THAI -> Script.THAI
    Character.UnicodeScript.LATIN -> Script.LATIN
    Character.UnicodeScript.HAN -> Script.HAN
    Character.UnicodeScript.HIRAGANA -> Script.HIRAGANA
    Character.UnicodeScript.KATAKANA -> Script.KATAKANA
    else -> Script.NEUTRAL
}
