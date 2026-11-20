package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceConfigDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(config: VoiceConfigEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(configs: List<VoiceConfigEntity>)
    @Query("SELECT * FROM voice_config") suspend fun getAllForBackup(): List<VoiceConfigEntity>
    @Query("SELECT * FROM voice_config") fun observeAll(): Flow<List<VoiceConfigEntity>>
    @Query("SELECT * FROM voice_config WHERE lang = :lang") suspend fun get(lang: String): VoiceConfigEntity?
    @Query("DELETE FROM voice_config") suspend fun deleteAll()
}
