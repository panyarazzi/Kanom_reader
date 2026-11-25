package com.nextz.kanomtts.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
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
        onTopProgressEnabled = { viewModel.onTopProgressEnabled(it) },
        onFooterProgressEnabled = { viewModel.onFooterProgressEnabled(it) },
        onTopProgressLayout = { l, m, r -> viewModel.onTopProgressLayout(l, m, r) },
        onFooterProgressLayout = { l, m, r -> viewModel.onFooterProgressLayout(l, m, r) },
        onLineHeight = { viewModel.onLineHeight(it) },
        onParagraphIndent = { viewModel.onParagraphIndent(it) },
        onParagraphSpacing = { viewModel.onParagraphSpacing(it) },
        onMargin = { viewModel.onMargin(it) },
        onTopMargin = { viewModel.onTopMargin(it) },
        onBottomMargin = { viewModel.onBottomMargin(it) },
        onBoldText = { viewModel.onBoldText(it) },
        onReverseVolumeKeys = { viewModel.onReverseVolumeKeys(it) },
        onTapAnywhereToNext = { viewModel.onTapAnywhereToNext(it) },
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
    onTopProgressEnabled: (Boolean) -> Unit = {},
    onFooterProgressEnabled: (Boolean) -> Unit = {},
    onTopProgressLayout: (String, String, String) -> Unit = { _, _, _ -> },
    onFooterProgressLayout: (String, String, String) -> Unit = { _, _, _ -> },
    onLineHeight: (Float) -> Unit,
    onParagraphIndent: (Float) -> Unit = {},
    onParagraphSpacing: (Float) -> Unit = {},
    onMargin: (Int) -> Unit,
    onTopMargin: (Int) -> Unit = {},
    onBottomMargin: (Int) -> Unit = {},
    onBoldText: (Boolean) -> Unit = {},
    onReverseVolumeKeys: (Boolean) -> Unit = {},
    onTapAnywhereToNext: (Boolean) -> Unit = {},
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
    var advancedMenuOpen by remember { mutableStateOf(false) }
    BackHandler(enabled = advancedMenuOpen) { advancedMenuOpen = false }
    val settingsBody: @Composable () -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                // Keep the controls visible during live preview so the changing numeric value remains readable.
        ) {
            if (!advancedMenuOpen) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
                        .clickable { advancedMenuOpen = true }
                        .padding(horizontal = 14.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "การตั้งค่าขั้นสูง",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "เปิดการตั้งค่าขั้นสูง")
                }

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

            // ── Line height ────────────────────────────────────────────────────
            SectionLabel("ระยะห่างบรรทัด: ${String.format(java.util.Locale.US, "%.2f", prefs.lineHeight)}")
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

            SectionLabel("ย่อหน้าบรรทัดแรก: ${String.format(java.util.Locale.US, "%.2f", prefs.paragraphIndentEm)} em")
            Slider(
                value = prefs.paragraphIndentEm,
                onValueChange = { previewing = true; onParagraphIndent(it) },
                onValueChangeFinished = { previewing = false },
                valueRange = 0f..3.5f,
                steps = 34, // 0.1 increments
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(8.dp))

            SectionLabel("ระยะห่างระหว่างย่อหน้า: ${String.format(java.util.Locale.US, "%.2f", prefs.paragraphSpacingEm)} em")
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



            // ── Escape hatches ─────────────────────────────────────────────────
            InfoToggleRow(label = "ใช้สไตล์ผู้จัดพิมพ์", checked = prefs.usePublisherStyles, onCheckedChange = onUsePublisherStyles, explanation = "เปิด: ให้แอปใช้รูปแบบที่ผู้ทำหนังสือกำหนดมา เช่น รูปแบบตัวอักษร ระยะห่าง และการจัดหน้า\nปิด: ให้แอปใช้รูปแบบการอ่านที่คุณเลือกใน Kanom Reader แทน")
            InfoToggleRow(label = "บังคับแบบอักษร", checked = prefs.forceFont, onCheckedChange = onForceFont, explanation = "เปิด: ใช้แบบอักษรที่เลือกใน Kanom Reader แทนแบบอักษรที่ฝังมากับหนังสือ\nปิด: อนุญาตให้หนังสือใช้แบบอักษรของตัวเองตามปกติ")
            InfoToggleRow(label = "ตัวอักษรหนา", checked = prefs.boldText, onCheckedChange = onBoldText, explanation = "เปิด: ทำให้ตัวอักษรในเนื้อหาหนังสือหนาขึ้นทั่วทั้งหน้า\nปิด: กลับไปใช้ความหนาตัวอักษรตามรูปแบบเดิมของหนังสือและแบบอักษร")
            InfoToggleRow(label = "แตะจอเพื่อหน้าถัดไป", checked = prefs.tapAnywhereToNext, onCheckedChange = onTapAnywhereToNext, explanation = "เปิด: แตะบริเวณหน้าจอที่ไม่ได้ใช้คำสั่งอื่นเพื่อไปหน้าถัดไปได้\nปิด: คงการแตะตามโซนเดิม โดยบริเวณกลางหน้าจอใช้เปิดเมนูอ่าน")
            InfoToggleRow(label = "สลับปุ่ม Volume", checked = prefs.reverseVolumeKeys, onCheckedChange = onReverseVolumeKeys, explanation = "เปิด: สลับหน้าที่ปุ่มเพิ่ม/ลดเสียงสำหรับเปลี่ยนหน้า\nปิด: ปุ่มเพิ่มเสียงไปหน้าถัดไป และปุ่มลดเสียงย้อนกลับ")
            InfoToggleRow(label = "ไม่ใช้สีผู้จัดพิมพ์", checked = prefs.stripColors, onCheckedChange = onStripColors, explanation = "เปิด: ไม่ใช้สีที่ผู้จัดพิมพ์กำหนดในหนังสือ เพื่อให้อ่านด้วยสีตามธีมของแอปได้สม่ำเสมอ\nปิด: แสดงสีตามที่หนังสือกำหนดไว้")
            InfoToggleRow(label = "ตัดคำไทย (ZWSP)", checked = prefs.thaiWordBreak, onCheckedChange = onThaiWordBreak, explanation = "เปิด: เพิ่มจุดแบ่งคำที่มองไม่เห็นในข้อความภาษาไทย ช่วยให้ตัดบรรทัดได้เหมาะสมขึ้น\nปิด: แสดงข้อความตามต้นฉบับโดยไม่เพิ่มจุดแบ่งคำ")
            InfoToggleRow(label = "แอนิเมชันพลิกหน้า", checked = prefs.pageAnimation, onCheckedChange = onPageAnimation, explanation = "เปิด: แสดงการเคลื่อนไหวเมื่อเปลี่ยนหน้า\nปิด: เปลี่ยนหน้าโดยไม่ใช้ภาพเคลื่อนไหว")
            InfoToggleRow(label = "แทนที่ในหน้าอ่าน", checked = prefs.visualReplacement, onCheckedChange = onVisualReplacement, explanation = "เปิด: ใช้กฎแทนข้อความที่ตั้งค่าไว้กับเนื้อหาหนังสือเมื่อเปิดหนังสือครั้งถัดไป\nปิด: ไม่ใช้กฎแทนข้อความกับหน้าอ่าน")

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

            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = { advancedMenuOpen = false }) { Text("‹ กลับ") }
                    Text("การตั้งค่าขั้นสูง", style = MaterialTheme.typography.titleMedium)
                }
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
            InfoToggleRow(label = "ตัวแสดงสถานะด้านบน", checked = prefs.topProgressEnabled, onCheckedChange = onTopProgressEnabled, explanation = "เปิด: แสดงข้อมูลความคืบหน้าการอ่านไว้ที่ขอบด้านบนของหน้าหนังสือ ตามรูปแบบซ้าย กลาง และขวาที่เลือกไว้\nปิด: ซ่อนแถบความคืบหน้าด้านบน โดยไม่กระทบแถบด้านล่าง")
            InfoToggleRow(label = "ตัวแสดงสถานะด้านล่าง", checked = prefs.footerProgressEnabled, onCheckedChange = onFooterProgressEnabled, explanation = "เปิด: แสดงข้อมูลความคืบหน้าการอ่านไว้ที่ขอบด้านล่างของหน้าหนังสือ ตามรูปแบบซ้าย กลาง และขวาที่เลือกไว้\nปิด: ซ่อนแถบความคืบหน้าด้านล่าง โดยไม่กระทบแถบด้านบน")

            Spacer(Modifier.height(12.dp))
            ProgressLayoutSection(
                title = "รูปแบบ Progress ด้านบน",
                layout = ProgressLayout(prefs.topProgressLeft, prefs.topProgressCenter, prefs.topProgressRight),
                onChange = onTopProgressLayout,
            )
            HorizontalDivider(Modifier.padding(vertical = 16.dp))
            ProgressLayoutSection(
                title = "รูปแบบ Progress ด้านล่าง",
                layout = ProgressLayout(prefs.footerProgressLeft, prefs.footerProgressCenter, prefs.footerProgressRight),
                onChange = onFooterProgressLayout,
            )
            Spacer(Modifier.height(16.dp))

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


            }
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Capture the constraints-scope value before entering nested layout scopes.
        // Give large e-reader settings panels ~12.5% more width so slider tracks
        // are easier to manipulate without changing compact phone layouts.
        val panelWidth = (maxWidth / 3 * 1.125f).coerceIn(315.dp, 520.dp)
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

