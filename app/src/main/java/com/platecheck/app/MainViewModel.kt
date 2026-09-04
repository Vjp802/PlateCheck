package com.platecheck.app

import android.graphics.Bitmap
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.FeatureStatus
import com.platecheck.app.model.AnalysisResult
import com.platecheck.app.model.HistoryItem
import com.platecheck.app.model.UiState
import com.platecheck.app.model.Verdict
import com.platecheck.app.service.NanoService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

class MainViewModel : ViewModel() {

    private val nanoService = NanoService()

    private val _uiState = MutableStateFlow<UiState>(UiState.Setup())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val history = mutableListOf<HistoryItem>()

    private val isEmulator = Build.PRODUCT.contains("sdk") || 
                           Build.MODEL.contains("Emulator") || 
                           Build.FINGERPRINT.contains("generic")

    init {
        checkModelAvailability()
    }

    private fun checkModelAvailability() {
        viewModelScope.launch {
            if (isEmulator) {
                _uiState.value = UiState.Setup("Emulator detected: Enabling Mock AI Mode...")
                delay(1.seconds)
                _uiState.value = UiState.Idle
                return@launch
            }

            try {
                when (nanoService.checkStatus()) {
                    FeatureStatus.AVAILABLE -> {
                        _uiState.value = UiState.Idle
                    }
                    FeatureStatus.DOWNLOADABLE -> {
                        _uiState.value = UiState.Setup("Downloading on-device AI model...")
                        try {
                            nanoService.downloadModel()
                            _uiState.value = UiState.Idle
                        } catch (e: Exception) {
                            _uiState.value = UiState.Unavailable(
                                "Failed to download AI model: ${e.localizedMessage}",
                            )
                        }
                    }
                    FeatureStatus.DOWNLOADING -> {
                        _uiState.value = UiState.Setup("AI model is downloading...")
                        // Poll periodically instead of immediate recursion to avoid stack issues or rapid looping
                        delay(5.seconds)
                        checkModelAvailability()
                    }
                    else -> {
                        _uiState.value = UiState.Unavailable(
                            "On-device AI is not available on this device.",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Unavailable(
                    "Error checking AI availability: ${e.localizedMessage}",
                )
            }
        }
    }

    fun onPhotoCaptured(bitmap: Bitmap) {
        _uiState.value = UiState.Analyzing(bitmap)
        viewModelScope.launch {
            try {
                val result = if (isEmulator) {
                    mockAnalysis()
                } else {
                    nanoService.analyzeFood(bitmap)
                }
                
                // Add to history
                history.add(0, HistoryItem(photo = bitmap, result = result))
                
                _uiState.value = UiState.Result(photo = bitmap, result = result)
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    photo = bitmap,
                    message = "Analysis failed: ${e.localizedMessage}",
                    rawResponse = e.message,
                )
            }
        }
    }

    private suspend fun mockAnalysis(): AnalysisResult {
        delay(6.seconds)
        return when (Random.nextInt(3)) {
            0 -> AnalysisResult(
                verdict = Verdict.GOOD,
                reason = "Mock: This meal has a perfect balance of grilled salmon, quinoa, and steamed broccoli.",
                suggestion = "NONE",
                rawResponse = "VERDICT: GOOD\nREASON: Balanced salmon meal.\nSUGGESTION: NONE",
            )
            1 -> AnalysisResult(
                verdict = Verdict.MODIFY,
                reason = "Mock: The pasta dish looks delicious but seems to be lacking a significant source of fiber or vegetables.",
                suggestion = "Mock: Try adding a side salad or mixing in some sautéed spinach to increase the nutrient density.",
                rawResponse = "VERDICT: MODIFY\nREASON: Needs more fiber.\nSUGGESTION: Add spinach.",
            )
            else -> AnalysisResult(
                verdict = Verdict.NOT_RECOMMENDED,
                reason = "Mock: This meal consists primarily of deep-fried items and lacks fresh produce or complex carbohydrates.",
                suggestion = "NONE",
                rawResponse = "VERDICT: NOT_RECOMMENDED\nREASON: Mostly fried food.\nSUGGESTION: NONE",
            )
        }
    }

    fun toggleDetails() {
        (_uiState.value as? UiState.Result)?.let { current ->
            _uiState.value = current.copy(showDetails = !current.showDetails)
        }
    }

    fun navigateToHistory() {
        _uiState.value = UiState.History(history.toList())
    }

    fun resetToIdle() {
        _uiState.value = UiState.Idle
    }

    fun retryFromError() {
        (_uiState.value as? UiState.Error)?.let { current ->
            onPhotoCaptured(current.photo)
        }
    }
}
