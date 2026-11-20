package com.nextz.kanomtts.feature.replacement

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import com.nextz.kanomtts.data.repository.ReplacementRuleRepository
import com.nextz.kanomtts.data.repository.RuleMapper
import com.nextz.kanomtts.text.replacement.ReplacementTsv
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ReplacementEditorUiState(
    val rules: List<ReplacementRuleEntity> = emptyList(),
    val inheritedGlobalCount: Int = 0,
    val isBookScope: Boolean = false,
)

data class TsvImportResult(val imported: Int, val skipped: Int)

@HiltViewModel
class ReplacementEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repo: ReplacementRuleRepository,
) : ViewModel() {

    private val bookId: String? = savedStateHandle["bookId"]
    private val scopeStr: String = if (bookId == null) "global" else "book"

    val uiState: StateFlow<ReplacementEditorUiState> =
        if (bookId == null) {
            repo.observeGlobal().map { ReplacementEditorUiState(it, 0, false) }
        } else {
            combine(repo.observeByBook(bookId), repo.globalCount()) { rules, gc ->
                ReplacementEditorUiState(rules, gc, true)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReplacementEditorUiState(isBookScope = bookId != null))

    fun addRule(pattern: String, replacement: String, isRegex: Boolean, showInReader: Boolean = false) {
        if (ReplacementRuleValidation.validate(pattern, isRegex) != null) return
        viewModelScope.launch {
            repo.add(ReplacementRuleEntity(
                scope = scopeStr, bookId = bookId, pattern = pattern, replacement = replacement,
                isRegex = isRegex, enabled = true, position = -1, // -1 → repo auto-appends
                showInReader = showInReader,
            ))
        }
    }

    fun updateRule(rule: ReplacementRuleEntity) {
        if (ReplacementRuleValidation.validate(rule.pattern, rule.isRegex) != null) return
        viewModelScope.launch { repo.update(rule) }
    }

    fun deleteRule(id: Long) = viewModelScope.launch { repo.remove(id) }

    fun toggleEnabled(rule: ReplacementRuleEntity) =
        viewModelScope.launch { repo.update(rule.copy(enabled = !rule.enabled)) }

    fun reorder(from: Int, to: Int) = viewModelScope.launch {
        val currentRules = if (bookId == null) repo.observeGlobal().first() else repo.observeByBook(bookId).first()
        repo.reorder(ReplacementReorder.reassignPositions(currentRules, from, to))
    }

    fun exportTsv(): String = ReplacementTsv.export(uiState.value.rules.map(RuleMapper::toDomain))

    suspend fun importTsv(text: String): TsvImportResult {
        val parsed = ReplacementTsv.import(text)
        val valid = parsed.filter { ReplacementRuleValidation.validate(it.pattern, it.isRegex) == null }
        valid.forEach { domain ->
            repo.add(RuleMapper.toEntity(domain, scopeStr, bookId).copy(position = -1))
        }
        return TsvImportResult(imported = valid.size, skipped = parsed.size - valid.size)
    }
}
