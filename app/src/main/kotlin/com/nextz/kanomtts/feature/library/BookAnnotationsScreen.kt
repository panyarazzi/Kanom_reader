package com.nextz.kanomtts.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val standardBookTags = listOf("แนะนำ", "อ่านจบแล้ว", "กำลังอ่าน", "รออ่าน", "แปลดี", "สำนวนดี", "ไม่แนะนำ")
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BookAnnotationsScreen(
    onBack: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: BookAnnotationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<BookAnnotationRow?>(null) }
    var tagsText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var csvContent by remember { mutableStateOf("") }
    var exportError by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            exportError = runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    // UTF-8 BOM helps spreadsheet apps recognize Thai text correctly.
                    it.write("\uFEFF")
                    it.write(csvContent)
                } ?: error("ไม่สามารถสร้างไฟล์ CSV ได้")
            }.exceptionOrNull()?.let { "ส่งออก CSV ไม่สำเร็จ: ${it.message ?: "เกิดข้อผิดพลาด"}" }
        }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ")
                }
                Column {
                    Text("Tags และ Notes", style = MaterialTheme.typography.titleLarge)
                    Text("บันทึกคำกำกับและรีวิวแยกตามหนังสือ", style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = {
                csvContent = viewModel.exportCsv()
                exportLauncher.launch("Kanom-Book-Reviews.csv")
            }) { Text("ส่งออก CSV") }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            label = { Text("ค้นหาชื่อหนังสือ ผู้แต่ง Tag หรือ Note") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterChip(
                selected = state.notesOnly,
                onClick = { viewModel.setNotesOnly(!state.notesOnly) },
                label = { Text("มี Notes") },
            )
            state.availableTags.forEach { tag ->
                FilterChip(
                    selected = state.selectedTag == tag,
                    onClick = { viewModel.setTagFilter(tag) },
                    label = { Text(tag) },
                )
            }
        }
        if (state.savedTags.isNotEmpty()) {
            Text("Tag ที่บันทึกไว้ใช้ซ้ำ · แตะ × เพื่อนำออกจากคลัง Tag", style = MaterialTheme.typography.labelSmall)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                state.savedTags.forEach { tag ->
                    TextButton(onClick = { viewModel.deleteReusableTag(tag) }) { Text("× $tag") }
                }
            }
        }
        Text("แสดง ${state.books.size} จาก ${state.allBooks.size} เล่ม", style = MaterialTheme.typography.bodySmall)
        exportError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val grouped = state.books.groupBy { it.sourceFolder?.takeIf(String::isNotBlank) ?: "ไม่ระบุโฟลเดอร์" }
            grouped.forEach { (folder, booksInFolder) ->
                item(key = "folder:$folder") {
                    Text(folder, style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
                }
                items(booksInFolder, key = { it.id }) { book ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(book.title, style = MaterialTheme.typography.titleMedium)
                            if (!book.author.isNullOrBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall)
                            if (!book.sourceFileName.isNullOrBlank()) Text("ไฟล์: ${book.sourceFileName}", style = MaterialTheme.typography.bodySmall)
                            if (book.tags.isNotEmpty()) Text("Tags: ${book.tags.joinToString(" · ")}")
                            if (book.note.isNotBlank()) Text("Note: ${book.note}")
                            TextButton(onClick = {
                                viewModel.clearSaveError()
                            editing = book
                                tagsText = book.tags.joinToString(", ")
                                noteText = book.note
                            }) { Text("แก้ไข Tags / Notes") }
                        }
                    }
                }
            }
        }
    }

    editing?.let { book ->
        AlertDialog(
            onDismissRequest = { if (!saveState.saving) editing = null },
            title = { Text("แก้ไขข้อมูล: ${book.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tagsText, onValueChange = { tagsText = it },
                        label = { Text("Tags (คั่นด้วยจุลภาค)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Tag มาตรฐาน", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        standardBookTags.forEach { tag ->
                            val selected = tagsText.split(',', ';', '\\n').any { it.trim().equals(tag, true) }
                            FilterChip(selected = selected, onClick = {
                                val current = tagsText.split(',', ';', '\\n').map { it.trim() }.filter { it.isNotEmpty() }
                                val next = if (selected) current.filterNot { it.equals(tag, true) } else current + tag
                                tagsText = next.distinctBy { it.lowercase() }.joinToString(", ")
                            }, label = { Text(tag) })
                        }
                    }
                    if (state.savedTags.isNotEmpty()) {
                        Text("Tag ที่บันทึกไว้", style = MaterialTheme.typography.labelMedium)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            state.savedTags.forEach { tag ->
                                val selected = tagsText.split(',', ';', '\\n').any { it.trim().equals(tag, true) }
                                FilterChip(selected = selected, onClick = {
                                    val current = tagsText.split(',', ';', '\\n').map { it.trim() }.filter { it.isNotEmpty() }
                                    val next = if (selected) current.filterNot { it.equals(tag, true) } else current + tag
                                    tagsText = next.distinctBy { it.lowercase() }.joinToString(", ")
                                }, label = { Text(tag) })
                            }
                        }
                    }
                    TextButton(onClick = {
                        tagsText.split(',', ';', '\\n').map { it.trim() }.filter { it.isNotEmpty() }
                            .forEach(viewModel::saveReusableTag)
                    }) { Text("บันทึก Tag ในช่องนี้ไว้ใช้ซ้ำ") }
                    OutlinedTextField(
                        value = noteText, onValueChange = { noteText = it },
                        label = { Text("Notes / รีวิว") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    )
                    saveState.error?.let { error ->
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !saveState.saving,
                    onClick = { viewModel.save(book.id, tagsText, noteText) { editing = null } },
                ) { Text(if (saveState.saving) "กำลังบันทึก…" else "บันทึก") }
            },
            dismissButton = {
                TextButton(enabled = !saveState.saving, onClick = { editing = null }) { Text("ยกเลิก") }
            },
        )
    }
}
