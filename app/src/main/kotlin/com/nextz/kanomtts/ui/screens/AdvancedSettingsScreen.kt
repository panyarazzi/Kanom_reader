package com.nextz.kanomtts.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModel

@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    onOpenReplacements: () -> Unit,
    onOpenBackupRestore: () -> Unit = {},
    onOpenAnnotations: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val prefs by viewModel.uiState.collectAsState()
    Column(
        Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "กลับ")
            }
            Text("การตั้งค่าขั้นสูง", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.fillMaxWidth().widthIn(max = 520.dp).weight(1f)
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Progress bar", style = MaterialTheme.typography.titleMedium)
            Text("กำหนดตำแหน่งและข้อมูลที่แสดงระหว่างอ่าน ทั้งแถบบนและแถบล่างใช้ขนาดตัวอักษรร่วมกัน", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleRow("แสดง Progress ด้านบน", prefs.topProgressEnabled, viewModel::setTopProgressEnabled)
            ToggleRow("แสดง Progress ด้านล่าง", prefs.footerProgressEnabled, viewModel::setFooterProgressEnabled)
            ValueSlider("ขนาดตัวอักษร Progress: ${prefs.progressFontSizeSp} sp",
                prefs.progressFontSizeSp.toFloat(), 8f..80f, 71) { viewModel.setProgressFontSizeSp(it.toInt()) }

            Text("รูปแบบ Progress ด้านบน", style = MaterialTheme.typography.titleSmall)
            LayoutChoice("ซ้าย", prefs.topProgressLeft, listOf("CHAPTER_PAGE" to "หน้า/บท", "BOOK_PERCENT" to "% ทั้งเล่ม", "NONE" to "ไม่แสดง")) {
                viewModel.setTopProgressLayout(it, prefs.topProgressCenter, prefs.topProgressRight)
            }
            LayoutChoice("กลาง", prefs.topProgressCenter, centerOptions) {
                viewModel.setTopProgressLayout(prefs.topProgressLeft, it, prefs.topProgressRight)
            }
            LayoutChoice("ขวา", prefs.topProgressRight, rightOptions) {
                viewModel.setTopProgressLayout(prefs.topProgressLeft, prefs.topProgressCenter, it)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("รูปแบบ Progress ด้านล่าง", style = MaterialTheme.typography.titleSmall)
            LayoutChoice("ซ้าย", prefs.footerProgressLeft, listOf("CHAPTER_PAGE" to "หน้า/บท", "BOOK_PERCENT" to "% ทั้งเล่ม", "NONE" to "ไม่แสดง")) {
                viewModel.setFooterProgressLayout(it, prefs.footerProgressCenter, prefs.footerProgressRight)
            }
            LayoutChoice("กลาง", prefs.footerProgressCenter, centerOptions) {
                viewModel.setFooterProgressLayout(prefs.footerProgressLeft, it, prefs.footerProgressRight)
            }
            LayoutChoice("ขวา", prefs.footerProgressRight, rightOptions) {
                viewModel.setFooterProgressLayout(prefs.footerProgressLeft, prefs.footerProgressCenter, it)
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Font / Typography และระยะย่อหน้า", style = MaterialTheme.typography.titleMedium)
            Text("ปรับระยะบรรทัด การเยื้องบรรทัดแรก และช่องว่างระหว่างย่อหน้าแยกจากขนาดตัวอักษรหลัก", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ValueSlider("ระยะห่างบรรทัด: ${"%.2f".format(prefs.lineHeight)}",
                prefs.lineHeight, 0.7f..2.5f, 17) { viewModel.setLineHeight(it) }
            ValueSlider("ย่อหน้าบรรทัดแรก: ${"%.2f".format(prefs.paragraphIndentEm)} em",
                prefs.paragraphIndentEm, 0f..3.5f, 34) { viewModel.setParagraphIndentEm(it) }
            ValueSlider("ระยะห่างระหว่างย่อหน้า: ${"%.2f".format(prefs.paragraphSpacingEm)} em",
                prefs.paragraphSpacingEm, 0f..2f, 19) { viewModel.setParagraphSpacingEm(it) }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("ระยะขอบหน้าอ่าน", style = MaterialTheme.typography.titleMedium)
            Text("ระยะขอบด้านบนและด้านล่างปรับได้สูงสุด 80 dp เพื่อรองรับหน้าจอ E Ink และพื้นที่ Progress", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ValueSlider("ด้านข้าง: ${prefs.marginDp} dp", prefs.marginDp.toFloat(), 0f..64f, 63) {
                viewModel.setMarginDp(it.toInt())
            }
            ValueSlider("ด้านบน: ${prefs.topMarginDp} dp", prefs.topMarginDp.toFloat(), 0f..80f, 79) {
                viewModel.setTopMarginDp(it.toInt())
            }
            ValueSlider("ด้านล่าง: ${prefs.bottomMarginDp} dp", prefs.bottomMarginDp.toFloat(), 0f..80f, 79) {
                viewModel.setBottomMarginDp(it.toInt())
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Reading & Navigation", style = MaterialTheme.typography.titleMedium)
            Text(if (prefs.tapAnywhereToNext) "เปิดอยู่: แตะพื้นที่ใดก็ไปหน้าถัดไป ยกเว้นพื้นที่กลางจอที่ยังเปิดเมนูอ่าน" else "ปิดอยู่: แตะซ้ายเพื่อย้อนหน้า แตะกลางเพื่อเปิดเมนู และแตะขวาเพื่อไปหน้าถัดไป", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ToggleRow("แตะพื้นที่อ่านเพื่อไปหน้าถัดไป (เว้นกลางจอ)", prefs.tapAnywhereToNext, viewModel::setTapAnywhereToNext)
            ToggleRow("สลับปุ่ม Volume", prefs.reverseVolumeKeys, viewModel::setReverseVolumeKeys)
            ToggleRow("ใช้สไตล์ผู้จัดพิมพ์", prefs.usePublisherStyles, viewModel::setUsePublisherStyles)
            ToggleRow("บังคับแบบอักษร", prefs.forceFont, viewModel::setForceFont)
            ToggleRow("ไม่ใช้สีผู้จัดพิมพ์", prefs.stripColors, viewModel::setStripColors)
            ToggleRow("ตัดคำไทย (ZWSP)", prefs.thaiWordBreak, viewModel::setThaiWordBreak)
            ToggleRow("แอนิเมชันพลิกหน้า", prefs.pageAnimation, viewModel::setPageAnimation)
            ToggleRow("แทนที่ข้อความในหน้าอ่าน", prefs.visualReplacement, viewModel::setVisualReplacement)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("จัดการกฎแทนที่ข้อความแบบส่วนกลางหรือแยกตามหนังสือ รวมถึงนำเข้าและส่งออกกฎได้", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenReplacements).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("จัดการกฎแทนที่ข้อความ", modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("ข้อมูลหนังสือและการสำรอง", style = MaterialTheme.typography.titleMedium)
            Text("จัดการ Tags / Notes และส่งออก CSV จากคลังหนังสือ รวมถึงสำรองและกู้คืนข้อมูลโดยไม่คัดลอกไฟล์ EPUB", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenAnnotations).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tags / Notes และส่งออก CSV", modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onOpenBackupRestore).padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Backup / Restore", modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("สารบัญและค้นหาหัวข้อ", style = MaterialTheme.typography.titleMedium)
            Text("เปิดสารบัญจากแถบ Progress ในหน้าอ่าน แล้วค้นหาหัวข้อได้ทุกระดับของสารบัญ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
        }
    }
}

private val centerOptions = listOf(
    "CHAPTER_TITLE" to "ชื่อบท",
    "BOOK_TITLE" to "ชื่อหนังสือ",
    "CHAPTER_PAGE" to "หน้า/บท",
    "BOOK_PERCENT" to "% ทั้งเล่ม",
    "NONE" to "ไม่แสดง",
)
private val rightOptions = listOf(
    "TIME_BATTERY" to "เวลา+แบตฯ",
    "TIME" to "เวลา",
    "BATTERY" to "แบตเตอรี่",
    "CHARGING" to "สถานะชาร์จ",
    "BOOK_PERCENT" to "% ทั้งเล่ม",
    "NONE" to "ไม่แสดง",
)

@Composable
private fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun LayoutChoice(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.first == selected }?.second ?: "ไม่แสดง"
    Box(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, text) ->
                DropdownMenuItem(text = { Text(text) }, onClick = { onSelect(value); expanded = false })
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
