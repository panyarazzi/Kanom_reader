package com.nextz.kanomtts.text.replacement

/**
 * Round-trippable TSV for replacement rules. Columns (tab-separated, with a header
 * row): pattern, replacement, isRegex, enabled, order, showInReader.
 *
 * LIMITATION: fields must not contain a literal tab or newline — such a value cannot
 * be represented, and the affected line is dropped on import. Validating rule fields
 * against this constraint is the rule-editor UI's responsibility (a later Settings
 * plan); this codec assumes clean input.
 */
object ReplacementTsv {
    private const val HEADER = "pattern\treplacement\tisRegex\tenabled\torder\tshowInReader"

    fun export(rules: List<ReplacementRule>): String =
        buildString {
            append(HEADER)
            for (r in rules) {
                append('\n')
                append(r.pattern).append('\t')
                append(r.replacement).append('\t')
                append(r.isRegex).append('\t')
                append(r.enabled).append('\t')
                append(r.order).append('\t')
                append(r.showInReader)
            }
        }

    fun import(tsv: String): List<ReplacementRule> {
        val lines = tsv.split('\n')
        if (lines.isEmpty()) return emptyList()
        return lines.drop(1).mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val c = line.split('\t')
            if (c.size != 5 && c.size != 6) return@mapNotNull null   // tolerate old 5-col
            val isRegex = c[2].toBooleanStrictOrNull() ?: return@mapNotNull null
            val enabled = c[3].toBooleanStrictOrNull() ?: return@mapNotNull null
            val order = c[4].toIntOrNull() ?: return@mapNotNull null
            val showInReader = if (c.size == 6) c[5].toBooleanStrictOrNull() ?: return@mapNotNull null else false
            ReplacementRule(c[0], c[1], isRegex, enabled, order, showInReader)
        }
    }
}
