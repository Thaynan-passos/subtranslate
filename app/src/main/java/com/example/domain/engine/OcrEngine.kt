package com.example.domain.engine

import android.graphics.Bitmap
import com.example.domain.model.OcrResult

data class OcrPreprocessingOptions(
    val enableHighContrast: Boolean = true,
    val enableOtsuBinarization: Boolean = true
)

interface OcrEngine {
    val name: String

    /**
     * Performs text recognition on the given bitmap region.
     */
    suspend fun recognizeText(bitmap: Bitmap, languageCode: String): OcrResult

    /**
     * Executa o reconhecimento OCR respeitando as opções de pré-processamento configuradas.
     */
    suspend fun recognizeText(
        bitmap: Bitmap,
        languageCode: String,
        options: OcrPreprocessingOptions
    ): OcrResult = recognizeText(bitmap, languageCode)

    /**
     * Cleans up any resources.
     */
    fun close()
}
