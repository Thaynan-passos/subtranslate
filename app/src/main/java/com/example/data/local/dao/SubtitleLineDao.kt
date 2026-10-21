package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.SubtitleLineEntity
import com.example.data.local.entity.TranslationCacheEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SubtitleLineDao {

    @Query("SELECT * FROM subtitle_lines WHERE projectId = :projectId ORDER BY startTimeMs ASC")
    fun getLinesByProject(projectId: Long): Flow<List<SubtitleLineEntity>>

    @Query("SELECT * FROM subtitle_lines WHERE projectId = :projectId ORDER BY startTimeMs ASC")
    suspend fun getLinesListByProject(projectId: Long): List<SubtitleLineEntity>

    @Query("SELECT COUNT(*) FROM subtitle_lines WHERE projectId = :projectId")
    suspend fun countLinesByProject(projectId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<SubtitleLineEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLine(line: SubtitleLineEntity): Long

    @Update
    suspend fun updateLine(line: SubtitleLineEntity)

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
