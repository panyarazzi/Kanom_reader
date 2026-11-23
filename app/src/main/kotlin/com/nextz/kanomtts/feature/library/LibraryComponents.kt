package com.nextz.kanomtts.feature.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.progress.formatProgressPercent
import com.nextz.kanomtts.ui.theme.KanomTheme

// ─── Brand Header ───────────────────────────────────────────────────────────────

/**
 * Original KanomTTS brand lockup: mascot badge, two-tone wordmark and Thai tagline.
 */
@Composable
fun BrandHeader(
    modifier: Modifier = Modifier,
) {
    val wordmark = buildAnnotatedString {
        withStyle(SpanStyle(color = KanomTheme.colors.highlight)) { append("Kanom") }
        withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) { append("TTS") }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(56.dp)
                .shadow(3.dp, CircleShape)
                .clip(CircleShape)
                .border(2.dp, KanomTheme.colors.highlightContainer, CircleShape),
        )
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = wordmark,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "หนังสือเสียงแสนหวาน",
                style = MaterialTheme.typography.bodySmall,
                color = KanomTheme.colors.muted,
            )
        }
    }
}

// ─── Continue Hero ─────────────────────────────────────────────────────────────

/**
 * Blue-gradient "continue listening" card.
 * Shows cover thumbnail, "ฟังต่อ · NN%" label, title, progress bar, round play button.
 */
@Composable
fun ContinueHero(
    card: BookCard,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradientStart = MaterialTheme.colorScheme.primary
    val gradientEnd = KanomTheme.colors.primaryGradientEnd

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
            .clip(MaterialTheme.shapes.large)
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(gradientStart, gradientEnd),
                ),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Small cover thumbnail
            CoverImage(
                coverPath = card.coverPath,
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.small),
            )

            // Text + progress block
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "อ่านต่อ · ${formatProgressPercent(card.percent.toDouble()) ?: "—"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                )
                Text(
                    text = card.displayTitle,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearProgressIndicator(
                    progress = { card.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            // Round play button
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.2f))
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "อ่านต่อ",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

// ─── Selection Bar ──────────────────────────────────────────────────────────────

/**
 * Shown in selection mode: close button, count label, delete button.
 */
@Composable
fun SelectionBar(
    count: Int,
    onClose: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = "ปิด")
        }
        Text(
            text = "เลือก $count เล่ม",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = "ลบ")
        }
    }
}

// ─── Book Grid Item ─────────────────────────────────────────────────────────────

/**
 * Grid card (~140 dp wide): cover art (or gradient placeholder), title, small progress bar.
 * Supports selection mode: long-press enters selection, tap toggles when in selection mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookGridItem(
    card: BookCard,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggle() else onClick() },
                onLongClick = onToggle,
            )
            .padding(bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box {
            CoverImage(
                coverPath = card.coverPath,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.7f)
                    .clip(MaterialTheme.shapes.medium),
            )
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                )
            }
        }
        Column(modifier = Modifier.padding(horizontal = 4.dp)) {
            Text(
                text = card.displayTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (card.percent > 0f) {
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { card.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
            }
        }
    }
}

// ─── Book List Item ─────────────────────────────────────────────────────────────

/**
 * List row: small cover, title + author (for LIST/DETAIL modes), progress bar + percentage.
 * Supports selection mode: long-press enters selection, tap toggles when in selection mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    card: BookCard,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = { if (isSelectionMode) onToggle() else onClick() },
                onLongClick = onToggle,
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (isSelectionMode) {
            Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        }
        CoverImage(
            coverPath = card.coverPath,
            modifier = Modifier
                .size(64.dp, 90.dp)
                .clip(MaterialTheme.shapes.small),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = card.displayTitle,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (!card.author.isNullOrBlank()) {
                Text(
                    text = card.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (card.percent > 0f) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { card.percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                )
                Text(
                    text = formatProgressPercent(card.percent.toDouble()) ?: "—",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

// ─── View Mode Toggle ───────────────────────────────────────────────────────────

/**
 * Segmented toggle: GRID / LIST only.
 * DETAIL is intentionally excluded — it renders identically to LIST and was confusing.
 * Uses `material-icons-extended`: GridView for grid, ViewList for list.
 */
@Composable
fun ViewModeToggle(
    mode: LibraryViewMode,
    onSelect: (LibraryViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    ) {
        listOf(LibraryViewMode.GRID, LibraryViewMode.LIST).forEach { viewMode ->
            val selected = viewMode == mode
            Box(
                modifier = Modifier
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(
                        if (selected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    )
                    .clickable { onSelect(viewMode) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = viewModeIcon(viewMode),
                    contentDescription = viewMode.name,
                    tint = if (selected) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Maps toggle-visible modes to their extended icons.
 * GRID → GridView, LIST → AutoMirrored ViewList.
 * DETAIL is not shown in the toggle but the enum value is retained for programmatic use.
 */
private fun viewModeIcon(mode: LibraryViewMode): ImageVector = when (mode) {
    LibraryViewMode.GRID -> Icons.Filled.GridView
    LibraryViewMode.LIST, LibraryViewMode.DETAIL -> Icons.AutoMirrored.Filled.ViewList
}

// ─── Library Search Bar ─────────────────────────────────────────────────────────

/**
 * Rounded pill search field with Thai placeholder "ค้นหาหนังสือ…".
 */
@Composable
fun LibrarySearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = {
            Text(
                text = "ค้นหาหนังสือ…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}

// ─── Internal helpers ───────────────────────────────────────────────────────────

/**
 * Coil AsyncImage with gradient placeholder when [coverPath] is null.
 */
@Composable
internal fun CoverImage(
    coverPath: String?,
    modifier: Modifier = Modifier,
) {
    if (coverPath != null) {
        AsyncImage(
            model = coverPath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        Box(
            modifier = modifier.background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    ),
                ),
            ),
        )
    }
}
