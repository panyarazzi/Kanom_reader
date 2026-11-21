package com.nextz.kanomtts.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nextz.kanomtts.ui.theme.KanomTheme
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Href
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.util.Url

/**
 * Hierarchical ToC in a left-side panel.
 *
 * The panel deliberately has no drag-to-dismiss gesture: vertical swipes belong to
 * the ToC list. It can be dismissed with Back or by tapping the scrim.
 */
@OptIn(ExperimentalReadiumApi::class)
@Composable
fun ReaderTocSheet(
    toc: List<Link>,
    currentLocator: Locator?,
    onNavigate: (Link) -> Unit,
    onDismiss: () -> Unit,
) {
    val currentHref = currentLocator?.href
    val flattened = remember(toc) { ReaderTocFocus.flatten(toc) }
    val currentIndex = ReaderTocFocus.currentIndex(toc, currentHref)
    val currentEntry = currentIndex?.let { flattened.getOrNull(it) }

    var expandedPaths by remember(toc, currentIndex) {
        mutableStateOf(ReaderTocFocus.expandedAncestors(toc, currentHref))
    }

    val entries = remember(toc, expandedPaths) {
        ReaderTocFocus.visible(toc, expandedPaths)
    }

    // When the current chapter is hidden by "Collapse all", keep its nearest
    // visible ancestor highlighted (normally the containing volume).
    val focusedVisibleKey = currentEntry?.let { current ->
        entries
            .filter { entry ->
                entry.path.size <= current.path.size &&
                    current.path.take(entry.path.size) == entry.path
            }
            .maxByOrNull { it.path.size }
            ?.key
    }

    val listState = rememberLazyListState()
    val focusedVisibleIndex = entries.indexOfFirst { it.key == focusedVisibleKey }

    LaunchedEffect(focusedVisibleKey, entries) {
        if (focusedVisibleIndex >= 0) listState.scrollToItem(focusedVisibleIndex)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.34f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
        ) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(maxWidth * 0.34f)
                    .shadow(12.dp, RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp),
            ) {
                Column(Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 12.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "สารบัญ",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = { expandedPaths = emptySet() },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandLess,
                                contentDescription = "ย่อทั้งหมด",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(
                            onClick = {
                                expandedPaths = ReaderTocFocus.expandablePaths(toc)
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = "ขยายทั้งหมด",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    if (entries.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "ไม่มีสารบัญ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.weight(1f),
                        ) {
                            itemsIndexed(entries, key = { _, entry -> entry.key }) { _, entry ->
                                TocRow(
                                    entry = entry,
                                    isCurrent = entry.key == focusedVisibleKey,
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = (8 + entry.depth * 12).dp,
                end = 8.dp,
                top = 8.dp,
                bottom = 8.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        if (entry.hasChildren) {
            IconButton(onClick = onToggle, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (expanded) "ย่อหัวข้อนี้" else "ขยายหัวข้อนี้",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(28.dp))
        }

        if (isCurrent) {
            Surface(
                color = accent,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .padding(top = 7.dp)
                    .size(7.dp),
            ) {}
            Spacer(Modifier.width(7.dp))
        } else {
            Spacer(Modifier.width(14.dp))
        }

        Text(
            text = entry.link.title ?: entry.link.href.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .weight(1f)
                .padding(top = 3.dp),
            // Intentionally no maxLines and no Ellipsis: long Thai chapter titles wrap
            // onto as many lines as needed within the narrow side panel.
        )
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Preview(name = "ToC side panel — populated", showBackground = true)
@Composable
private fun TocSheetPreview() {
    KanomTheme {
        Column {
            Text("สารบัญ", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(16.dp))
            val sampleLinks = listOf(
                SampleLink("เล่มที่ 1", children = listOf(
                    SampleLink("ภาคที่ 1", children = listOf(
                        SampleLink("บทที่ 1 — จุดเริ่มต้นของการเดินทางในโลกใบใหม่"),
                        SampleLink("บทที่ 2 — การเดินทางที่ยาวนานและความลับที่ไม่มีใครเคยรู้"),
                    )),
                    SampleLink("ภาคที่ 2", children = listOf(SampleLink("บทที่ 3 — ความลับ"))),
                )),
                SampleLink("เล่มที่ 2", children = listOf(SampleLink("บทที่ 4"))),
            )
            ReaderTocFocus.visible(sampleLinks, setOf("0", "0/0")).forEachIndexed { index, entry ->
                TocRow(
                    entry,
                    isCurrent = index == 2,
                    expanded = entry.key in setOf("0", "0/0"),
                    onToggle = {},
                    onClick = {},
                )
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
