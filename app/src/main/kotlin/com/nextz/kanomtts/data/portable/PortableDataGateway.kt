package com.nextz.kanomtts.data.portable

import com.nextz.kanomtts.data.db.BookEntity

/**
 * Test seam for backup/restore orchestration. The implementation owns ZIP, SAF and
 * Room/DataStore operations; ViewModels depend only on this contract.
 */
interface PortableDataGateway {
    fun hasStorageAccess(): Boolean
    fun ensurePortableFolders(): Boolean
    fun latestBackupExists(): Boolean
    suspend fun createBackup(): PortableBackupSummary
    suspend fun previewBackupBooks(): List<BookEntity>
    suspend fun restoreLatestBackup(
        sourceMappings: Map<String, RestoreSourceCandidate> = emptyMap(),
    ): PortableBackupSummary
}
