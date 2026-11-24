package com.nextz.kanomtts.playback.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.nextz.kanomtts.ui.theme.KanomTheme

/**
 * Stateful wrapper — Hilt-injected in the real app.
 * Renders nothing when playback is idle (visible = false).
 */
@Composable
fun MiniPlayer(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    MiniPlayerInline(
        onExpand = onExpand,
        modifier = modifier,
        viewModel = viewModel,
    )
}

/**
 * Inline player slot for Library. It disappears completely when playback is idle,
 * so the Statistics/Listening layout never reserves empty space.
 */
@Composable
fun MiniPlayerInline(
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaybackViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    if (!state.visible) return
    MiniPlayerBar(
        state = state,
        onPlayPause = viewModel::playPause,
        onNext = viewModel::next,
        onPrev = viewModel::prev,
        onSleep = { viewModel.setSleep(15) },
        onExpand = onExpand,
        modifier = modifier,
    )
}

/**
 * Stateless visual — accepts hoisted state for Previews (no Hilt needed).
 *
 * Keeps the latest-book card visible while idle; transport controls appear only during playback.
 */
@Composable
fun MiniPlayerBar(
    state: MiniPlayerUiState,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onSleep: () -> Unit,
    onExpand: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        tonalElevation = 4.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Cover thumbnail + title/author — tapping anywhere here expands to Now-Playing
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onExpand),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniCoverImage(
                    coverPath = state.coverPath,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(MaterialTheme.shapes.small),
                )

                // Title + author/voice info
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = state.title,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (!state.author.isNullOrBlank()) {
                            Text(
                                text = state.author,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        if (state.visible && state.voiceLabel != null) {
                            VoiceChip(label = state.voiceLabel)
                        }
                    }
                }
            }

            // Sleep timer is available only during an active playback session.
            if (state.visible && state.sleepRemainingMs != null) {
                SleepChip(
                    remainingMs = state.sleepRemainingMs,
                    onClick = onSleep,
                )
            }

            // Keep the book card visible while idle; transport controls require a live session.
            if (state.visible) {
                IconButton(onClick = onPrev, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipPrevious,
                        contentDescription = if (state.byParagraph) "ย่อหน้าก่อนหน้า" else "ประโยคก่อนหน้า",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "หยุดชั่วคราว" else "เล่น",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp),
                    )
                }
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = if (state.byParagraph) "ย่อหน้าถัดไป" else "ประโยคถัดไป",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

// ─── Internal sub-components ───────────────────────────────────────────────────

@Composable
private fun MiniCoverImage(
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

@Composable
private fun VoiceChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Composable
private fun SleepChip(
    remainingMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val totalSec = remainingMs / 1_000L
    val minutes = totalSec / 60
    val seconds = totalSec % 60
    val label = "%d:%02d".format(minutes, seconds)

    TextButton(
        onClick = onClick,
        modifier = modifier.height(28.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(
            text = "💤 $label",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

// ─── Previews ─────────────────────────────────────────────────────────────────

private val previewPlayingState = MiniPlayerUiState(
    visible = true,
    isPlaying = true,
    title = "ขนมไทยโบราณ บทที่หนึ่ง",
    author = "สมชาย ขนมดี",
    coverPath = null,
    voiceLabel = "ไทย",
    sleepRemainingMs = 14 * 60_000L + 37_000L,
)

private val previewPausedState = MiniPlayerUiState(
    visible = true,
    isPlaying = false,
    title = "The Art of Thai Desserts",
    author = "Jane Smith",
    coverPath = null,
    voiceLabel = "อังกฤษ",
    sleepRemainingMs = null,
)

@Preview(name = "MiniPlayer — Playing (with sleep timer)", showBackground = true)
@Composable
private fun MiniPlayerPlayingPreview() {
    KanomTheme {
        MiniPlayerBar(
            state = previewPlayingState,
            onPlayPause = {},
            onNext = {},
            onPrev = {},
            onSleep = {},
        )
    }
}

@Preview(name = "MiniPlayer — Paused", showBackground = true)
@Composable
private fun MiniPlayerPausedPreview() {
    KanomTheme {
        MiniPlayerBar(
            state = previewPausedState,
            onPlayPause = {},
            onNext = {},
            onPrev = {},
            onSleep = {},
        )
    }
}

@Preview(name = "MiniPlayer — Hidden (idle)", showBackground = true)
@Composable
private fun MiniPlayerHiddenPreview() {
    KanomTheme {
        Column {
            Text("(nothing below — mini-player is hidden when idle)")
            Spacer(Modifier.height(8.dp))
            MiniPlayerBar(
                state = MiniPlayerUiState(
                    visible = false,
                    isPlaying = false,
                    title = "",
                    author = null,
                    coverPath = null,
                    voiceLabel = null,
                    sleepRemainingMs = null,
                ),
                onPlayPause = {},
                onNext = {},
                onPrev = {},
                onSleep = {},
            )
        }
    }
}
