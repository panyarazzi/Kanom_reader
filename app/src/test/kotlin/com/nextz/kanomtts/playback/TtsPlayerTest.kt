package com.nextz.kanomtts.playback

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer.MediaItemData
import androidx.media3.common.SimpleBasePlayer.State
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * BUG 1 regression tests for [TtsPlayer].
 *
 * The on-device crash was:
 *   java.lang.IllegalArgumentException: Empty playlist only allowed in
 *   STATE_IDLE or STATE_ENDED  (SimpleBasePlayer$State.<init>)
 * triggered when the controller went Playing/Paused/Loading (non-idle playbackState)
 * while [TtsPlayer]'s playlist was still empty (book not yet resolved).
 *
 * Two layers are proven:
 *  (1) [TtsPlayer.chooseState] — the pure guard: empty playlist forces IDLE + pwr=false.
 *  (2) media3 [State] construction under Robolectric — the guarded combination builds
 *      without throwing; the unguarded combination throws (reproducing the crash).
 */
@OptIn(UnstableApi::class)
@Config(sdk = [35])
@RunWith(RobolectricTestRunner::class)
class TtsPlayerTest {

    // ── (1) pure guard ─────────────────────────────────────────────────────────

    @Test
    fun `chooseState forces IDLE and playWhenReady=false when playlist is empty`() {
        // Controller wants READY + playWhenReady=true (Playing) but playlist is empty.
        val (state, pwr) = TtsPlayer.chooseState(
            playlistEmpty = true,
            desiredPlaybackState = Player.STATE_READY,
            desiredPlayWhenReady = true,
        )
        assertEquals("empty playlist must map to STATE_IDLE", Player.STATE_IDLE, state)
        assertFalse("empty playlist must force playWhenReady=false", pwr)
    }

    @Test
    fun `chooseState forces IDLE even for BUFFERING when playlist is empty`() {
        val (state, pwr) = TtsPlayer.chooseState(
            playlistEmpty = true,
            desiredPlaybackState = Player.STATE_BUFFERING,  // Loading
            desiredPlayWhenReady = false,
        )
        assertEquals(Player.STATE_IDLE, state)
        assertFalse(pwr)
    }

    @Test
    fun `chooseState passes desired state through when playlist is non-empty`() {
        val (state, pwr) = TtsPlayer.chooseState(
            playlistEmpty = false,
            desiredPlaybackState = Player.STATE_READY,
            desiredPlayWhenReady = true,
        )
        assertEquals("non-empty playlist keeps the desired state", Player.STATE_READY, state)
        assertTrue("non-empty playlist keeps the desired playWhenReady", pwr)
    }

    // ── (2) media3 State invariant (device crash path) ─────────────────────────

    private fun buildState(playlist: List<MediaItemData>, playbackState: Int, pwr: Boolean): State =
        State.Builder()
            .setAvailableCommands(Player.Commands.Builder().add(Player.COMMAND_PLAY_PAUSE).build())
            .setPlaybackState(playbackState)
            .setPlayWhenReady(pwr, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .setPlaylist(playlist)
            .setCurrentMediaItemIndex(if (playlist.isEmpty()) 0 else 0)
            .setContentPositionMs(C.TIME_UNSET)
            .build()

    private fun item(bookId: String): MediaItemData =
        MediaItemData.Builder(bookId as Any)
            .setMediaItem(MediaItem.Builder().setMediaId(bookId).build())
            .setIsSeekable(false)
            .setIsDynamic(false)
            .setDurationUs(C.TIME_UNSET)
            .build()

    @Test
    fun `unguarded empty playlist with STATE_READY throws - reproduces the crash`() {
        // This is exactly what getState() used to do for Playing-with-no-item.
        assertThrows(IllegalArgumentException::class.java) {
            buildState(playlist = emptyList(), playbackState = Player.STATE_READY, pwr = true)
        }
    }

    @Test
    fun `guarded empty playlist (forced IDLE) builds without throwing`() {
        val (state, pwr) = TtsPlayer.chooseState(
            playlistEmpty = true,
            desiredPlaybackState = Player.STATE_READY,
            desiredPlayWhenReady = true,
        )
        // Must NOT throw — the guard makes the State invariant hold.
        val built = buildState(playlist = emptyList(), playbackState = state, pwr = pwr)
        assertEquals(Player.STATE_IDLE, built.playbackState)
    }

    @Test
    fun `non-empty playlist with STATE_READY builds and reports READY plus playlist size 1`() {
        val (state, pwr) = TtsPlayer.chooseState(
            playlistEmpty = false,
            desiredPlaybackState = Player.STATE_READY,
            desiredPlayWhenReady = true,
        )
        val built = buildState(playlist = listOf(item("book1")), playbackState = state, pwr = pwr)
        assertEquals(Player.STATE_READY, built.playbackState)
        assertEquals(1, built.playlist.size)
    }
}
