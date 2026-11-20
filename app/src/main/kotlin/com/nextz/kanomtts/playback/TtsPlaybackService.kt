package com.nextz.kanomtts.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.nextz.kanomtts.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Hosts [TtsPlayer] as a [MediaSessionService].
 *
 * media3 auto-generates the persistent media notification + lock-screen transport
 * controls from the player's [State] and [MediaMetadata] — no manual NotificationCompat needed.
 *
 * Do NOT call startForeground() manually; MediaSessionService handles it once the player
 * has media items and playWhenReady=true.
 */
@AndroidEntryPoint
@OptIn(UnstableApi::class)
class TtsPlaybackService : MediaSessionService() {

    @Inject lateinit var ttsPlayer: TtsPlayer
    @Inject lateinit var audioFocus: AudioFocus

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val activityPendingIntent = PendingIntent.getActivity(
            /* context = */ this,
            /* requestCode = */ 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        session = MediaSession.Builder(this, ttsPlayer)
            .setSessionActivity(activityPendingIntent)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        session?.run {
            player.release()
            release()
        }
        session = null
        audioFocus.abandonFocus()
        super.onDestroy()
    }
}
