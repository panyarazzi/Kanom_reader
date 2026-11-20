package com.nextz.kanomtts.text.replacement

class ReplacementEngine(rules: List<ReplacementRule>) {

    private val literalRules: List<ReplacementRule> =
        rules.filter { it.enabled && !it.isRegex }.sortedBy { it.order }
    private val regexRules: List<Pair<Regex, String>> =
        rules.filter { it.enabled && it.isRegex }.sortedBy { it.order }
            .map { Regex(it.pattern) to it.replacement }

    // pattern -> replacement; earliest `order` wins for duplicate identical patterns
    // (literalRules is sorted by order ascending, so putIfAbsent keeps the first).
    private val replacementOf: Map<String, String> = buildMap {
        for (r in literalRules) putIfAbsent(r.pattern, r.replacement)
    }
    private val automaton = AhoCorasick(literalRules.map { it.pattern })

    fun apply(text: String): String {
        val afterLiterals = applyLiterals(text)
        var out = afterLiterals
        for ((rx, repl) in regexRules) out = rx.replace(out, repl)
        return out
    }

    private fun applyLiterals(text: String): String {
        if (literalRules.isEmpty()) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            val len = automaton.firstMatchAt(text, i)
            if (len > 0) {
                val matched = text.substring(i, i + len)
                sb.append(replacementOf[matched] ?: matched)
                i += len
            } else {
                sb.append(text[i]); i++
            }
        }
        return sb.toString()
    }
}
