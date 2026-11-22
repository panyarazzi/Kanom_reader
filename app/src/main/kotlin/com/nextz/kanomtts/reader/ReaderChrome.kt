package com.nextz.kanomtts.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nextz.kanomtts.ui.theme.KanomTheme
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    progressContent: ProgressContent? = null,
    topProgressLayout: ProgressLayout = ProgressLayout("CHAPTER_PAGE", "CHAPTER_TITLE", "TIME_BATTERY"),
    footerProgressLayout: ProgressLayout = ProgressLayout("BOOK_PERCENT", "CHAPTER_TITLE", "TIME_BATTERY"),
    progressFontSizeSp: Int = 24,
    topProgressEnabled: Boolean = true,
    footerProgressEnabled: Boolean = true,
    /** Footer tap opens ToC; double-tap opens reader settings while progress is visible. */
    onFooterTap: () -> Unit = {},
    onFooterDoubleTap: () -> Unit = {},
    onTopProgressHeightChanged: (Dp) -> Unit = {},
    onFooterProgressHeightChanged: (Dp) -> Unit = {},
    modifier: Modifier = Modifier,
    /** DEBUG-ONLY probe toggle. Pass null (default) in release / previews. */
    onToggleProbe: (() -> Unit)? = null,
) {
    val density = LocalDensity.current
    val context = LocalContext.current
    var clockText by remember { mutableStateOf("") }
    var batteryPercent by remember { mutableStateOf<Int?>(null) }
    var isCharging by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            clockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(30_000)
        }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale) else null
                val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL
            }
        }
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    val liveProgress = progressContent?.copy(
        time = clockText.takeIf { it.isNotBlank() },
        battery = batteryPercent?.let { "$it%" },
        charging = isCharging,
    )
    Box(modifier = modifier.fillMaxSize()) {
        if (liveProgress != null) {
            if (topProgressEnabled) ProgressLabel(
                layout = topProgressLayout,
                content = liveProgress,
                fontSizeSp = progressFontSizeSp,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 64.dp)
                    .onSizeChanged { size -> onTopProgressHeightChanged(with(density) { size.height.toDp() }) },
            )
            if (footerProgressEnabled) ProgressLabel(
                layout = footerProgressLayout,
                content = liveProgress,
                fontSizeSp = progressFontSizeSp,
                maxLines = 2,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
                    .onSizeChanged { size -> onFooterProgressHeightChanged(with(density) { size.height.toDp() }) },
            )
            if (footerProgressEnabled) Box(
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
private fun ProgressLabel(
    layout: ProgressLayout,
    content: ProgressContent,
    fontSizeSp: Int,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val values = progressSlotValues(layout, content)
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 2.dp,
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressSlot(values.left, fontSizeSp, maxLines, Alignment.CenterStart, Modifier.weight(1f))
            ProgressSlot(values.center, fontSizeSp, maxLines, Alignment.Center, Modifier.weight(1f))
            ProgressSlot(values.right, fontSizeSp, maxLines, Alignment.CenterEnd, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ProgressSlot(
    value: String?,
    fontSizeSp: Int,
    maxLines: Int,
    alignment: Alignment,
    modifier: Modifier,
) {
    Box(modifier = modifier, contentAlignment = alignment) {
        if (!value.isNullOrBlank()) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSizeSp.coerceIn(8, 80).sp),
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
