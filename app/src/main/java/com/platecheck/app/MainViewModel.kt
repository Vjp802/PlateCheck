package com.platecheck.app

import android.app.Application
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.mlkit.genai.common.FeatureStatus
import com.platecheck.app.data.AppDatabase
import com.platecheck.app.data.HistoryEntity
import com.platecheck.app.model.AnalysisResult
import com.platecheck.app.model.HistoryItem
import com.platecheck.app.model.UiState
import com.platecheck.app.model.Verdict
import com.platecheck.app.service.CloudGeminiService
import com.platecheck.app.service.NanoService
import com.platecheck.app.util.BitmapUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds

private const val TAG = "PlateCheck"

enum class AiEngine {
    ON_DEVICE_NANO,
    CLOUD_FLASH,
    MOCK
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val nanoService = NanoService()
    private val historyDao = AppDatabase.getDatabase(application).historyDao()

    // Cloud fallback via Firebase AI Logic. Controlled by CLOUD_FALLBACK_ENABLED in build.gradle.kts.
    private val cloudGeminiService: CloudGeminiService? =
        if (BuildConfig.CLOUD_FALLBACK_ENABLED) CloudGeminiService() else null

    private var analysisJob: Job? = null

    var activeEngine: AiEngine = AiEngine.MOCK
        private set

    private val _uiState = MutableStateFlow<UiState>(UiState.Setup())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var historyCache: List<HistoryItem> = emptyList()

    private val isEmulator = Build.PRODUCT.contains("sdk") || 
                           Build.MODEL.contains("Emulator") || 
                           Build.FINGERPRINT.contains("generic") ||
                           Build.FINGERPRINT.startsWith("google/sdk_gphone") ||
                           Build.HARDWARE.contains("goldfish") ||
                           Build.HARDWARE.contains("ranchu")

    init {
        observeHistory()
        checkModelAvailability()
    }

