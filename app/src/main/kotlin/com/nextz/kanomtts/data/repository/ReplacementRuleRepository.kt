package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ReplacementRuleDao
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.text.replacement.ReplacementRule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class ReplacementRuleRepository @Inject constructor(private val dao: ReplacementRuleDao) {
    /** Effective domain rules for a book (global + book-scoped), ready for ReplacementEngine. */
    fun observeEffective(bookId: String): Flow<List<ReplacementRule>> =
        dao.observeEffective(bookId).map { list -> list.map(RuleMapper::toDomain) }

    /** Effective visual rules for a book (showInReader=true, global + book-scoped), for display transform. */
    fun observeVisualEffective(bookId: String): Flow<List<ReplacementRule>> =
        dao.observeEffectiveVisual(bookId).map { list -> list.map(RuleMapper::toDomain) }

    fun observeGlobal(): Flow<List<ReplacementRuleEntity>> = dao.observeGlobal()
    suspend fun add(rule: ReplacementRuleEntity): Long =
        dao.insert(rule.copy(position = if (rule.position >= 0) rule.position else dao.maxPosition() + 1))
    suspend fun update(rule: ReplacementRuleEntity) = dao.update(rule)
    suspend fun remove(id: Long) = dao.delete(id)

    fun observeByBook(bookId: String): Flow<List<ReplacementRuleEntity>> = dao.observeByBook(bookId)
    fun globalCount(): Flow<Int> = dao.globalCount()
    suspend fun reorder(rules: List<ReplacementRuleEntity>) = dao.updateAll(rules)
}
