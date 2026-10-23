package com.example.domain.model

data class BoundingBox(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

data class OcrTextBlock(
    val text: String,
    val confidence: Float,
    val boundingBox: BoundingBox? = null
)

data class OcrResult(
    val fullText: String,
    val confidence: Float,
    val blocks: List<OcrTextBlock> = emptyList()
)

data class RawFrameDetection(
    val timestampMs: Long,
    val text: String,
    val confidence: Float
)

data class AppSettings(
    val defaultFps: Int = 3,
    val minSpeechDurationMs: Long = 300L,
    val textSimilarityThreshold: Float = 0.85f,
    val maxCharsPerLine: Int = 42,
    val coverOriginalOverlay: Boolean = true,
    val overlayColorHex: String = "#E6000000",
    val subtitleFontSizeSp: Int = 18,
    val subtitleTextColorHex: String = "#FFFFFFFF",
    val subtitleOutlineColorHex: String = "#FF000000",
    val glossaryRaw: String = "",
    val glossaryTerms: Map<String, String> = emptyMap()
)
