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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    onBack: () -> Unit = {},
    viewModel: NowPlayingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var sheetLang by remember { mutableStateOf<Lang?>(null) }

    // This is a session card over the current destination, not a navigation destination.
    // Dismissing it must leave the Reader and its Readium navigator composed underneath.
    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = true,
        ),
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .heightIn(max = 720.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 16.dp,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("กำลังฟัง", style = MaterialTheme.typography.titleMedium)
                    // X #1: dismiss only the popup. Playback continues.
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "ปิดหน้าต่าง โดยเล่นต่อ")
                    }
                }

                if (!state.present) {
                    Box(
                        Modifier.fillMaxWidth().heightIn(min = 180.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "ยังไม่มีการเล่น",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    AsyncImage(
                        model = state.coverPath,
                        contentDescription = null,
                        modifier = Modifier.size(132.dp).clip(MaterialTheme.shapes.large),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(state.title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    state.author?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(Modifier.height(16.dp))
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
                            modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())

                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IconButton(onClick = viewModel::prev) {
                            Icon(Icons.Filled.SkipPrevious, contentDescription = "ก่อนหน้า")
                        }
                        FilledIconButton(viewModel::playPause, modifier = Modifier.size(56.dp)) {
                            Icon(
                                if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = if (state.isPlaying) "หยุดชั่วคราว" else "เล่น",
                            )
                        }
                        IconButton(onClick = viewModel::next) {
                            Icon(Icons.Filled.SkipNext, contentDescription = "ถัดไป")
                        }
                        // X #2: end this TTS session, then dismiss the card.
                        IconButton(onClick = { viewModel.stop(); onBack() }) {
                            Icon(Icons.Filled.Close, contentDescription = "ออกจาก TTS และจบเซสชัน")
                        }
                    }

                    Spacer(Modifier.height(8.dp))
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

                    Spacer(Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        AssistChip(
                            onClick = { viewModel.setSleep(if (state.sleepRemainingMs != null) null else 15) },
                            label = {
                                Text(
                                    state.sleepRemainingMs?.let { "💤 " + PlaybackFormatting.formatSleepRemaining(it) }
                                        ?: "💤 ตั้งเวลานอน",
                                )
                            },
                        )
                        state.currentLang?.let { lang ->
                            AssistChip(
                                onClick = { sheetLang = lang },
                                label = { Text("เสียง: " + PlaybackFormatting.langLabel(lang)) },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Lang.entries.forEach { lang ->
                            TextButton(onClick = { sheetLang = lang }) {
                                Text(PlaybackFormatting.langLabel(lang))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }

    sheetLang?.let { lang ->
        ModalBottomSheet(onDismissRequest = { sheetLang = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Text(
                    "ตั้งค่าเสียง: ${PlaybackFormatting.langLabel(lang)}",
                    style = MaterialTheme.typography.titleMedium,
                )
                VoiceLanguageEditor(lang = lang)
            }
        }
    }
}
