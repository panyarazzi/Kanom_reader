package com.nextz.kanomtts.playback

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.nextz.kanomtts.data.repository.BookReader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * A [SimpleBasePlayer] that bridges [TtsPlaybackController] into the media3 ecosystem.
 *
 * Observes [TtsPlaybackController.state] on the main looper and calls [invalidateState]
 * on every change, which causes media3 to re-read [getState] and propagate Player events
 * to MediaSession (notification + lock-screen controls).
 *
 * Audio focus is NOT managed here — [AudioFocus] handles it by observing controller state.
 */
@OptIn(UnstableApi::class)
class TtsPlayer(
    looper: Looper,
    private val controller: TtsPlaybackController,
    private val bookReader: BookReader,
) : SimpleBasePlayer(looper) {

    private val availableCommands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_RELEASE,
        ).build()

    // Backing state — mutated only on the main thread (player looper = main looper)
    private var playWhenReady = false
    private var playbackState = Player.STATE_IDLE
    private var currentIndex = 0

    // The single media item for the current book; null when nothing is loaded.
    // Assigned on Main (collector runs on Main; resolveMediaItem resumes on Main).
    @Volatile
    private var currentItem: MediaItemData? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            controller.state.collect { state ->
                when (state) {
                    is PlaybackState.Playing -> {
                        // Resolve + assign the media item BEFORE flipping to a non-idle
                        // playbackState so getState() never reports READY with an empty
                        // playlist. (resolveMediaItem is suspend and resumes on Main.)
                        currentItem = resolveMediaItem(state.bookId)
                        playWhenReady = true
                        playbackState = Player.STATE_READY
                    }
                    is PlaybackState.Paused -> {
                        currentItem = resolveMediaItem(state.bookId)
                        playWhenReady = false
                        playbackState = Player.STATE_READY
                    }
                    is PlaybackState.Loading -> {
                        // Loading carries no bookId, so we can't resolve a media item here.
                        // Keep whatever currentItem we already have (null on a fresh start);
                        // the getState() guard maps null-item + BUFFERING → IDLE safely until
                        // the controller emits Playing (which carries the bookId).
                        playWhenReady = false
                        playbackState = Player.STATE_BUFFERING
                    }
                    is PlaybackState.Idle -> {
                        currentItem = null
                        playWhenReady = false
                        playbackState = Player.STATE_IDLE
                    }
                    is PlaybackState.Error -> {
                        currentItem = null
                        playWhenReady = false
                        playbackState = Player.STATE_IDLE
                    }
                }
                invalidateState()
            }
        }
    }

    /**
     * Resolves [bookId] into a single [MediaItemData] for the notification / lock-screen.
     *
     * uid = bookId (a stable, unique [Any]). Title/author/artwork come from the book row;
     * falls back to bookId as the title when the book can't be loaded.
     */
    private suspend fun resolveMediaItem(bookId: String): MediaItemData {
        val book = bookReader.get(bookId)
        val metadata = MediaMetadata.Builder()
            .setTitle(book?.title ?: bookId)
            .setArtist(book?.author)
            .setAlbumTitle(book?.title)
            .apply {
                val coverPath = book?.coverPath
                if (coverPath != null) {
                    setArtworkUri(Uri.fromFile(File(coverPath)))
                }
            }
            .setIsPlayable(true)
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(bookId)
            .setMediaMetadata(metadata)
            .build()

        return MediaItemData.Builder(bookId as Any)
            .setMediaItem(mediaItem)
            .setMediaMetadata(metadata)
            .setIsSeekable(false)
            .setIsDynamic(false)
            .setDurationUs(C.TIME_UNSET)
            .build()
    }

    override fun getState(): State {
        val playlist = listOfNotNull(currentItem)
        // BULLETPROOF INVARIANT: media3 forbids an empty playlist while the
        // playbackState is anything other than IDLE/ENDED. Resolve the playlist
        // first; if it is empty, force IDLE + playWhenReady=false REGARDLESS of the
        // controller state. This holds on EVERY getState() return and is the safety
        // net for the brief window before currentItem is resolved.
        val (effectiveState, effectivePwr) = chooseState(playlist.isEmpty(), playbackState, playWhenReady)
        return State.Builder()
            .setAvailableCommands(availableCommands)
            .setPlaybackState(effectiveState)
            .setPlayWhenReady(effectivePwr, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) 0 else currentIndex.coerceIn(0, playlist.size - 1))
            .setContentPositionMs(C.TIME_UNSET)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        // Set the local field first so the next synchronous getState() is correct,
        // rather than waiting for the async controller.state round-trip (avoids
        // a stale-playWhenReady window that flickers the notification).
        this.playWhenReady = playWhenReady
        invalidateState()
        if (playWhenReady) controller.play() else controller.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        playbackState = Player.STATE_READY
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        controller.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> {
        // Cancel the controller.state collector so it doesn't outlive release.
        scope.cancel()
        controller.shutdown()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(
        mediaItemIndex: Int,
        positionMs: Long,
        seekCommand: Int,
    ): ListenableFuture<*> {
        // The whole book is a single MediaItem, so media3's next/previous-MEDIA-ITEM seeks
        // collapse to index 0 — which would restart the book instead of moving one sentence.
        // Route the transport intents to sentence-level skip; only genuine index seeks fall
        // through to jumpToSegment.
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_NEXT -> controller.nextSentence()
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS -> controller.previousSentence()
            else -> {
                currentIndex = mediaItemIndex
                controller.jumpToSegment(mediaItemIndex)
            }
        }
        return Futures.immediateVoidFuture()
    }

    companion object {
        /**
         * Pure decision for the media3 [State] invariant: an EMPTY playlist is only
         * legal while the playbackState is IDLE or ENDED. When the playlist is empty
         * we force IDLE + playWhenReady=false; otherwise the desired values pass through.
         *
         * Extracted so it can be unit-tested without constructing a media3 [State]
         * (whose validation is what threw on-device). Returns (playbackState, playWhenReady).
         */
        fun chooseState(
            playlistEmpty: Boolean,
            desiredPlaybackState: Int,
            desiredPlayWhenReady: Boolean,
        ): Pair<Int, Boolean> = if (playlistEmpty) {
            Player.STATE_IDLE to false
        } else {
            desiredPlaybackState to desiredPlayWhenReady
        }
    }
}
