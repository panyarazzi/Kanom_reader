package com.nextz.kanomtts.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModel
import com.nextz.kanomtts.feature.settings.PortableDataViewModel
import com.nextz.kanomtts.feature.settings.VoiceLanguageEditor
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.text.pipeline.Lang

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenReplacements: () -> Unit = {},
    playbackVm: PlaybackSettingsViewModel = hiltViewModel(),
    portableVm: PortableDataViewModel = hiltViewModel(),
) {
    val prefs by playbackVm.uiState.collectAsState()
    val portable by portableVm.uiState.collectAsState()
    val context = LocalContext.current
    var lang by remember { mutableStateOf(Lang.TH) }
    var confirmRestore by remember { mutableStateOf(false) }

    val storageSettingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { portableVm.refresh() }
    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { portableVm.refresh() }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Text("เสียงพากย์", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
        Row {
            Lang.entries.forEach { l ->
                FilterChip(
                    selected = lang == l,
                    onClick = { lang = l },
                    label = { Text(PlaybackFormatting.langLabel(l)) },
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        VoiceLanguageEditor(lang = lang)

        HorizontalDivider(Modifier.padding(vertical = 16.dp))

        Text("การเล่น", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp))
        ToggleRow("ข้ามลิงก์ (URL)", prefs.skipUrls, playbackVm::setSkipUrls)
        ToggleRow("เลื่อนตามอัตโนมัติ", prefs.autoScroll, playbackVm::setAutoScroll)
        ToggleRow("ไฮไลต์ประโยคที่กำลังอ่าน", prefs.highlightOn, playbackVm::setHighlightOn)
        ToggleRow(
            "อ่านทีละย่อหน้า",
            prefs.readingUnit == ReadingUnit.PARAGRAPH,
            playbackVm::setReadingByParagraph,
            subtitle = "เล่นต่อเนื่องทั้งย่อหน้า — ไฮไลต์และปุ่มก่อนหน้า/ถัดไปจะทำงานทีละย่อหน้า (มีผลรอบเล่นถัดไป)",
        )
        Text("ตั้งเวลานอนเริ่มต้น: ${prefs.sleepTimerDefaultMin} นาที",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(
            value = prefs.sleepTimerDefaultMin.toFloat(),
            onValueChange = { playbackVm.setSleepTimerDefaultMin(it.toInt()) },
            valueRange = 5f..120f, steps = 22, modifier = Modifier.fillMaxWidth(),
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpenReplacements).padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.settings_replacements), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("ข้อมูลถาวรของ Kanom", style = MaterialTheme.typography.titleLarge)
        Text(
            "สำรองและกู้คืนข้อมูลผ่านโฟลเดอร์ Internal Storage/Kanom เพื่อเก็บข้อมูลไว้เมื่อติดตั้งแอปใหม่ หากไม่คัดลอก EPUB ต้องคงไฟล์ต้นฉบับและอาจต้องเลือกไฟล์ใหม่หลังติดตั้งแอป",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
        )
        Text(
            if (portable.hasAccess) "สถานะ: อนุญาตเข้าถึงแล้ว" else "สถานะ: ยังไม่ได้อนุญาตเข้าถึงไฟล์ทั้งหมด",
            style = MaterialTheme.typography.bodyMedium,
        )
        Text("ตำแหน่ง: Internal Storage/Kanom", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (portable.hasAccess) {
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Text("คัดลอกไฟล์ EPUB ไปไว้ใน Kanom/books", style = MaterialTheme.typography.bodyMedium)
                    Text("ปิดไว้โดยค่าเริ่มต้น — เปิดเฉพาะเมื่อต้องการสำรองไฟล์หนังสือด้วย จะใช้พื้นที่เพิ่มตามขนาดหนังสือทั้งหมด",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(
                    checked = portable.includeEpubFiles,
                    onCheckedChange = portableVm::setIncludeEpubFiles,
                    enabled = !portable.busy,
                )
            }
        }
        if (!portable.hasAccess) {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        runCatching { storageSettingsLauncher.launch(intent) }.onFailure {
                            storageSettingsLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    } else {
                        legacyPermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) { Text("อนุญาตให้ Kanom เข้าถึงไฟล์") }
        } else {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = portableVm::createBackup,
                    enabled = !portable.busy,
                    modifier = Modifier.weight(1f),
                ) { Text(if (portable.busy) "กำลังทำงาน…" else "สำรองข้อมูล") }
                OutlinedButton(
                    onClick = { confirmRestore = true },
                    enabled = !portable.busy && portable.backupExists,
                    modifier = Modifier.weight(1f),
                ) { Text("กู้คืนข้อมูล") }
            }
        }
        if (portable.message != null) {
            Text(portable.message!!, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            "ไฟล์สำรองล่าสุด: Kanom/backup/kanom-latest.zip",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        HorizontalDivider(Modifier.padding(vertical = 16.dp))
        Text("Neural · พรีเมียม — เร็วๆ นี้",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("กู้คืนข้อมูลจากไฟล์สำรอง?") },
            text = { Text("ข้อมูลหนังสือ ตำแหน่งอ่าน Bookmark กฎแทนที่ เสียงพากย์ และค่าตั้งปัจจุบันจะถูกแทนที่ด้วยข้อมูลในไฟล์สำรอง Kanom ล่าสุด ควรสำรองข้อมูลปัจจุบันก่อนดำเนินการ") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; portableVm.restoreBackup() }) { Text("กู้คืน") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("ยกเลิก") }
            },
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
