package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.SubtitleLineEntity
import com.example.data.local.entity.TranslationCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleLineDao {

    @Query("SELECT * FROM subtitle_lines WHERE projectId = :projectId ORDER BY startTimeMs ASC, lineIndex ASC, id ASC")
    fun getLinesByProject(projectId: Long): Flow<List<SubtitleLineEntity>>

    @Query("SELECT * FROM subtitle_lines WHERE projectId = :projectId ORDER BY startTimeMs ASC, lineIndex ASC, id ASC")
    suspend fun getLinesListByProject(projectId: Long): List<SubtitleLineEntity>

    @Query("SELECT COUNT(*) FROM subtitle_lines WHERE projectId = :projectId")
    suspend fun countLinesByProject(projectId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<SubtitleLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: SubtitleLineEntity): Long

    @Update
    suspend fun updateLine(line: SubtitleLineEntity)

    @Transaction
    @Update
    suspend fun updateLines(lines: List<SubtitleLineEntity>)

    @Query("UPDATE subtitle_lines SET originalText = :originalText, translatedText = :translatedText WHERE id = :id")
    suspend fun updateLineText(id: Long, originalText: String, translatedText: String)

    @Query("UPDATE subtitle_lines SET startTimeMs = :startTimeMs, endTimeMs = :endTimeMs WHERE id = :id")
    suspend fun updateLineTimestamps(id: Long, startTimeMs: Long, endTimeMs: Long)

    @Delete
    suspend fun deleteLine(line: SubtitleLineEntity)

    @Query("DELETE FROM subtitle_lines WHERE id = :id")
    suspend fun deleteLineById(id: Long)

    @Query("DELETE FROM subtitle_lines WHERE projectId = :projectId")
    suspend fun deleteLinesByProject(projectId: Long)
}

@Dao
interface TranslationCacheDao {

    @Query("SELECT translatedText FROM translation_cache WHERE sourceText = :text AND sourceLang = :sourceLang AND targetLang = :targetLang AND engine = :engine LIMIT 1")
    suspend fun getCachedTranslation(text: String, sourceLang: String, targetLang: String, engine: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCache(cache: TranslationCacheEntity)
}
