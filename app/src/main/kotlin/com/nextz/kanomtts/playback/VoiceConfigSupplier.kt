package com.nextz.kanomtts.playback

import com.nextz.kanomtts.data.db.VoiceConfigEntity
import kotlinx.coroutines.flow.Flow

interface VoiceConfigSupplier {
    /** One-shot snapshot, keyed by Lang.name. Used at session start. */
    suspend fun load(): Map<String, VoiceConfigEntity>

    /** Live stream of the config map — emits on every edit so playback applies changes at the next segment. */
    fun observe(): Flow<Map<String, VoiceConfigEntity>>
}