private val progressLayoutOptions = listOf(
    "CHAPTER_PAGE" to "หน้า/บท",
    "CHAPTER_TITLE" to "ชื่อบท",
    "BOOK_TITLE" to "ชื่อหนังสือ",
    "BOOK_PERCENT" to "% ทั้งเล่ม",
    "TIME_BATTERY" to "เวลา+แบตฯ",
    "TIME" to "เวลา",
    "BATTERY" to "แบตเตอรี่",
    "CHARGING" to "สถานะชาร์จ",
    "NONE" to "ไม่แสดง",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressLayoutSection(
    title: String,
    layout: ProgressLayout,
    onChange: (String, String, String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        ProgressChoiceRow("ซ้าย", layout.left, progressLayoutOptions) {
            onChange(it, layout.center, layout.right)
        }
        Spacer(Modifier.height(8.dp))
        ProgressChoiceRow("กลาง", layout.center, progressLayoutOptions) {
            onChange(layout.left, it, layout.right)
        }
        Spacer(Modifier.height(8.dp))
        ProgressChoiceRow("ขวา", layout.right, progressLayoutOptions) {
            onChange(layout.left, layout.center, it)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProgressChoiceRow(
    label: String,
    selected: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "ไม่แสดง"
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = it },
            modifier = Modifier.weight(1f),
        ) {
            OutlinedTextField(
                value = selectedLabel,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                modifier = Modifier.menuAnchor().fillMaxWidth(),
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (value, text) ->
                    DropdownMenuItem(
                        text = { Text(text) },
                        onClick = { onSelect(value); expanded = false },
                    )
                }
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
private fun InfoToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    explanation: String,
) {
    var infoOpen by remember(label) { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyMedium)
            Text("ⓘ", color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 8.dp).clickable { infoOpen = true })
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
    if (infoOpen) {
        Popup(
            popupPositionProvider = remember {
                object : PopupPositionProvider {
                    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize,
                        layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
                        val margin = 12
                        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
                        val x = (anchorBounds.left + anchorBounds.width / 2 - popupContentSize.width / 2).coerceIn(margin, maxX)
                        val below = anchorBounds.bottom + 8
                        val y = if (below + popupContentSize.height <= windowSize.height - margin) below
                            else (anchorBounds.top - popupContentSize.height - 8).coerceAtLeast(margin)
                        return IntOffset(x, y)
                    }
                }
            },
            onDismissRequest = { infoOpen = false },
            properties = PopupProperties(focusable = true, dismissOnClickOutside = true),
        ) {
            Surface(
                modifier = Modifier.width(300.dp).padding(4.dp),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp,
                shadowElevation = 10.dp,
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        TextButton(onClick = { infoOpen = false }) { Text("X") }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(explanation, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = { infoOpen = false }, modifier = Modifier.align(Alignment.End)) { Text("ปิด") }
                }
            }
        }
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