    private fun observeHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            historyDao.getAllHistory().collect { entities ->
                historyCache = entities.mapNotNull { entity ->
                    val bitmap = BitmapUtils.loadBitmapFromPath(entity.imagePath) ?: return@mapNotNull null
                    val verdict = try {
                        Verdict.valueOf(entity.verdict)
                    } catch (_: Exception) {
                        Verdict.GOOD
                    }
                    val items = entity.foodItemsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                    HistoryItem(
                        id = entity.id,
                        timestamp = Date(entity.timestampMs),
                        photo = bitmap,
                        result = AnalysisResult(
                            foodItems = items,
                            verdict = verdict,
                            reason = entity.reason,
                            suggestion = entity.suggestion,
                            rawResponse = entity.rawResponse
                        )
                    )
                }
            }
        }
    }

    private fun checkModelAvailability() {
        viewModelScope.launch {
            if (isEmulator) {
                if (cloudGeminiService != null) {
                    activeEngine = AiEngine.CLOUD_FLASH
                    _uiState.value = UiState.Setup("Emulator: Using cloud Gemini...")
                } else {
                    activeEngine = AiEngine.MOCK
                    _uiState.value = UiState.Setup("Emulator: Enabling Mock AI Mode...")
                }
                delay(1.seconds)
                _uiState.value = UiState.Idle
                return@launch
            }

            try {
                var status = nanoService.checkStatus()
                while (status == FeatureStatus.DOWNLOADING) {
                    _uiState.value = UiState.Setup("AI model is downloading...")
                    delay(5.seconds)
                    status = nanoService.checkStatus()
                }

                when (status) {
                    FeatureStatus.AVAILABLE -> {
                        activeEngine = AiEngine.ON_DEVICE_NANO
                        _uiState.value = UiState.Idle
                    }
                    FeatureStatus.DOWNLOADABLE -> {
                        _uiState.value = UiState.Setup("Downloading on-device AI model...")
                        try {
                            nanoService.downloadModel { progress ->
                                val pctMsg = if (progress != null) " (${(progress * 100).toInt()}%)" else ""
                                _uiState.value = UiState.Setup(
                                    message = "Downloading on-device AI model$pctMsg...",
                                    progress = progress
                                )
                            }
                            activeEngine = AiEngine.ON_DEVICE_NANO
                            _uiState.value = UiState.Idle
                        } catch (e: Exception) {
                            fallbackToCloudOrUnavailable("Failed to download AI model: ${e.localizedMessage}")
                        }
                    }
                    else -> {
                        fallbackToCloudOrUnavailable("On-device AI is not available on this device.")
                    }
                }
            } catch (e: Exception) {
                fallbackToCloudOrUnavailable("Error checking AI availability: ${e.localizedMessage}")
            }
        }
    }

    /**
     * Real devices without Gemini Nano use the cloud model if one is configured.
     * Otherwise show the "not supported" screen. Mock results are emulator-only,
     * so real users never see made-up verdicts.
     */
    private fun fallbackToCloudOrUnavailable(message: String) {
        if (cloudGeminiService != null) {
            activeEngine = AiEngine.CLOUD_FLASH
            _uiState.value = UiState.Idle
        } else {
            _uiState.value = UiState.Unavailable(message)
        }
    }

    fun onPhotoCaptured(rawBitmap: Bitmap) {
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            // Scale down bitmap to prevent memory pressure / OOM crashes
            val scaledBitmap = withContext(Dispatchers.Default) {
                BitmapUtils.scaleDown(rawBitmap, maxDimension = 1024)
            }

            _uiState.value = UiState.Analyzing(
                photo = scaledBitmap,
                statusText = when (activeEngine) {
                    AiEngine.ON_DEVICE_NANO -> "Gemini Nano is checking your food on your phone"
                    AiEngine.CLOUD_FLASH -> "Sending your photo to Gemini in the cloud"
                    AiEngine.MOCK -> "Mock mode: showing a sample result"
                },
            )

            try {
                val result = when (activeEngine) {
                    AiEngine.ON_DEVICE_NANO -> nanoService.analyzeFood(scaledBitmap)
                    AiEngine.CLOUD_FLASH -> {
                        val service = checkNotNull(cloudGeminiService) { "Cloud AI is not configured." }
                        service.analyzeFood(scaledBitmap)
                    }
                    AiEngine.MOCK -> mockAnalysis()
                }

                // Persist to Room database & internal storage
                withContext(Dispatchers.IO) {
                    val id = System.currentTimeMillis()
                    val fileName = "meal_$id.jpg"
                    val path = BitmapUtils.saveBitmapToInternalStorage(getApplication(), scaledBitmap, fileName)

                    historyDao.insertHistory(
                        HistoryEntity(
                            id = id,
                            timestampMs = id,
                            imagePath = path,
                            foodItemsRaw = result.foodItems.joinToString(","),
                            verdict = result.verdict.name,
                            reason = result.reason,
                            suggestion = result.suggestion,
                            rawResponse = result.rawResponse
                        )
                    )
                }

                _uiState.value = UiState.Result(photo = scaledBitmap, result = result)
            } catch (e: CancellationException) {
                // User cancelled: don't show an error or save anything.
                throw e
            } catch (e: Exception) {
                // Log the full exception (incl. cause) so failures are diagnosable in logcat.
                // Android's Log hides stack traces for network errors (UnknownHostException),
                // so also log the exception chain as text.
                val causes = generateSequence(e as Throwable) { it.cause }
                    .joinToString(" <- ") { "${it.javaClass.simpleName}: ${it.message}" }
                Log.e(TAG, "Analysis failed using $activeEngine: $causes", e)
                _uiState.value = UiState.Error(
                    photo = scaledBitmap,
                    message = "Analysis failed: ${e.localizedMessage}",
                    rawResponse = e.message
                )
            }
        }
    }

    private suspend fun mockAnalysis(): AnalysisResult {
        delay(3.seconds)
        return when (Random.nextInt(3)) {
            0 -> AnalysisResult(
                foodItems = listOf("Grilled Salmon", "Quinoa", "Steamed Broccoli"),
                verdict = Verdict.GOOD,
                reason = "Mock: This meal has a perfect balance of grilled salmon, quinoa, and steamed broccoli.",
                suggestion = "NONE",
                rawResponse = "ITEMS: Grilled Salmon, Quinoa, Steamed Broccoli\nVERDICT: GOOD\nREASON: Balanced salmon meal.\nSUGGESTION: NONE"
            )
            1 -> AnalysisResult(
                foodItems = listOf("Pasta Carbonara"),
                verdict = Verdict.MODIFY,
                reason = "Mock: The pasta dish looks delicious but seems to be lacking a significant source of fiber or vegetables.",
                suggestion = "Mock: Try adding a side salad or mixing in some sautéed spinach to increase nutrient density.",
                rawResponse = "ITEMS: Pasta Carbonara\nVERDICT: MODIFY\nREASON: Needs more fiber.\nSUGGESTION: Add spinach."
            )
            else -> AnalysisResult(
                foodItems = listOf("Fried Chicken", "French Fries"),
                verdict = Verdict.NOT_RECOMMENDED,
                reason = "Mock: This meal consists primarily of deep-fried items and lacks fresh produce or complex carbohydrates.",
                suggestion = "NONE",
                rawResponse = "ITEMS: Fried Chicken, French Fries\nVERDICT: NOT_RECOMMENDED\nREASON: Mostly fried food.\nSUGGESTION: NONE"
            )
        }
    }

    fun toggleDetails() {
        (_uiState.value as? UiState.Result)?.let { current ->
            _uiState.value = current.copy(showDetails = !current.showDetails)
        }
    }

    fun navigateToHistory() {
        _uiState.value = UiState.History(historyCache)
    }

    fun resetToIdle() {
        // Stop any in-flight analysis so it can't overwrite this state or save to history.
        analysisJob?.cancel()
        analysisJob = null
        _uiState.value = UiState.Idle
    }

    fun retryFromError() {
        (_uiState.value as? UiState.Error)?.let { current ->
            onPhotoCaptured(current.photo)
        }
    }
}
