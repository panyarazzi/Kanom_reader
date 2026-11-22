package com.nextz.kanomtts.feature.settings

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalFocusManager
import androidx.hilt.navigation.compose.hiltViewModel
import com.nextz.kanomtts.playback.PlaybackFormatting
import com.nextz.kanomtts.playback.Voice
import com.nextz.kanomtts.text.pipeline.Lang

/** Hilt wrapper — load voices for [lang] on first composition, render the editor for it. */
@Composable
fun VoiceLanguageEditor(
    lang: Lang,
    modifier: Modifier = Modifier,
    viewModel: VoiceSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    LaunchedEffect(lang) { viewModel.loadVoices(lang) }
    val langState = state.languages.firstOrNull { it.lang == lang } ?: return
    VoiceLanguageEditor(
        state = langState,
        onSelectVoice = { viewModel.selectVoice(lang, it) },
        onSpeed = { viewModel.setSpeed(lang, it) },
        onPitch = { viewModel.setPitch(lang, it) },
        onPreview = { viewModel.preview(lang) },
        modifier = modifier,
    )
}

/** Stateless — voice picker + speed/pitch sliders + audition button. */
@Composable
fun VoiceLanguageEditor(
    state: VoiceLangState,
    onSelectVoice: (String?) -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("เสียง", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.voices.isEmpty()) {
            Text("ไม่พบเสียง — ติดตั้งเสียงในระบบ", style = MaterialTheme.typography.bodySmall)
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                state.voices.forEach { v ->
                    FilterChip(
                        selected = state.selectedVoiceId == v.id,
                        onClick = { onSelectVoice(v.id) },
                        label = { Text(v.name + if (v.needsNetwork) " ☁" else "") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text("ความเร็ว: ${PlaybackFormatting.formatMultiplier(state.speed)}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = state.speed, onValueChange = onSpeed,
            valueRange = PlaybackFormatting.SPEED_MIN..PlaybackFormatting.SPEED_MAX, modifier = Modifier.fillMaxWidth())
        DecimalValueField("ความเร็ว (เช่น 1.08)", state.speed.toString(), onSpeed)

        Text("ระดับเสียง: ${PlaybackFormatting.formatMultiplier(state.pitch)}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = state.pitch, onValueChange = onPitch,
            valueRange = PlaybackFormatting.PITCH_MIN..PlaybackFormatting.PITCH_MAX, modifier = Modifier.fillMaxWidth())
        DecimalValueField("ระดับเสียง", state.pitch.toString(), onPitch)

        Button(onClick = onPreview, modifier = Modifier.padding(top = 4.dp)) { Text("เล่นตัวอย่าง") }
    }
}

@Composable
private fun DecimalValueField(
    label: String,
    value: String,
    onCommit: (Float) -> Unit,
) {
    val focusManager = LocalFocusManager.current
    var input by remember(value) { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(value, focused) { if (!focused) input = value }
    OutlinedTextField(
        value = input,
        onValueChange = { candidate ->
            if (candidate.matches(Regex("[0-9]*(\\.[0-9]*)?"))) input = candidate
        },
        modifier = Modifier.fillMaxWidth().onFocusChanged { state ->
            val wasFocused = focused
            focused = state.isFocused
            if (wasFocused && !state.isFocused) input.toFloatOrNull()?.let(onCommit)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { input.toFloatOrNull()?.let(onCommit); focusManager.clearFocus() }),
    )
}

