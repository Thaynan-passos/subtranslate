package com.example.domain.model

import java.io.Serializable

enum class ProjectStatus {
    IDLE,
    PROCESSING,
    COMPLETED,
    ERROR,
    PAUSED
}

enum class OutputMode {
    SRT_ONLY
}

data class RegionOfInterest(
    val left: Float = 0.05f,   // normalized 0.0 to 1.0
    val top: Float = 0.70f,
    val right: Float = 0.95f,
    val bottom: Float = 0.95f
) : Serializable

data class Project(
    val id: Long = 0,
    val title: String,
    val videoUri: String,
    val durationMs: Long = 0,
    val videoWidth: Int = 1920,
    val videoHeight: Int = 1080,
    val sourceLanguage: String = "en",
    val targetLanguage: String = "pt",
    val samplingFps: Int = 2,
    val outputMode: OutputMode = OutputMode.SRT_ONLY,
    val status: ProjectStatus = ProjectStatus.IDLE,
    val progress: Float = 0f,
    val currentStep: String = "",
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val roiLeft: Float = 0.05f,
    val roiTop: Float = 0.70f,
    val roiRight: Float = 0.95f,
    val roiBottom: Float = 0.95f,
    val linesCount: Int = 0
) : Serializable
