package com.nextz.kanomtts.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Bottom sheet listing every table-of-contents entry in depth-first order.
 * Nested entries are indented by their depth and retain their original Readium Link,
 * so selecting any level navigates through the same ReaderViewModel.goTo(link) path.
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
    val entries = ReaderTocFocus.flatten(toc)
    val currentIndex = ReaderTocFocus.currentIndex(toc, currentLocator?.href)
    val listState = rememberLazyListState()

    // Focus the exact rendered entry, including nested chapters.
    LaunchedEffect(currentIndex) {
        if (currentIndex != null) listState.scrollToItem(currentIndex)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Text(
            text = "สารบัญ",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp),
        )

        if (entries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
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
            LazyColumn(state = listState) {
                itemsIndexed(entries) { index, entry ->
                    TocRow(
                        link = entry.link,
                        depth = entry.depth,
                        isCurrent = index == currentIndex,
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
    link: Link,
    depth: Int,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = (16 + depth * 18).dp,
                end = 16.dp,
                top = 14.dp,
                bottom = 14.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isCurrent) {
            Surface(
                color = accent,
                shape = CircleShape,
                modifier = Modifier.size(8.dp),
            ) {}
            Spacer(Modifier.width(10.dp))
        } else if (depth > 0) {
            // Keep nested titles aligned when the current-row marker is visible elsewhere.
            Spacer(Modifier.width(18.dp))
        }
        Text(
            text = link.title ?: link.href.toString(),
            style = MaterialTheme.typography.bodyLarge,
            color = if (isCurrent) accent else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ─── Previews ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalReadiumApi::class)
@Preview(name = "ToC sheet — populated", showBackground = true)
@Composable
private fun TocSheetPreview() {
    KanomTheme {
        androidx.compose.foundation.layout.Column {
            Text(
                text = "สารบัญ",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val sampleLinks = listOf(
                SampleLink("เล่มที่ 1", children = listOf(
                    SampleLink("บทที่ 1 — จุดเริ่มต้น"),
                    SampleLink("บทที่ 2 — การเดินทาง"),
                )),
                SampleLink("เล่มที่ 2", children = listOf(
                    SampleLink("บทที่ 3 — ความลับ"),
                )),
            )
            ReaderTocFocus.flatten(sampleLinks).forEachIndexed { index, entry ->
                TocRow(
                    link = entry.link,
                    depth = entry.depth,
                    isCurrent = index == 2,
                    onClick = {},
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@OptIn(ExperimentalReadiumApi::class)
@Preview(name = "ToC sheet — empty", showBackground = true)
@Composable
private fun TocSheetEmptyPreview() {
    KanomTheme {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "ไม่มีสารบัญ",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Sample [Link] for previews only. */
@OptIn(ExperimentalReadiumApi::class)
private fun SampleLink(title: String, children: List<Link> = emptyList()): Link = Link(
    href = Href(Url("chapter/${title.hashCode()}.xhtml")!!),
    title = title,
    children = children,
)
