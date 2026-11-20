package com.nextz.kanomtts.data.repository

import com.nextz.kanomtts.data.db.ProgressEntity

interface ProgressWriter {
    suspend fun save(progress: ProgressEntity)

    /**
     * Update only the TTS locator, without touching the reading locator column.
     * Ensures a row exists before updating (INSERT OR IGNORE + UPDATE).
     */
    suspend fun saveTtsLocator(bookId: String, ttsLocator: String?, updatedAt: Long)

    /**
     * Update only the reading locator + percent, without touching ttsLocator.
     * Ensures a row exists before updating (INSERT OR IGNORE + UPDATE).
     */
    suspend fun saveReadingLocator(bookId: String, locator: String?, percent: Float, updatedAt: Long)
}
