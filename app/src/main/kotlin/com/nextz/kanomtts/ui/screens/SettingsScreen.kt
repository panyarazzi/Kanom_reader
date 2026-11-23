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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.data.prefs.ReadingFont
import com.nextz.kanomtts.ui.theme.ReaderTheme
import com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModel
import com.nextz.kanomtts.feature.settings.PortableDataViewModel
import com.nextz.kanomtts.feature.settings.RestoreReconciliationStage
import com.nextz.kanomtts.feature.settings.VoiceLanguageEditor
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.text.pipeline.Lang

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenReplacements: () -> Unit = {},
    onOpenAdvanced: () -> Unit = {},
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

    val restoreFolderLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri == null) {
            portableVm.cancelRestore()
        } else {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            portableVm.scanRestoreFolder(treeUri)
        }
    }

    LaunchedEffect(portable.restoreStage) {
        if (portable.restoreStage == RestoreReconciliationStage.CHOOSE_FOLDER) {
            restoreFolderLauncher.launch(null)
        }
    }

    Box(
        modifier.fillMaxSize().padding(contentPadding),
        contentAlignment = Alignment.TopCenter,
    ) {
    Column(Modifier.fillMaxWidth().widthIn(max = 520.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("ตั้งค่า", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        ) {
        Text("การอ่าน", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReaderTheme.entries.forEach { theme ->
                FilterChip(
                    selected = prefs.readerTheme == theme,
                    onClick = { playbackVm.setReaderTheme(theme) },
                    label = { Text(when (theme) { ReaderTheme.LIGHT -> "สว่าง"; ReaderTheme.SEPIA -> "เซเปีย"; ReaderTheme.DARK -> "มืด"; ReaderTheme.TRUE_BLACK -> "ดำ" }) },
                )
            }
        }
        Text("แบบอักษร", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReadingFont.entries.forEach { font ->
                FilterChip(
                    selected = prefs.readingFont == font,
                    onClick = { playbackVm.setReadingFont(font) },
                    label = { Text(when (font) { ReadingFont.SARABUN -> "Sarabun"; ReadingFont.SERIF -> "Serif"; ReadingFont.SYSTEM -> "ระบบ" }) },
                )
            }
        }
        Text("ขนาดตัวอักษร: ${prefs.fontSizeSp} sp", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
        Slider(value = prefs.fontSizeSp.toFloat(), onValueChange = { playbackVm.setFontSizeSp(it.toInt()) },
            valueRange = 12f..72f, steps = 59)
        ToggleRow("ตัวอักษรหนา", prefs.boldText, playbackVm::setBoldText)
        ToggleRow("แตะพื้นที่อ่านเพื่อไปหน้าถัดไป (เว้นกลางจอ)", prefs.tapAnywhereToNext, playbackVm::setTapAnywhereToNext)
        Card(Modifier.fillMaxWidth().clickable(onClick = onOpenAdvanced)) {
            Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("การตั้งค่าขั้นสูง", style = MaterialTheme.typography.titleMedium)
                    Text("Progress, ระยะบรรทัด, ระยะขอบ และตัวเลือกเพิ่มเติม", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))

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
        IntegerValueField("ตั้งเวลานอนเริ่มต้น (5–120 นาที)", prefs.sleepTimerDefaultMin.toString()) {
            it.toIntOrNull()?.let { value -> playbackVm.setSleepTimerDefaultMin(value.coerceIn(5, 120)) }
        }

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
            "สำรองและกู้คืนข้อมูลผ่านโฟลเดอร์ Internal Storage/Kanom ระบบจะไม่คัดลอกไฟล์ EPUB จึงควรเก็บไฟล์ต้นฉบับไว้ และอาจต้องเชื่อมไฟล์ใหม่หลังติดตั้งแอปหรือย้ายอุปกรณ์",
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
        Text(
            "ระบบสำรองเฉพาะข้อมูลหนังสือ ตำแหน่งอ่าน Bookmark ปก Tags Notes สถิติการอ่าน และการตั้งค่าเท่านั้น จะไม่มีการคัดลอกไฟล์ EPUB ไม่ว่ากรณีใด",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
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
    }
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("กู้คืนข้อมูลจากไฟล์สำรอง?") },
            text = { Text("ก่อนเปลี่ยนข้อมูลปัจจุบัน ระบบจะให้เลือกโฟลเดอร์ EPUB ต้นฉบับ ตรวจสอบรายการที่จับคู่ได้ และให้เลือกเองในกรณีที่พบหลายไฟล์ ข้อมูลเดิมจะยังไม่ถูกแทนที่จนกว่าจะยืนยันขั้นตอนสุดท้าย ควรสำรองข้อมูลปัจจุบันก่อนดำเนินการ") },
            confirmButton = {
                TextButton(onClick = { confirmRestore = false; portableVm.prepareRestore() }) { Text("ดำเนินการ") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("ยกเลิก") }
            },
        )
    }

    when (portable.restoreStage) {
        RestoreReconciliationStage.REVIEW_UNIQUE -> {
            val plan = portable.restorePlan
            AlertDialog(
                onDismissRequest = portableVm::cancelRestore,
                title = { Text("ตรวจสอบรายการ EPUB ที่จับคู่ได้") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text("พบรายการที่ตรงกันแน่นอน ${plan?.uniqueMatches?.size ?: 0} เล่ม")
                        plan?.uniqueMatches?.forEach { (book, candidate) ->
                            Text("${book.title}  →  ${candidate.name}", modifier = Modifier.padding(top = 8.dp))
                            Text(candidate.sourceFolder.orEmpty(), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text("รายการที่ต้องเลือกเอง: ${plan?.ambiguousMatches?.size ?: 0} เล่ม")
                        Text("รายการที่ไม่พบ: ${plan?.notFound?.size ?: 0} เล่ม")
                        Text("ไฟล์ต้นฉบับจะไม่ถูกคัดลอกหรือย้าย", style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(onClick = portableVm::confirmUniqueMatches) { Text("ยืนยันรายการนี้") }
                },
                dismissButton = {
                    TextButton(onClick = portableVm::cancelRestore) { Text("ยกเลิก Restore") }
                },
            )
        }
        RestoreReconciliationStage.AMBIGUOUS -> {
            val item = portable.restorePlan?.ambiguousMatches?.getOrNull(portable.ambiguousIndex)
            AlertDialog(
                onDismissRequest = portableVm::cancelRestore,
                title = { Text("เลือกไฟล์ต้นฉบับ (${portable.ambiguousIndex + 1}/${portable.restorePlan?.ambiguousMatches?.size ?: 0})") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text(item?.book?.title.orEmpty(), style = MaterialTheme.typography.titleMedium)
                        item?.candidates?.forEach { candidate ->
                            val alreadyAssigned = portableVm.isCandidateAlreadyAssigned(candidate)
                            OutlinedButton(
                                onClick = { portableVm.chooseAmbiguousCandidate(candidate) },
                                enabled = !alreadyAssigned,
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            ) {
                                Column {
                                    Text(if (alreadyAssigned) "${candidate.name} · จับคู่แล้ว" else candidate.name)
                                    Text(candidate.sourceFolder.orEmpty(), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = portableVm::skipAmbiguousBook) { Text("ข้ามหนังสือเล่มนี้") }
                },
                dismissButton = {
                    TextButton(onClick = portableVm::cancelRestore) { Text("ยกเลิก Restore") }
                },
            )
        }
        RestoreReconciliationStage.NOT_FOUND -> {
            val missing = portable.restorePlan?.notFound.orEmpty()
            AlertDialog(
                onDismissRequest = portableVm::cancelRestore,
                title = { Text("หนังสือที่ไม่พบไฟล์ต้นฉบับ") },
                text = {
                    Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                        Text("หนังสือเหล่านี้จะกู้คืนข้อมูลและตำแหน่งอ่านไว้ แต่ยังเปิดอ่านไม่ได้จนกว่าจะ Relink ภายหลัง")
                        missing.forEach { book ->
                            Text("• ${book.title}", modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = portableVm::finishRestore, enabled = !portable.busy) { Text("กู้คืนต่อ") }
                },
                dismissButton = {
                    TextButton(onClick = portableVm::cancelRestore) { Text("ยกเลิก Restore") }
                },
            )
        }
        else -> Unit
    }

    if (portable.busy && portable.restoreStage != RestoreReconciliationStage.NONE) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("กำลังเตรียม Restore") },
            text = { Column { CircularProgressIndicator(); Spacer(Modifier.height(12.dp)); Text(portable.message.orEmpty()) } },
            confirmButton = {},
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

@Composable
private fun IntegerValueField(
    label: String,
    value: String,
    onCommit: (String) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var input by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) input = value }
    OutlinedTextField(
        value = input,
        onValueChange = { candidate -> if (candidate.matches(Regex("[0-9]*"))) input = candidate },
        modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            val wasFocused = focused
            focused = state.isFocused
            if (wasFocused && !state.isFocused) onCommit(input)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onCommit(input); focusManager.clearFocus() }),
    )
}

