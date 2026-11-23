package com.nextz.kanomtts.feature.library

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun BookAnnotationsScreen(
    onBack: () -> Unit,
    viewModel: BookAnnotationsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var editing by remember { mutableStateOf<BookAnnotationRow?>(null) }
    var tagsText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var csvContent by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
                    it.write(csvContent)
                } ?: error("ไม่สามารถสร้างไฟล์ CSV ได้")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
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
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.books, key = { it.id }) { book ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium)
                        if (!book.author.isNullOrBlank()) Text(book.author, style = MaterialTheme.typography.bodySmall)
                        if (!book.sourceFileName.isNullOrBlank()) Text("ไฟล์: ${book.sourceFileName}", style = MaterialTheme.typography.bodySmall)
                        if (book.tags.isNotEmpty()) Text("Tags: ${book.tags.joinToString(" · ")}")
                        if (book.note.isNotBlank()) Text("Note: ${book.note}")
                        TextButton(onClick = {
                            editing = book
                            tagsText = book.tags.joinToString(", ")
                            noteText = book.note
                        }) { Text("แก้ไข Tags / Notes") }
                    }
                }
            }
        }
    }

    editing?.let { book ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("แก้ไขข้อมูล: ${book.title}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = tagsText, onValueChange = { tagsText = it },
                        label = { Text("Tags (คั่นด้วยจุลภาค)") }, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = noteText, onValueChange = { noteText = it },
                        label = { Text("Notes / รีวิว") }, modifier = Modifier.fillMaxWidth(), minLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.save(book.id, tagsText, noteText); editing = null }) { Text("บันทึก") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("ยกเลิก") } },
        )
    }
}
