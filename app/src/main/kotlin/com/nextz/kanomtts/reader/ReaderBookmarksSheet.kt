package com.nextz.kanomtts.reader

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nextz.kanomtts.data.db.BookmarkEntity
import com.nextz.kanomtts.reader.engine.LocatorSerializer

/**
 * Bottom sheet for bookmark management.
 *
 * - "เพิ่มที่ตำแหน่งนี้" button adds a bookmark at the current reading position.
 * - List of bookmarks (newest first, ordered by createdAt DESC in the DAO query).
 * - Tap row text area → navigate to that bookmark and dismiss.
 * - Trailing delete IconButton removes the bookmark.
 * - Empty state shown when no bookmarks exist.
 *
 * Swipe-to-delete was considered but falls back to a trailing delete IconButton
 * per brief guidance — avoids SwipeToDismissBox gesture conflicts with the
 * ModalBottomSheet's own swipe-to-dismiss gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderBookmarksSheet(
    bookmarks: List<BookmarkEntity>,
    onAddCurrent: () -> Unit,
    onNavigate: (locatorJson: String) -> Unit,
    onRemove: (id: Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "ที่คั่นหน้า",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )

        Button(
            onClick = onAddCurrent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        ) {
            Text(text = "เพิ่มที่ตำแหน่งนี้")
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        if (bookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ยังไม่มีที่คั่นหน้า",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkRow(
                        bookmark = bookmark,
                        onNavigate = {
                            onNavigate(bookmark.locator)
                            onDismiss()
                        },
                        onRemove = { onRemove(bookmark.id) },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun BookmarkRow(
    bookmark: BookmarkEntity,
    onNavigate: () -> Unit,
    onRemove: () -> Unit,
) {
    // Decode the locator once per row so JSON isn't re-parsed on every recomposition.
    val label = remember(bookmark.locator, bookmark.snippet) {
        resolveBookmarkLabel(bookmark.snippet, LocatorSerializer.decode(bookmark.locator))
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onNavigate)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                text = label.primary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (label.secondary.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = label.secondary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "ลบที่คั่นหน้า",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
