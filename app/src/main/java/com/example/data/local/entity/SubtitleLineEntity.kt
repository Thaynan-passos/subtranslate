package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.SubtitleLine

@Entity(
    tableName = "subtitle_lines",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("projectId"),
        Index("startTimeMs")
    ]
)
data class SubtitleLineEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long,
    val lineIndex: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val originalText: String,
    val translatedText: String,
    val confidence: Float = 1.0f,
    val isFlagged: Boolean = false
) {
    fun toDomain(): SubtitleLine {
        return SubtitleLine(
            id = id,
            projectId = projectId,
            index = lineIndex,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            originalText = originalText,
            translatedText = translatedText,
            confidence = confidence,
            isFlagged = isFlagged
        )
    }

    companion object {
        fun fromDomain(line: SubtitleLine): SubtitleLineEntity {
            return SubtitleLineEntity(
                id = line.id,
                projectId = line.projectId,
                lineIndex = line.index,
                startTimeMs = line.startTimeMs,
                endTimeMs = line.endTimeMs,
                originalText = line.originalText,
                translatedText = line.translatedText,
                confidence = line.confidence,
                isFlagged = line.isFlagged
            )
        }
    }
}

@Entity(
    tableName = "translation_cache",
    primaryKeys = ["sourceText", "sourceLang", "targetLang", "engine"]
)
data class TranslationCacheEntity(
    val sourceText: String,
    val sourceLang: String,
    val targetLang: String,
    val engine: String,
    val translatedText: String,
    val timestamp: Long = System.currentTimeMillis()
)
