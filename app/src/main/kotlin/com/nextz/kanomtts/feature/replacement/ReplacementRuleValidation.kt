package com.nextz.kanomtts.feature.replacement

/** Why a rule is invalid; null from [ReplacementRuleValidation.validate] means OK. */
enum class RuleError { EmptyPattern, InvalidRegex }

object ReplacementRuleValidation {
    /**
     * A non-null result blocks saving. Regex patterns are validated by compiling them, because
     * ReplacementEngine compiles every enabled regex in its constructor (at the next playback
     * stream start) — a bad regex saved here would crash playback.
     */
    fun validate(pattern: String, isRegex: Boolean): RuleError? {
        if (pattern.isBlank()) return RuleError.EmptyPattern
        if (isRegex && runCatching { Regex(pattern) }.isFailure) return RuleError.InvalidRegex
        return null
    }
}
