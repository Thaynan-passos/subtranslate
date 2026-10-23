package com.example.data.repository

import com.example.data.local.dao.SubtitleLineDao
import com.example.data.local.dao.TranslationCacheDao
import com.example.data.local.entity.SubtitleLineEntity
import com.example.data.local.entity.TranslationCacheEntity
import com.example.domain.model.SubtitleLine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SubtitleRepository(
    private val subtitleLineDao: SubtitleLineDao,
    private val translationCacheDao: TranslationCacheDao
) {

    fun getLines(projectId: Long): Flow<List<SubtitleLine>> {
        return subtitleLineDao.getLinesByProject(projectId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getLinesList(projectId: Long): List<SubtitleLine> {
        return subtitleLineDao.getLinesListByProject(projectId).map { it.toDomain() }
    }

    suspend fun saveLines(lines: List<SubtitleLine>) {
        subtitleLineDao.insertLines(lines.map { SubtitleLineEntity.fromDomain(it) })
    }

    suspend fun saveLine(line: SubtitleLine): Long {
        return subtitleLineDao.insertLine(SubtitleLineEntity.fromDomain(line))
    }

    suspend fun updateLine(line: SubtitleLine) {
        subtitleLineDao.updateLine(SubtitleLineEntity.fromDomain(line))
    }

    suspend fun updateLineText(id: Long, originalText: String, translatedText: String) {
        subtitleLineDao.updateLineText(id, originalText, translatedText)
    }

    suspend fun updateLineTimestamps(id: Long, startTimeMs: Long, endTimeMs: Long) {
        subtitleLineDao.updateLineTimestamps(id, startTimeMs, endTimeMs)
    }

    suspend fun updateLines(lines: List<SubtitleLine>) {
        subtitleLineDao.updateLines(lines.map(SubtitleLineEntity::fromDomain))
    }

    suspend fun deleteLine(lineId: Long) {
        subtitleLineDao.deleteLineById(lineId)
    }

    suspend fun clearLines(projectId: Long) {
        subtitleLineDao.deleteLinesByProject(projectId)
    }

    suspend fun getCachedTranslation(
        text: String,
        sourceLang: String,
        targetLang: String,
        engine: String
    ): String? {
        return translationCacheDao.getCachedTranslation(text, sourceLang, targetLang, engine)
    }

    suspend fun cacheTranslation(
        text: String,
        sourceLang: String,
        targetLang: String,
        engine: String,
        translatedText: String
    ) {
        translationCacheDao.insertCache(
            TranslationCacheEntity(
                sourceText = text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                engine = engine,
                translatedText = translatedText
            )
        )
    }
}
