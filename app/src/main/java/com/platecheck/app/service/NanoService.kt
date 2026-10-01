package com.platecheck.app.service

import android.graphics.Bitmap
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.ImagePart
import com.platecheck.app.model.AnalysisResult
import com.platecheck.app.model.Verdict
import com.google.mlkit.genai.common.DownloadStatus

/**
 * Wraps all ML Kit GenAI Prompt API interactions for on-device
 * food photo analysis via Gemini Nano.
 */
class NanoService {

    private val generativeModel: GenerativeModel by lazy {
        Generation.getClient()
    }

    /**
     * Check whether Gemini Nano is available, downloadable, or unavailable.
     */
    suspend fun checkStatus(): Int {
        return generativeModel.checkStatus()
    }

    /**
     * Trigger download of Gemini Nano if it's in DOWNLOADABLE state and collect until completion.
     */
    suspend fun downloadModel(onProgress: ((Float?) -> Unit)? = null) {
        var totalBytes = 0L
        generativeModel.download().collect { status ->
            when (status) {
                is DownloadStatus.DownloadStarted -> {
                    totalBytes = status.bytesToDownload
                    onProgress?.invoke(0f)
                }
                is DownloadStatus.DownloadProgress -> {
                    if (totalBytes > 0) {
                        val ratio = status.totalBytesDownloaded.toFloat() / totalBytes.toFloat()
                        onProgress?.invoke(ratio)
                    } else {
                        onProgress?.invoke(null)
                    }
                }
                is DownloadStatus.DownloadCompleted -> {
                    onProgress?.invoke(1f)
                }
                is DownloadStatus.DownloadFailed -> {
                    throw status.e
                }
            }
        }
    }

    /**
     * Analyze a food photo on-device and return a structured result.
     */
    suspend fun analyzeFood(photo: Bitmap): AnalysisResult {
        val request = GenerateContentRequest.Builder(
            ImagePart(photo),
            TextPart(FoodAnalysisParser.FOOD_ANALYSIS_PROMPT.trimIndent()),
        ).build()

        val response = generativeModel.generateContent(request)
        val responseText = response.candidates.firstOrNull()?.text ?: throw Exception("Empty response from model")

        return FoodAnalysisParser.parseResponse(responseText)
    }
}
