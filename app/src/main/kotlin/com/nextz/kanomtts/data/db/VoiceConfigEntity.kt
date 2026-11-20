package com.nextz.kanomtts.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row per routing language (TH/EN/JA/ZH). */
@Entity(tableName = "voice_config")
data class VoiceConfigEntity(
    @PrimaryKey val lang: String,        // Lang.name
    val providerId: String,
    val voiceId: String?,
    val speed: Float,
    val pitch: Float,
)
