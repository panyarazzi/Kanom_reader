package com.nextz.kanomtts.text.pipeline

data class NormalizerOptions(val skipUrls: Boolean = false, val urlPlaceholder: String = "ลิงก์")

object Normalizer {
    private val URL = Regex("""https?://\S+""")
    private val THAI_DIGIT_BASE = '๐'.code // ๐ == 0
    private val EN_ABBR = mapOf("etc." to "et cetera", "e.g." to "for example", "i.e." to "that is")

    fun normalize(text: String, lang: Lang, options: NormalizerOptions = NormalizerOptions()): String {
        var out = text
        if (options.skipUrls) out = URL.replace(out, options.urlPlaceholder)
        out = thaiDigitsToArabic(out)
        if (lang == Lang.TH) out = YamokExpander.expand(out)
        if (lang == Lang.EN) for ((k, v) in EN_ABBR) out = out.replace(k, v)
        return out
    }

    private fun thaiDigitsToArabic(s: String): String {
        if (s.none { it in '๐'..'๙' }) return s
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '๐'..'๙') ('0' + (ch.code - THAI_DIGIT_BASE)) else ch)
        return sb.toString()
    }
}
