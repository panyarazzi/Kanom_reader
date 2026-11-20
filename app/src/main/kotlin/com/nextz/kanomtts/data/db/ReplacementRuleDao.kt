package com.nextz.kanomtts.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReplacementRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(rule: ReplacementRuleEntity): Long
    @Update suspend fun update(rule: ReplacementRuleEntity)
    @Query("DELETE FROM replacement_rules WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM replacement_rules WHERE scope = 'book' AND bookId = :bookId")
    suspend fun deleteByBook(bookId: String)

    // Effective rules for a book: global + that book's own, ordered.
    @Query("""SELECT * FROM replacement_rules
              WHERE scope = 'global' OR (scope = 'book' AND bookId = :bookId)
              ORDER BY position ASC""")
    fun observeEffective(bookId: String): Flow<List<ReplacementRuleEntity>>

    @Query("""SELECT * FROM replacement_rules
              WHERE showInReader = 1 AND (scope = 'global' OR (scope = 'book' AND bookId = :bookId))
              ORDER BY position ASC""")
    fun observeEffectiveVisual(bookId: String): Flow<List<ReplacementRuleEntity>>

    @Query("SELECT * FROM replacement_rules WHERE scope = 'global' ORDER BY position ASC")
    fun observeGlobal(): Flow<List<ReplacementRuleEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) FROM replacement_rules")
    suspend fun maxPosition(): Int

    @Query("SELECT * FROM replacement_rules WHERE scope = 'book' AND bookId = :bookId ORDER BY position ASC")
    fun observeByBook(bookId: String): Flow<List<ReplacementRuleEntity>>

    @Query("SELECT COUNT(*) FROM replacement_rules WHERE scope = 'global'")
    fun globalCount(): Flow<Int>

    @Update
    suspend fun updateAll(rules: List<ReplacementRuleEntity>)
}
