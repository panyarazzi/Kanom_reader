package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.text.replacement.ReplacementRule

object RuleMapper {
    fun toDomain(e: ReplacementRuleEntity): ReplacementRule =
        ReplacementRule(
            pattern = e.pattern,
            replacement = e.replacement,
            isRegex = e.isRegex,
            enabled = e.enabled,
            order = e.position,
            showInReader = e.showInReader,
        )

    fun toEntity(rule: ReplacementRule, scope: String, bookId: String?, id: Long = 0): ReplacementRuleEntity =
        ReplacementRuleEntity(
            id = id,
            scope = scope,
            bookId = bookId,
            pattern = rule.pattern,
            replacement = rule.replacement,
            isRegex = rule.isRegex,
            enabled = rule.enabled,
            position = rule.order,
            showInReader = rule.showInReader,
        )
}
