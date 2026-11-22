package com.nextz.kanomtts.reader

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.runtime.LaunchedEffect
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
 * Adaptive reader settings: a right-side panel on wide reading surfaces and
 * a bottom sheet on compact phone layouts. Both share the same settings content.
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
        onProgressFontSize = { viewModel.onProgressFontSize(it) },
        onLineHeight = { viewModel.onLineHeight(it) },
        onParagraphIndent = { viewModel.onParagraphIndent(it) },
        onParagraphSpacing = { viewModel.onParagraphSpacing(it) },
        onMargin = { viewModel.onMargin(it) },
        onTopMargin = { viewModel.onTopMargin(it) },
        onBottomMargin = { viewModel.onBottomMargin(it) },
        onBoldText = { viewModel.onBoldText(it) },
        onReverseVolumeKeys = { viewModel.onReverseVolumeKeys(it) },
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
    onProgressFontSize: (Int) -> Unit = {},
    onLineHeight: (Float) -> Unit,
    onParagraphIndent: (Float) -> Unit = {},
    onParagraphSpacing: (Float) -> Unit = {},
    onMargin: (Int) -> Unit,
    onTopMargin: (Int) -> Unit = {},
    onBottomMargin: (Int) -> Unit = {},
    onBoldText: (Boolean) -> Unit = {},
    onReverseVolumeKeys: (Boolean) -> Unit = {},
    onUsePublisherStyles: (Boolean) -> Unit = {},
    onForceFont: (Boolean) -> Unit = {},
    onStripColors: (Boolean) -> Unit = {},
    onThaiWordBreak: (Boolean) -> Unit = {},
    onPageAnimation: (Boolean) -> Unit = {},
    onVisualReplacement: (Boolean) -> Unit = {},
    onDismiss: () -> Unit,
    onOpenReplacements: () -> Unit = {},
) {
    var previewing by remember { mutableStateOf(false) }
    val settingsBody: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                // Keep the controls visible during live preview so the changing numeric value remains readable.
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
            SectionLabel("ขนาดตัวอักษร: ${prefs.fontSizeSp} sp (${prefs.fontSizeSp * 100 / 16}%)")
            Slider(
                value = prefs.fontSizeSp.toFloat(),
                onValueChange = { previewing = true; onFontSize(it.toInt()) },
                onValueChangeFinished = { previewing = false },
                valueRange = 12f..72f,
                steps = 59,
                modifier = Modifier.fillMaxWidth(),
            )

            NumericSettingField("ขนาดตัวอักษร (sp)", prefs.fontSizeSp.toString(), decimal = false) {
                it.toIntOrNull()?.let { value -> onFontSize(value.coerceIn(12, 72)) }
            }

            Spacer(Modifier.height(8.dp))

            SectionLabel("ขนาดตัวอักษรแสดงสถานะบน/ล่าง: ${prefs.progressFontSizeSp} sp")
            Slider(
                value = prefs.progressFontSizeSp.toFloat(),
                onValueChange = { previewing = true; onProgressFontSize(it.toInt()) },
                onValueChangeFinished = { previewing = false },
                valueRange = 8f..80f,
                steps = 71,
                modifier = Modifier.fillMaxWidth(),
            )
            NumericSettingField("ขนาด Progress (8–80 sp)", prefs.progressFontSizeSp.toString(), decimal = false) {
                it.toIntOrNull()?.let { value -> onProgressFontSize(value.coerceIn(8, 80)) }
            }

            Spacer(Modifier.height(8.dp))

            // ── Line height ────────────────────────────────────────────────────
            SectionLabel("ระยะห่างบรรทัด: ${"%.1f".format(prefs.lineHeight)}")
            Slider(
                value = prefs.lineHeight,
                onValueChange = { previewing = true; onLineHeight(it) },
                onValueChangeFinished = { previewing = false },
                valueRange = 0.7f..2.5f,
                steps = 17, // 0.1 increments
                modifier = Modifier.fillMaxWidth(),
            )

            NumericSettingField("ระยะห่างบรรทัด (เช่น 1.08)", prefs.lineHeight.toString()) {
                it.toFloatOrNull()?.let(onLineHeight)
            }

            Spacer(Modifier.height(8.dp))

            SectionLabel("ย่อหน้าบรรทัดแรก: ${"%.1f".format(prefs.paragraphIndentEm)} em")
            Slider(
                value = prefs.paragraphIndentEm,
                onValueChange = { previewing = true; onParagraphIndent(it) },
                onValueChangeFinished = { previewing = false },
                valueRange = 0f..3.5f,
                steps = 34, // 0.1 increments
                modifier = Modifier.fillMaxWidth(),
            )

            NumericSettingField("ย่อหน้าบรรทัดแรก (em)", prefs.paragraphIndentEm.toString()) {
                it.toFloatOrNull()?.let(onParagraphIndent)
            }

            Spacer(Modifier.height(8.dp))

            SectionLabel("ระยะห่างระหว่างย่อหน้า: ${"%.1f".format(prefs.paragraphSpacingEm)} em")
            Slider(value = prefs.paragraphSpacingEm, onValueChange = { previewing = true; onParagraphSpacing(it) },
                onValueChangeFinished = { previewing = false }, valueRange = 0f..2f, steps = 19, modifier = Modifier.fillMaxWidth())

            NumericSettingField("ระยะห่างย่อหน้า (em)", prefs.paragraphSpacingEm.toString()) {
                it.toFloatOrNull()?.let(onParagraphSpacing)
            }

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

            NumericSettingField("ระยะขอบด้านข้าง (dp)", prefs.marginDp.toString(), decimal = false) {
                it.toIntOrNull()?.let { value -> onMargin(value.coerceIn(0, 64)) }
            }

            Spacer(Modifier.height(8.dp))

            SectionLabel("ระยะขอบด้านบน: ${prefs.topMarginDp} dp")
            Slider(value = prefs.topMarginDp.toFloat(), onValueChange = { previewing = true; onTopMargin(it.toInt()) }, onValueChangeFinished = { previewing = false }, valueRange = 0f..80f, steps = 79, modifier = Modifier.fillMaxWidth())

            NumericSettingField("ระยะขอบด้านบน (dp)", prefs.topMarginDp.toString(), decimal = false) {
                it.toIntOrNull()?.let { value -> onTopMargin(value.coerceIn(0, 80)) }
            }

            Spacer(Modifier.height(8.dp))

            SectionLabel("ระยะขอบด้านล่าง: ${prefs.bottomMarginDp} dp")
            Slider(value = prefs.bottomMarginDp.toFloat(), onValueChange = { previewing = true; onBottomMargin(it.toInt()) }, onValueChangeFinished = { previewing = false }, valueRange = 0f..80f, steps = 79, modifier = Modifier.fillMaxWidth())

            NumericSettingField("ระยะขอบด้านล่าง (dp)", prefs.bottomMarginDp.toString(), decimal = false) {
                it.toIntOrNull()?.let { value -> onBottomMargin(value.coerceIn(0, 80)) }
            }

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
                label = "สลับปุ่ม Volume",
                checked = prefs.reverseVolumeKeys,
                onCheckedChange = onReverseVolumeKeys,
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

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Capture the constraints-scope value before entering nested layout scopes.
        // Give large e-reader settings panels ~12.5% more width so slider tracks
        // are easier to manipulate without changing compact phone layouts.
        val panelWidth = (maxWidth / 3 * 1.125f).coerceIn(315.dp, 472.dp)
        if (maxWidth >= 600.dp) {
            // Wide reading surfaces use a persistent right-side settings panel.
            // The EPUB remains visible at full height in the remaining area.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (previewing) 0f else 0.28f))
                    .clickable(onClick = onDismiss),
            ) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(panelWidth)
                        .clickable(enabled = true, onClick = {}),
                    color = if (previewing) Color.Transparent else MaterialTheme.colorScheme.surface,
                    tonalElevation = if (previewing) 0.dp else 8.dp,
                    shadowElevation = 12.dp,
                ) {
                    settingsBody()
                }
            }
        } else {
            val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
            ModalBottomSheet(
                onDismissRequest = onDismiss,
                sheetState = sheetState,
                containerColor = if (previewing) Color.Transparent else MaterialTheme.colorScheme.surface,
                scrimColor = if (previewing) Color.Transparent else Color.Black.copy(alpha = 0.32f),
            ) {
                settingsBody()
            }
        }
    }
}

@Composable
private fun NumericSettingField(
    label: String,
    value: String,
    decimal: Boolean = true,
    onCommit: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var input by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) {
        if (!focused) input = value
    }
    OutlinedTextField(
        value = input,
        onValueChange = { candidate ->
            val valid = if (decimal) candidate.matches(Regex("[0-9]*(\\.[0-9]*)?")) else candidate.matches(Regex("[0-9]*"))
            if (valid) input = candidate
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            val wasFocused = focused
            focused = state.isFocused
            if (wasFocused && !state.isFocused) onCommit(input)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onCommit(input); focusManager.clearFocus() }),
    )
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
