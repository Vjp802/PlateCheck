package com.platecheck.app.model

import android.graphics.Bitmap
import java.util.Date

/**
 * Represents the verdict from the on-device AI analysis.
 */
enum class Verdict {
    GOOD,
    NOT_RECOMMENDED,
    MODIFY
}

/**
 * Holds the parsed result from Gemini Nano.
 */
data class AnalysisResult(
    val foodItems: List<String> = emptyList(),
    val verdict: Verdict,
    val reason: String,
    val suggestion: String,
    val rawResponse: String
)

/**
 * A record in the user's history.
 */
data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Date = Date(),
    val photo: Bitmap,
    val result: AnalysisResult
)

/**
 * Sealed class representing the visual states of the app.
 */
sealed class UiState {
    /** Gemini Nano is being downloaded or checked. */
    data class Setup(
        val message: String = "Setting up on-device AI...",
        val progress: Float? = null
    ) : UiState()

    /** Camera viewfinder is active, ready to capture. */
    data object Idle : UiState()

    /** Viewing the full history list. */
    data class History(val items: List<HistoryItem>) : UiState()

    /** Photo has been taken, waiting for on-device inference. */
    data class Analyzing(val photo: Bitmap) : UiState()

    /** Inference complete, showing the result card. */
    data class Result(
        val photo: Bitmap,
        val result: AnalysisResult,
        val showDetails: Boolean = false
    ) : UiState()

    /** Gemini Nano is not available on this device. */
    data class Unavailable(
        val message: String = "On-device AI is not available on this device."
    ) : UiState()

    /** Something went wrong during inference. */
    data class Error(
        val photo: Bitmap,
        val message: String,
        val rawResponse: String? = null
    ) : UiState()
}
