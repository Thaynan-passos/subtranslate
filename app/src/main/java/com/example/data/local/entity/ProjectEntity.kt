package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.model.OutputMode
import com.example.domain.model.Project
import com.example.domain.model.ProjectStatus

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val videoUri: String,
    val durationMs: Long = 0,
    val videoWidth: Int = 1920,
    val videoHeight: Int = 1080,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "pt",
    val samplingFps: Int = 2,
    val outputMode: String = OutputMode.SRT_ONLY.name,
    val status: String = ProjectStatus.IDLE.name,
    val progress: Float = 0f,
    val currentStep: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val roiLeft: Float = 0.05f,
    val roiTop: Float = 0.70f,
    val roiRight: Float = 0.95f,
    val roiBottom: Float = 0.95f
) {
    fun toDomain(linesCount: Int = 0): Project {
        return Project(
            id = id,
            title = title,
            videoUri = videoUri,
            durationMs = durationMs,
            videoWidth = videoWidth,
            videoHeight = videoHeight,
            sourceLanguage = sourceLanguage,
            targetLanguage = targetLanguage,
            samplingFps = samplingFps,
            outputMode = try { OutputMode.valueOf(outputMode) } catch (_: Exception) { OutputMode.SRT_ONLY },
            status = try { ProjectStatus.valueOf(status) } catch (_: Exception) { ProjectStatus.IDLE },
            progress = progress,
            currentStep = currentStep,
            errorMessage = errorMessage,
            createdAt = createdAt,
            roiLeft = roiLeft,
            roiTop = roiTop,
            roiRight = roiRight,
            roiBottom = roiBottom,
            linesCount = linesCount
        )
    }

    companion object {
        fun fromDomain(project: Project): ProjectEntity {
            return ProjectEntity(
                id = project.id,
                title = project.title,
                videoUri = project.videoUri,
                durationMs = project.durationMs,
                videoWidth = project.videoWidth,
                videoHeight = project.videoHeight,
                sourceLanguage = project.sourceLanguage,
                targetLanguage = project.targetLanguage,
                samplingFps = project.samplingFps,
                outputMode = project.outputMode.name,
                status = project.status.name,
                progress = project.progress,
                currentStep = project.currentStep,
                errorMessage = project.errorMessage,
                createdAt = project.createdAt,
                roiLeft = project.roiLeft,
                roiTop = project.roiTop,
                roiRight = project.roiRight,
                roiBottom = project.roiBottom
            )
        }
    }
}
