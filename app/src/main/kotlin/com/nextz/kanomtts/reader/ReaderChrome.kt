package com.nextz.kanomtts.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Tune
import com.nextz.kanomtts.BuildConfig
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextz.kanomtts.ui.theme.KanomTheme

/**
 * Transparent overlay drawn OVER [EpubNavigatorFragment] that provides a [TopAppBar]
 * (shown only when [chromeVisible]) with back, ToC, settings, bookmarks, and TTS actions.
 *
 * Gesture handling (tap/swipe page-nav, chrome toggle) is now driven entirely by
 * Readium's InputListener pipeline registered in [ReaderViewModel.bindNavigator], so this
 * composable has no pointer-input modifiers. Long-press flows natively to the WebView, enabling
 * native text selection + the [selectionActionModeCallback] menu.
 *
 * Task 5 adds a Settings action; Task 7 adds a Bookmark action — both go into the same
 * [TopAppBar.actions] slot with no structural change needed here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderChrome(
    title: String,
    chromeVisible: Boolean,
    onBack: () -> Unit,
    onTocOpen: () -> Unit,
    onSettingsOpen: () -> Unit,
    onBookmarksOpen: () -> Unit,
    /** True when TTS is actively playing; determines the icon (Pause vs PlayArrow). */
    ttsPlaying: Boolean = false,
    onToggleTts: () -> Unit = {},
    /** Current page / total pages and chapter title, based on Readium's pagination callback. */
    progressText: String? = null,
    progressFontSizeSp: Int = 24,
    /** Footer tap opens ToC; double-tap opens reader settings while progress is visible. */
    onFooterTap: () -> Unit = {},
    onFooterDoubleTap: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** DEBUG-ONLY probe toggle. Pass null (default) in release / previews. */
    onToggleProbe: (() -> Unit)? = null,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Persistent progress labels stay visible even when the toolbar auto-hides.
        if (!progressText.isNullOrBlank()) {
            ProgressLabel(
                text = progressText,
                fontSizeSp = progressFontSizeSp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = if (chromeVisible) 64.dp else 8.dp),
            )
            ProgressLabel(
                text = progressText,
                fontSizeSp = progressFontSizeSp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 8.dp),
            )
            // The footer is an intentional touch target: single tap opens the ToC,
            // double tap opens reader settings. It exists only while Footer Progress is visible.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(44.dp)
                    .pointerInput(onFooterTap, onFooterDoubleTap) {
                        detectTapGestures(
                            onTap = { onFooterTap() },
                            onDoubleTap = { onFooterDoubleTap() },
                        )
                    },
            )
        }

        // Top app bar — animated in/out with fade.
        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "กลับ",
                        )
                    }
                },
                actions = {
                    // ToC action — Task 4
                    IconButton(onClick = onTocOpen) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.List,
                            contentDescription = "สารบัญ",
                        )
                    }
                    // Settings action — Task 5
                    IconButton(onClick = onSettingsOpen) {
                        Icon(
                            imageVector = Icons.Filled.Tune,
                            contentDescription = "ตั้งค่าการแสดงผล",
                        )
                    }
                    // Bookmarks action — Task 7
                    IconButton(onClick = onBookmarksOpen) {
                        Icon(
                            imageVector = Icons.Filled.Bookmarks,
                            contentDescription = "ที่คั่นหน้า",
                        )
                    }
                    // TTS play/pause toggle — Task 11
                    IconButton(onClick = onToggleTts) {
                        Icon(
                            imageVector = if (ttsPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (ttsPlaying) "หยุดชั่วคราว" else "เล่นเสียง",
                        )
                    }
                    // DEBUG-ONLY: sentence-highlight probe toggle (Task 8).
                    // Shown only in debug builds and only when a callback is wired.
                    if (BuildConfig.DEBUG && onToggleProbe != null) {
                        IconButton(onClick = onToggleProbe) {
                            Icon(
                                imageVector = Icons.Filled.BugReport,
                                contentDescription = "สลับ probe ไฮไลต์ประโยค (debug)",
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

@Composable
private fun ProgressLabel(text: String, fontSizeSp: Int, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(50),
        tonalElevation = 2.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSizeSp.coerceIn(8, 80).sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "ReaderChrome — visible", showBackground = true, backgroundColor = 0xFF_E8E0D5)
@Composable
private fun ReaderChromeVisiblePreview() {
    KanomTheme {
        ReaderChrome(
            title = "ขนมไทยโบราณ",
            chromeVisible = true,
            onBack = {},
            onTocOpen = {},
            onSettingsOpen = {},
            onBookmarksOpen = {},
        )
    }
}

@Preview(name = "ReaderChrome — hidden", showBackground = true, backgroundColor = 0xFF_E8E0D5)
@Composable
private fun ReaderChromeHiddenPreview() {
    KanomTheme {
        ReaderChrome(
            title = "ขนมไทยโบราณ",
            chromeVisible = false,
            onBack = {},
            onTocOpen = {},
            onSettingsOpen = {},
            onBookmarksOpen = {},
        )
    }
}
