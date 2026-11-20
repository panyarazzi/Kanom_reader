package com.nextz.kanomtts.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Self-managed audio focus for TTS playback.
 *
 * Uses a callback-based API rather than direct injection of [TtsPlaybackController] to
 * avoid a Hilt circular dependency (controller → AudioFocus → controller).
 *
 * Call [bind] once (from the controller's init) to wire the pause/play/stop callbacks.
 * The class then autonomously requests/abandons focus and delegates focus-change events
 * to those callbacks.
 *
 * Handles transient focus loss (pause), permanent loss (stop), re-gain (play),
 * and headset unplug (pause via [AudioManager.ACTION_AUDIO_BECOMING_NOISY]).
 *
 * Note: [SimpleBasePlayer] does NOT manage AudioManager focus automatically —
 * ExoPlayer's AudioFocusManager is not available to custom players.
 */
@Singleton
class AudioFocus @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val audioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    // Callbacks — set by bind(); safe null-checks ensure no crash if bind hasn't been called.
    private var onPause: (() -> Unit)? = null
    private var onPlay: (() -> Unit)? = null
    private var onStop: (() -> Unit)? = null

    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(audioAttributes)
        .setOnAudioFocusChangeListener { change ->
            when (change) {
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onPause?.invoke()
                AudioManager.AUDIOFOCUS_GAIN -> onPlay?.invoke()
                AudioManager.AUDIOFOCUS_LOSS -> onStop?.invoke()
            }
        }
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPause?.invoke()
            }
        }
    }

    private var focused = false
    private var noisyReceiverRegistered = false

    /**
     * Wire the controller's pause/play/stop functions as focus-change callbacks.
     *
     * Must be called once from the controller's init (or post-construction setup)
     * so AudioFocus can delegate focus events without a circular constructor dependency.
     */
    fun bind(onPause: () -> Unit, onPlay: () -> Unit, onStop: () -> Unit) {
        this.onPause = onPause
        this.onPlay = onPlay
        this.onStop = onStop
    }

    /** Request audio focus and register the AUDIO_BECOMING_NOISY receiver. */
    fun requestFocus() {
        audioManager.requestAudioFocus(focusRequest)
        if (!noisyReceiverRegistered) {
            context.registerReceiver(
                noisyReceiver,
                IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            )
            noisyReceiverRegistered = true
        }
        focused = true
    }

    /** Abandon audio focus and unregister the AUDIO_BECOMING_NOISY receiver. */
    fun abandonFocus() {
        audioManager.abandonAudioFocusRequest(focusRequest)
        if (noisyReceiverRegistered) {
            try {
                context.unregisterReceiver(noisyReceiver)
            } catch (_: IllegalArgumentException) {
                // receiver not registered — safe to ignore
            }
            noisyReceiverRegistered = false
        }
        focused = false
    }
}
