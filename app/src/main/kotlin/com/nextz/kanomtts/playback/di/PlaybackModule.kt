package com.nextz.kanomtts.playback.di

import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import com.nextz.kanomtts.data.repository.BookReader
import com.nextz.kanomtts.data.repository.VoiceConfigRepository
import com.nextz.kanomtts.playback.DefaultTtsContentSource
import com.nextz.kanomtts.playback.SystemTtsProvider
import com.nextz.kanomtts.playback.TtsContentSource
import com.nextz.kanomtts.playback.TtsPlaybackController
import com.nextz.kanomtts.playback.PlaybackStopper
import com.nextz.kanomtts.playback.TtsPlaybackSource
import com.nextz.kanomtts.playback.TtsPlayer
import com.nextz.kanomtts.playback.TtsProvider
import com.nextz.kanomtts.playback.VoiceCatalog
import com.nextz.kanomtts.playback.VoiceConfigSupplier
import com.nextz.kanomtts.playback.VoicePreviewer
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlaybackModule {

    @Binds
    @Singleton
    abstract fun ttsProvider(impl: SystemTtsProvider): TtsProvider

    @Binds
    @Singleton
    abstract fun voiceCatalog(impl: SystemTtsProvider): VoiceCatalog

    @Binds
    @Singleton
    abstract fun ttsContentSource(impl: DefaultTtsContentSource): TtsContentSource

    @Binds
    @Singleton
    abstract fun ttsPlaybackSource(impl: TtsPlaybackController): TtsPlaybackSource

    @Binds
    @Singleton
    abstract fun voicePreviewer(impl: TtsPlaybackController): VoicePreviewer

    @Binds
    @Singleton
    abstract fun playbackStopper(impl: TtsPlaybackController): PlaybackStopper

    companion object {
        /**
         * Application-scoped playback coroutine scope.
         * SupervisorJob ensures child failures do not propagate to the scope.
         * Main dispatcher matches the single-threaded model used in the controller.
         */
        @Provides
        @Singleton
        @PlaybackScope
        fun playbackCoroutineScope(): CoroutineScope =
            CoroutineScope(SupervisorJob() + Dispatchers.Main)

        /**
         * VoiceConfigSupplier that reads the current voice configs on demand.
         * Each [VoiceConfigSupplier.load] call reads the latest snapshot from Room.
         * [VoiceConfigSupplier.observe] provides a live stream so mid-session edits apply at the next segment.
         */
        @Provides
        @Singleton
        fun voiceConfigSupplier(repo: VoiceConfigRepository): VoiceConfigSupplier =
            object : VoiceConfigSupplier {
                override suspend fun load(): Map<String, VoiceConfigEntity> =
                    repo.observeAll().first().associateBy { it.lang }

                override fun observe(): Flow<Map<String, VoiceConfigEntity>> =
                    repo.observeAll().map { list -> list.associateBy { it.lang } }
            }

        /**
         * Provides [TtsPlayer] on the main looper so media3 state is driven from
         * the main thread (matching [SimpleBasePlayer]'s threading contract).
         */
        @Provides
        @Singleton
        @OptIn(UnstableApi::class)
        fun ttsPlayer(
            controller: TtsPlaybackController,
            bookReader: BookReader,
        ): TtsPlayer = TtsPlayer(Looper.getMainLooper(), controller, bookReader)
    }
}
