package com.nextz.kanomtts.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.data.db.ReaderHighlightEntity
import com.nextz.kanomtts.reader.engine.LocatorSerializer

/** Shared management surface for point bookmarks and text-range highlights. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    highlights: List<ReaderHighlightEntity>,
    onAddCurrent: () -> Unit,
    onNavigateBookmark: (locatorJson: String) -> Unit,
    onRemoveBookmark: (id: Long) -> Unit,
    onNavigateHighlight: (ReaderHighlightEntity) -> Unit,
    onRemoveHighlight: (id: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    var selectedTab by rememberSaveable { mutableStateOf("bookmarks") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Text(
            text = "ที่คั่นหน้าและไฮไลท์",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(selected = selectedTab == "bookmarks", onClick = { selectedTab = "bookmarks" },
                label = { Text("ที่คั่นหน้า (${bookmarks.size})") })
            FilterChip(selected = selectedTab == "highlights", onClick = { selectedTab = "highlights" },
                label = { Text("ไฮไลท์ (${highlights.size})") })
        }

        if (selectedTab == "bookmarks") {
            Button(
                onClick = onAddCurrent,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp),
            ) { Text("เพิ่มที่ตำแหน่งนี้") }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (bookmarks.isEmpty()) {
                EmptyMarksMessage("ยังไม่มีที่คั่นหน้า")
            } else {
                LazyColumn {
                    items(bookmarks, key = { it.id }) { bookmark ->
                        BookmarkRow(bookmark, onNavigate = {
                            onNavigateBookmark(bookmark.locator)
                            onDismiss()
                        }, onRemove = { onRemoveBookmark(bookmark.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (highlights.isEmpty()) {
                EmptyMarksMessage("ยังไม่มีไฮไลท์")
            } else {
                LazyColumn {
                    items(highlights, key = { it.id }) { highlight ->
                        HighlightRow(highlight, onNavigate = {
                            onNavigateHighlight(highlight)
                            onDismiss()
                        }, onRemove = { onRemoveHighlight(highlight.id) })
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyMarksMessage(message: String) {
    Box(Modifier.fillMaxWidth().padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookmarkRow(bookmark: BookmarkEntity, onNavigate: () -> Unit, onRemove: () -> Unit) {
    val label = remember(bookmark.locator, bookmark.snippet) {
        resolveBookmarkLabel(bookmark.snippet, LocatorSerializer.decode(bookmark.locator))
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onNavigate).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(label.primary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (label.secondary.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(label.secondary, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "ลบที่คั่นหน้า", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HighlightRow(highlight: ReaderHighlightEntity, onNavigate: () -> Unit, onRemove: () -> Unit) {
    val chapter = remember(highlight.locator) {
        LocatorSerializer.decode(highlight.locator)?.title?.let(::cleanSnippet).orEmpty()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).clickable(onClick = onNavigate).padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(highlight.selectedText, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold,
                maxLines = 3, overflow = TextOverflow.Ellipsis)
            if (chapter.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(chapter, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "ลบไฮไลท์", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
