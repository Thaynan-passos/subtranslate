package com.example.domain.engine

import android.graphics.Bitmap
import com.example.domain.model.OcrResult

interface OcrEngine {
    val name: String

    /**
     * Performs text recognition on the given bitmap region.
     */
    suspend fun recognizeText(bitmap: Bitmap, languageCode: String): OcrResult

    /**
     * Cleans up any resources.
     */
    fun close()
}
