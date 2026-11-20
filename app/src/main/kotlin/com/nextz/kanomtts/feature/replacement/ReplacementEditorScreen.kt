package com.nextz.kanomtts.feature.replacement

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.db.ReplacementRuleEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplacementEditorScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: ReplacementEditorViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    // Resolved at composition: stringResource can't be called inside the launcher callbacks
    // (and LocalContext.getString in a callback trips the LocalContextGetResourceValueCall lint).
    val importedMsg = stringResource(R.string.rules_imported)
    val exportedMsg = stringResource(R.string.rules_exported)

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (text != null) {
                val result = viewModel.importTsv(text)
                val msg = importedMsg + if (result.skipped > 0) " (ข้าม ${result.skipped})" else ""
                withContext(Dispatchers.Main) { snackbar.showSnackbar(msg) }
            }
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/tab-separated-values")) { uri ->
        if (uri != null) {
            val tsv = viewModel.exportTsv()
            scope.launch(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(tsv) }
                withContext(Dispatchers.Main) {
                    snackbar.showSnackbar(exportedMsg)
                }
            }
        }
    }

    val state by viewModel.uiState.collectAsState()
    var editing by remember { mutableStateOf<ReplacementRuleEntity?>(null) }
    var addOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.replacements_title)) },
                navigationIcon = { IconButton(onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
                actions = {
                    IconButton(onClick = { importLauncher.launch(arrayOf("text/tab-separated-values", "text/plain", "text/*")) }) {
                        Icon(Icons.Filled.FileDownload, stringResource(R.string.rules_import))
                    }
                    IconButton(onClick = { exportLauncher.launch("replacements.tsv") }) {
                        Icon(Icons.Filled.FileUpload, stringResource(R.string.rules_export))
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { addOpen = true },
                modifier = Modifier.padding(bottom = contentPadding.calculateBottomPadding()),
            ) { Icon(Icons.Filled.Add, stringResource(R.string.action_add)) }
        },
    ) { inner ->
        Column(Modifier.padding(inner).padding(contentPadding)) {
            if (state.isBookScope && state.inheritedGlobalCount > 0) {
                Text(
                    stringResource(R.string.replacements_inherited, state.inheritedGlobalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (state.rules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.replacements_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val listState = rememberLazyListState()
                var draggingId by remember { mutableStateOf<Long?>(null) }
                var dragOffset by remember { mutableFloatStateOf(0f) }
                // Local working copy so the row follows the finger; committed to the VM on drop.
                var working by remember(state.rules) { mutableStateOf(state.rules) }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 96.dp),
                ) {
                    itemsIndexed(working, key = { _, r -> r.id }) { _, rule ->
                        val dragging = draggingId == rule.id
                        RuleRow(
                            rule = rule,
                            dragging = dragging,
                            onToggle = { viewModel.toggleEnabled(rule) },
                            onEdit = { editing = rule },
                            onDelete = { viewModel.deleteRule(rule.id) },
                            dragHandleModifier = Modifier.pointerInput(rule.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { draggingId = rule.id; dragOffset = 0f },
                                    onDragEnd = {
                                        val from = state.rules.indexOfFirst { it.id == rule.id }
                                        val to = working.indexOfFirst { it.id == rule.id }
                                        if (from >= 0 && to >= 0 && from != to) viewModel.reorder(from, to)
                                        draggingId = null; dragOffset = 0f
                                    },
                                    onDragCancel = { draggingId = null; dragOffset = 0f; working = state.rules },
                                    onDrag = { change, drag ->
                                        change.consume()
                                        dragOffset += drag.y
                                        val curIndex = working.indexOfFirst { it.id == rule.id }
                                        val rowPx = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: return@detectDragGesturesAfterLongPress
                                        // Swap with neighbor once the finger crosses half a row.
                                        if (dragOffset > rowPx / 2 && curIndex < working.lastIndex) {
                                            working = working.toMutableList().apply { add(curIndex + 1, removeAt(curIndex)) }
                                            dragOffset -= rowPx
                                        } else if (dragOffset < -rowPx / 2 && curIndex > 0) {
                                            working = working.toMutableList().apply { add(curIndex - 1, removeAt(curIndex)) }
                                            dragOffset += rowPx
                                        }
                                    },
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (addOpen) {
        ReplacementRuleSheet(
            initial = null,
            onSave = { p, r, rx, show -> viewModel.addRule(p, r, rx, show) },
            onDismiss = { addOpen = false },
        )
    }
    editing?.let { rule ->
        ReplacementRuleSheet(
            initial = rule,
            onSave = { p, r, rx, show -> viewModel.updateRule(rule.copy(pattern = p, replacement = r, isRegex = rx, showInReader = show)) },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun RuleRow(
    rule: ReplacementRuleEntity,
    dragging: Boolean = false,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    dragHandleModifier: Modifier = Modifier,
) {
    val containerColor = if (dragging) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface
    Surface(color = containerColor) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onEdit).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.DragHandle,
                contentDescription = null,
                modifier = dragHandleModifier.padding(end = 8.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column(Modifier.weight(1f)) {
                Text("${rule.pattern} → ${rule.replacement}", style = MaterialTheme.typography.bodyMedium)
                Row {
                    if (rule.isRegex) Text("regex ", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    if (rule.showInReader) Text("👁", style = MaterialTheme.typography.labelSmall)
                }
            }
            Switch(checked = rule.enabled, onCheckedChange = { onToggle() })
            IconButton(onDelete) { Icon(Icons.Filled.Delete, stringResource(R.string.action_delete)) }
        }
    }
}
