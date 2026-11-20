package com.nextz.kanomtts.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil3.compose.AsyncImage
import com.nextz.kanomtts.feature.settings.VoiceLanguageEditor
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.ui.NowPlayingViewModel
import com.nextz.kanomtts.text.pipeline.Lang
import com.nextz.kanomtts.ui.theme.KanomTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var sheetLang by remember { mutableStateOf<Lang?>(null) }

    if (!state.present) {
        Box(modifier.fillMaxSize().padding(contentPadding), contentAlignment = Alignment.Center) {
            Text(
                "ยังไม่มีการเล่น",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    Column(
        modifier
            .fillMaxSize()
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))
        AsyncImage(
            model = state.coverPath,
            contentDescription = null,
            modifier = Modifier.size(220.dp).clip(MaterialTheme.shapes.large),
        )
        Spacer(Modifier.height(16.dp))
        Text(state.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        state.author?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(20.dp))
        // Reserved rose: "this is being spoken"
        Surface(
            color = KanomTheme.colors.highlightContainer,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                state.sentenceText ?: "…",
                style = MaterialTheme.typography.titleMedium,
                color = KanomTheme.colors.onHighlight,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            IconButton(viewModel::prev) { Icon(Icons.Filled.SkipPrevious, "ก่อนหน้า") }
            FilledIconButton(viewModel::playPause, modifier = Modifier.size(64.dp)) {
                Icon(
                    if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (state.isPlaying) "หยุดชั่วคราว" else "เล่น",
                )
            }
            IconButton(viewModel::next) { Icon(Icons.Filled.SkipNext, "ถัดไป") }
            IconButton(viewModel::stop) { Icon(Icons.Filled.Stop, "หยุด") }
        }

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(onClick = { viewModel.nudgeSpeed(-0.1f) }, label = { Text("ช้าลง") })
            Text("ความเร็ว ${state.speedDisplay}", style = MaterialTheme.typography.labelMedium)
            AssistChip(onClick = { viewModel.nudgeSpeed(0.1f) }, label = { Text("เร็วขึ้น") })
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AssistChip(onClick = { viewModel.nudgePitch(-0.1f) }, label = { Text("ต่ำลง") })
            Text("ระดับเสียง ${state.pitchDisplay}", style = MaterialTheme.typography.labelMedium)
            AssistChip(onClick = { viewModel.nudgePitch(0.1f) }, label = { Text("สูงขึ้น") })
        }

        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { viewModel.setSleep(if (state.sleepRemainingMs != null) null else 15) },
                label = {
                    Text(
                        state.sleepRemainingMs?.let { "💤 " + PlaybackFormatting.formatSleepRemaining(it) }
                            ?: "💤 ตั้งเวลานอน",
                    )
                },
            )
            state.currentLang?.let { l ->
                AssistChip(
                    onClick = { sheetLang = l },
                    label = { Text("เสียง: " + PlaybackFormatting.langLabel(l)) },
                )
            }
        }
        Row {
            Lang.entries.forEach { l ->
                TextButton(onClick = { sheetLang = l }) { Text(PlaybackFormatting.langLabel(l)) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    sheetLang?.let { l ->
        ModalBottomSheet(onDismissRequest = { sheetLang = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(
                    "ตั้งค่าเสียง: ${PlaybackFormatting.langLabel(l)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                VoiceLanguageEditor(lang = l)
            }
        }
    }
}
