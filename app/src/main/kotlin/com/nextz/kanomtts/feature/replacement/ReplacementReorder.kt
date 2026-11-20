package com.nextz.kanomtts.feature.replacement

import com.nextz.kanomtts.data.db.ReplacementRuleEntity

object ReplacementReorder {
    /** Move item [from]→[to] and rewrite every position to its new index. Returns the reordered list. */
    fun reassignPositions(rules: List<ReplacementRuleEntity>, from: Int, to: Int): List<ReplacementRuleEntity> {
        val ordered = if (from in rules.indices && to in rules.indices && from != to) {
            rules.toMutableList().apply { add(to, removeAt(from)) }
        } else {
            rules
        }
        return ordered.mapIndexed { i, r -> if (r.position == i) r else r.copy(position = i) }
    }
}
