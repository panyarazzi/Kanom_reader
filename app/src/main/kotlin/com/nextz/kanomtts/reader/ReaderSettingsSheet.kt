package com.nextz.kanomtts.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.prefs.KanomPreferences
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.ui.theme.KanomTheme
import com.nextz.kanomtts.ui.theme.ReaderTheme

/**
 * Bottom sheet for core display settings: theme, font family, font size,
 * line height, margin, and Task 6 escape-hatch toggles.
 * Each control calls the corresponding [ReaderViewModel] setter, which writes
 * through [PreferencesRepository] and live-applies via the navigator's [submitPreferences].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderSettingsSheet(
    viewModel: ReaderViewModel,
    onDismiss: () -> Unit,
    onOpenReplacements: () -> Unit = {},
) {
    val state by viewModel.uiState.collectAsState()
    val prefs = (state as? ReaderUiState.Ready)?.prefs ?: KanomPreferences()

    ReaderSettingsSheetContent(
        prefs = prefs,
        onTheme = { viewModel.onThemeSelected(it) },
        onFont = { viewModel.onFontSelected(it) },
        onFontSize = { viewModel.onFontSize(it) },
        onLineHeight = { viewModel.onLineHeight(it) },
        onMargin = { viewModel.onMargin(it) },
        onTopMargin = { viewModel.onTopMargin(it) },
        onBottomMargin = { viewModel.onBottomMargin(it) },
        onBoldText = { viewModel.onBoldText(it) },
        onUsePublisherStyles = { viewModel.onUsePublisherStyles(it) },
        onForceFont = { viewModel.onForceFont(it) },
        onStripColors = { viewModel.onStripColors(it) },
        onThaiWordBreak = { viewModel.onThaiWordBreak(it) },
        onPageAnimation = { viewModel.onPageAnimation(it) },
        onVisualReplacement = { viewModel.onVisualReplacement(it) },
        onDismiss = onDismiss,
        onOpenReplacements = onOpenReplacements,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ReaderSettingsSheetContent(
    prefs: KanomPreferences,
    onTheme: (ReaderTheme) -> Unit,
    onFont: (ReadingFont) -> Unit,
    onFontSize: (Int) -> Unit,
    onLineHeight: (Float) -> Unit,
    onMargin: (Int) -> Unit,
    onTopMargin: (Int) -> Unit = {},
    onBottomMargin: (Int) -> Unit = {},
    onBoldText: (Boolean) -> Unit = {},
    onUsePublisherStyles: (Boolean) -> Unit = {},
    onForceFont: (Boolean) -> Unit = {},
    onStripColors: (Boolean) -> Unit = {},
    onThaiWordBreak: (Boolean) -> Unit = {},
    onPageAnimation: (Boolean) -> Unit = {},
    onVisualReplacement: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onOpenReplacements: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // ── Theme ──────────────────────────────────────────────────────────
            SectionLabel("ธีม")
            Row {
                ReaderTheme.entries.forEach { theme ->
                    FilterChip(
                        selected = prefs.readerTheme == theme,
                        onClick = { onTheme(theme) },
                        label = { Text(theme.displayName()) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Font family ────────────────────────────────────────────────────
            SectionLabel("แบบอักษร")
            Row {
                ReadingFont.entries.forEach { font ->
                    FilterChip(
                        selected = prefs.readingFont == font,
                        onClick = { onFont(font) },
                        label = { Text(font.displayName()) },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Font size ──────────────────────────────────────────────────────
            SectionLabel("ขนาดตัวอักษร: ${prefs.fontSizeSp} sp")
            Slider(
                value = prefs.fontSizeSp.toFloat(),
                onValueChange = { onFontSize(it.toInt()) },
                valueRange = 12f..36f,
                steps = 23,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // ── Line height ────────────────────────────────────────────────────
            SectionLabel("ระยะห่างบรรทัด: ${"%.1f".format(prefs.lineHeight)}")
            Slider(
                value = prefs.lineHeight,
                onValueChange = { onLineHeight(it) },
                valueRange = 1.0f..2.5f,
                steps = 29,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            // ── Margin ─────────────────────────────────────────────────────────
            SectionLabel("ระยะขอบด้านข้าง: ${prefs.marginDp} dp")
            Slider(
                value = prefs.marginDp.toFloat(),
                onValueChange = { onMargin(it.toInt()) },
                valueRange = 0f..64f,
                steps = 63,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            SectionLabel("ระยะขอบด้านบน: ${prefs.topMarginDp} dp")
            Slider(value = prefs.topMarginDp.toFloat(), onValueChange = { onTopMargin(it.toInt()) }, valueRange = 0f..80f, steps = 79, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(8.dp))

            SectionLabel("ระยะขอบด้านล่าง: ${prefs.bottomMarginDp} dp")
            Slider(value = prefs.bottomMarginDp.toFloat(), onValueChange = { onBottomMargin(it.toInt()) }, valueRange = 0f..80f, steps = 79, modifier = Modifier.fillMaxWidth())

            Spacer(Modifier.height(16.dp))

            // ── Escape hatches ─────────────────────────────────────────────────
            SectionLabel("การตั้งค่าขั้นสูง")
            ToggleRow(
                label = "ใช้สไตล์ผู้จัดพิมพ์",
                checked = prefs.usePublisherStyles,
                onCheckedChange = onUsePublisherStyles,
            )
            ToggleRow(
                label = "บังคับแบบอักษร",
                checked = prefs.forceFont,
                onCheckedChange = onForceFont,
            )
            ToggleRow(
                label = "ตัวอักษรหนา",
                checked = prefs.boldText,
                onCheckedChange = onBoldText,
            )
            ToggleRow(
                label = "ไม่ใช้สีผู้จัดพิมพ์",
                checked = prefs.stripColors,
                onCheckedChange = onStripColors,
            )
            ToggleRow(
                label = "ตัดคำไทย (ZWSP)",
                checked = prefs.thaiWordBreak,
                onCheckedChange = onThaiWordBreak,
            )
            ToggleRow(
                label = "แอนิเมชันพลิกหน้า",
                checked = prefs.pageAnimation,
                onCheckedChange = onPageAnimation,
            )
            ToggleRow(
                label = "แทนที่ในหน้าอ่าน",
                checked = prefs.visualReplacement,
                onCheckedChange = onVisualReplacement,
            )

            Spacer(Modifier.height(16.dp))

            // ── Replacements ───────────────────────────────────────────────────
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onDismiss(); onOpenReplacements() }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.reader_manage_replacements), Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun ReaderTheme.displayName(): String = when (this) {
    ReaderTheme.LIGHT -> "สว่าง"
    ReaderTheme.SEPIA -> "เซเปีย"
    ReaderTheme.DARK -> "มืด"
    ReaderTheme.TRUE_BLACK -> "ดำ"
}

private fun ReadingFont.displayName(): String = when (this) {
    ReadingFont.SARABUN -> "สระบุรี"
    ReadingFont.SERIF -> "Serif"
    ReadingFont.SYSTEM -> "ระบบ"
}

// ─── Preview ──────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Preview(name = "Settings sheet — defaults", showBackground = true)
@Composable
private fun ReaderSettingsSheetPreview() {
    KanomTheme {
        ReaderSettingsSheetContent(
            prefs = KanomPreferences(),
            onTheme = {},
            onFont = {},
            onFontSize = {},
            onLineHeight = {},
            onMargin = {},
            onDismiss = {},
        )
    }
}
