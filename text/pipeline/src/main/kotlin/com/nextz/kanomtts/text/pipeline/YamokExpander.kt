package com.nextz.kanomtts.text.pipeline

/**
 * Expands the Thai repetition mark ไม้ยมก (ๆ, U+0E46) for spoken text.
 *
 * ๆ means "repeat the preceding word", so "มากๆ" should be SPOKEN as "มาก มาก".
 * System TTS engines do not reliably expand it (they skip it or read the mark's
 * name), so we rewrite it before handing text to the engine. A space is inserted
 * between the repeats so the engine treats them as two words.
 *
 * Word boundaries come from [WordTokenizer] (ICU Thai dictionary). A word that
 * ends with one or more ๆ (e.g. "มากๆ") repeats its base that many times. A
 * standalone "ๆ" token (e.g. from "นานา ๆ") repeats the previous word. A leading
 * ๆ with no antecedent is left untouched (nothing to repeat).
 *
 * This only rewrites SPOKEN text — it never affects locator offsets or the
 * on-screen highlight, which track the original (displayed) characters.
 */
object YamokExpander {

    private const val YAMOK = 'ๆ' // U+0E46

    fun expand(text: String): String {
        if (YAMOK !in text) return text
        val words = WordTokenizer.tokenize(text)
        if (words.isEmpty()) return text

        val sb = StringBuilder(text.length + 8)
        var prevBase: String? = null
        var cursor = 0
        for (range in words) {
            val start = range.first
            val endExclusive = range.last + 1
            // Preserve the inter-word characters (spaces / punctuation) verbatim.
            if (start > cursor) sb.append(text, cursor, start)
            val word = text.substring(start, endExclusive)
            when {
                word.all { it == YAMOK } -> {
                    val base = prevBase
                    if (base != null) repeat(word.length) { sb.append(' ').append(base) }
                    else sb.append(word) // no antecedent — leave the mark as-is
                }
                word.endsWith(YAMOK) -> {
                    val base = word.trimEnd(YAMOK)
                    val repeats = word.length - base.length
                    sb.append(base)
                    repeat(repeats) { sb.append(' ').append(base) }
                    prevBase = base
                }
                else -> {
                    sb.append(word)
                    prevBase = word
                }
            }
            cursor = endExclusive
        }
        if (cursor < text.length) sb.append(text, cursor, text.length)
        return sb.toString()
    }
}
