package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.VoiceConfigDao
import com.nextz.kanomtts.data.db.VoiceConfigEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class VoiceConfigRepository @Inject constructor(private val dao: VoiceConfigDao) {
    fun observeAll(): Flow<List<VoiceConfigEntity>> = dao.observeAll()
    suspend fun get(lang: String): VoiceConfigEntity? = dao.get(lang)
    suspend fun set(config: VoiceConfigEntity) = dao.upsert(config)
}
