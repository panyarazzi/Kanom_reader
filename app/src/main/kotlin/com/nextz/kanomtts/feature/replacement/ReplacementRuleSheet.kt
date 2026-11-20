package com.nextz.kanomtts.feature.replacement

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.db.ReplacementRuleEntity

/**
 * Bottom sheet for adding or editing one rule. [initial] null = add; non-null = edit.
 * [scopeContent] is an optional composable slot rendered above the Save button (used by the
 * reader quick-add path to show a Global / This-book scope choice).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplacementRuleSheet(
    initial: ReplacementRuleEntity?,
    prefilledPattern: String = "",
    onSave: (pattern: String, replacement: String, isRegex: Boolean, showInReader: Boolean) -> Unit,
    onDismiss: () -> Unit,
    scopeContent: (@Composable () -> Unit)? = null,
) {
    var pattern by remember { mutableStateOf(initial?.pattern ?: prefilledPattern) }
    var replacement by remember { mutableStateOf(initial?.replacement ?: "") }
    var isRegex by remember { mutableStateOf(initial?.isRegex ?: false) }
    var showInReader by remember { mutableStateOf(initial?.showInReader ?: false) }
    val error = ReplacementRuleValidation.validate(pattern, isRegex)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp)) {
            OutlinedTextField(
                value = pattern, onValueChange = { pattern = it },
                label = { Text(stringResource(R.string.rule_pattern_hint)) },
                isError = error == RuleError.EmptyPattern || error == RuleError.InvalidRegex,
                supportingText = {
                    when (error) {
                        RuleError.EmptyPattern -> Text(stringResource(R.string.rule_error_empty))
                        RuleError.InvalidRegex -> Text(stringResource(R.string.rule_error_regex))
                        null -> {}
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = replacement, onValueChange = { replacement = it },
                label = { Text(stringResource(R.string.rule_replacement_hint)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rule_regex), Modifier.weight(1f))
                Switch(checked = isRegex, onCheckedChange = { isRegex = it })
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.rule_show_in_reader), Modifier.weight(1f))
                Switch(checked = showInReader, onCheckedChange = { showInReader = it })
            }
            scopeContent?.invoke()
            Button(
                onClick = { onSave(pattern.trim(), replacement, isRegex, showInReader); onDismiss() },
                enabled = error == null,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_save)) }
        }
    }
}
