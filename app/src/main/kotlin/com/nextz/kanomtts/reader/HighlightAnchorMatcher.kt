package com.nextz.kanomtts.reader
/** Strict exact-quote matcher. Ambiguous duplicate quotes fail closed. */
object HighlightAnchorMatcher {
    fun hasUnambiguousExactMatch(resourceText: String, quote: String?, before: String?, after: String?): Boolean {
        val exact = normalize(quote.orEmpty())
        if (exact.isBlank()) return false
        val content = normalize(resourceText)
        val candidates = mutableListOf<Int>()
        var from = 0
        while (from <= content.length - exact.length) {
            val found = content.indexOf(exact, from)
            if (found < 0) break
            candidates += found
            from = found + 1
        }
        if (candidates.isEmpty()) return false
        if (candidates.size == 1) return true
        val prefix = normalize(before.orEmpty())
        val suffix = normalize(after.orEmpty())
        if (prefix.isBlank() && suffix.isBlank()) return false
        val contextual = candidates.filter { start ->
            val end = start + exact.length
            val prefixMatches = prefix.isBlank() || (start >= prefix.length && content.substring(start - prefix.length, start) == prefix)
            val suffixMatches = suffix.isBlank() || (end + suffix.length <= content.length && content.substring(end, end + suffix.length) == suffix)
            prefixMatches && suffixMatches
        }
        return contextual.size == 1
    }
    // Preserve U+200B: it is part of the current displayed DOM and Readium quote.
    private fun normalize(value: String): String = value.replace(Regex("\\s+"), " ").trim()
}
