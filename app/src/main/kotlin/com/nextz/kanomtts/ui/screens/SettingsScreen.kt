package com.nextz.kanomtts.ui.screens

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.R
import com.nextz.kanomtts.data.prefs.ReadingUnit
import com.nextz.kanomtts.feature.settings.PlaybackSettingsViewModel
import com.nextz.kanomtts.feature.settings.VoiceLanguageEditor
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.text.pipeline.Lang

@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    onOpenReplacements: () -> Unit = {},
    playbackVm: PlaybackSettingsViewModel = hiltViewModel(),
) {
    val prefs by playbackVm.uiState.collectAsState()
    var lang by remember { mutableStateOf(Lang.TH) }

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

        Text("Neural · พรีเมียม — เร็วๆ นี้",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
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
