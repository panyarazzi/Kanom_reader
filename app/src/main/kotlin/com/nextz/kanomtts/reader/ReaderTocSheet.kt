package com.nextz.kanomtts.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextz.kanomtts.ui.theme.KanomTheme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

/**
 * Hierarchical ToC. Parent arrows expand/collapse only their own branch;
 * tapping a title continues to navigate to that original Readium Link.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class)
@Composable
fun ReaderTocSheet(
    toc: List<Link>,
    currentLocator: Locator?,
    onNavigate: (Link) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val currentHref = currentLocator?.href
    val currentIndex = ReaderTocFocus.currentIndex(toc, currentHref)
    var expandedPaths by remember(toc, currentIndex) {
        mutableStateOf(ReaderTocFocus.expandedAncestors(toc, currentHref))
    }
    val entries = ReaderTocFocus.visible(toc, expandedPaths)
    val currentEntryKey = currentIndex?.let { ReaderTocFocus.flatten(toc).getOrNull(it)?.key }
    val visibleCurrentIndex = entries.indexOfFirst { it.key == currentEntryKey }
    val listState = rememberLazyListState()

    LaunchedEffect(visibleCurrentIndex) {
        if (visibleCurrentIndex >= 0) listState.scrollToItem(visibleCurrentIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "สารบัญ",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "ไม่มีสารบัญ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(state = listState) {
                itemsIndexed(entries, key = { _, entry -> entry.key }) { index, entry ->
                    TocRow(
                        entry = entry,
                        isCurrent = entry.key == currentEntryKey,
                        expanded = entry.key in expandedPaths,
                        onToggle = {
                            expandedPaths = if (entry.key in expandedPaths) {
                                expandedPaths - entry.key
                            } else {
                                expandedPaths + entry.key
                            }
                        },
                        onClick = {
                            onNavigate(entry.link)
                            onDismiss()
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Composable
private fun TocRow(
    entry: ReaderTocFocus.Entry,
    isCurrent: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(
            start = (12 + entry.depth * 18).dp,
            end = 16.dp,
            top = 8.dp,
            bottom = 8.dp,
        ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (entry.hasChildren) {
            IconButton(onClick = onToggle, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "ย่อหัวข้อนี้" else "ขยายหัวข้อนี้",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }

        if (isCurrent) {
            Surface(color = accent, shape = CircleShape, modifier = Modifier.size(8.dp)) {}
            Spacer(Modifier.width(10.dp))
        } else {
            Spacer(Modifier.width(18.dp))
        }

        Text(
            text = entry.link.title ?: entry.link.href.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Preview(name = "ToC sheet — populated", showBackground = true)
@Composable
private fun TocSheetPreview() {
    KanomTheme {
        androidx.compose.foundation.layout.Column {
            Text("สารบัญ", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            val sampleLinks = listOf(
                SampleLink("เล่มที่ 1", children = listOf(
                    SampleLink("ภาคที่ 1", children = listOf(
                        SampleLink("บทที่ 1 — จุดเริ่มต้น"),
                        SampleLink("บทที่ 2 — การเดินทาง"),
                    )),
                    SampleLink("ภาคที่ 2", children = listOf(SampleLink("บทที่ 3 — ความลับ"))),
                )),
                SampleLink("เล่มที่ 2", children = listOf(SampleLink("บทที่ 4"))),
            )
            ReaderTocFocus.visible(sampleLinks, setOf("0", "0/0")).forEachIndexed { index, entry ->
                TocRow(entry, isCurrent = index == 2, expanded = entry.key in setOf("0", "0/0"), onToggle = {}, onClick = {})
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
private fun SampleLink(title: String, children: List<Link> = emptyList()): Link = Link(
    href = Href(Url("chapter/${title.hashCode()}.xhtml")!!),
    title = title,
    children = children,
)
