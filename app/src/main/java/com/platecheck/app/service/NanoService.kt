package com.platecheck.app.service

import android.graphics.Bitmap
import com.google.mlkit.genai.prompt.GenerativeModel
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.GenerateContentRequest
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.ImagePart
import com.platecheck.app.model.AnalysisResult
import com.platecheck.app.model.Verdict
import kotlinx.coroutines.flow.first

/**
 * Wraps all ML Kit GenAI Prompt API interactions for on-device
 * food photo analysis via Gemini Nano.
 */
class NanoService {

    private val generativeModel: GenerativeModel by lazy {
        Generation.getClient()
    }

    companion object {
        private const val FOOD_ANALYSIS_PROMPT = """
You are a nutrition assistant. Analyze the food in this image.
1. Identify the main food items present.
2. Determine if the meal is generally healthy and balanced.
3. Classify the result as:
   - GOOD: Balanced meal with protein, healthy fats, and fiber/vegetables.
   - MODIFY: Mostly healthy but could be improved (e.g., missing a vegetable, too much of one component).
   - NOT_RECOMMENDED: Highly processed, lacks nutritional value, or very unbalanced.

4. Provide a concise, one-sentence reason for your choice.
5. If the classification is MODIFY, provide a specific, one-sentence suggestion for improvement. Otherwise, write NONE.

Strictly follow this output format:
ITEMS: [Comma-separated list of food items]
VERDICT: [GOOD, MODIFY, or NOT_RECOMMENDED]
REASON: [Your one-sentence explanation]
SUGGESTION: [Your suggestion or NONE]
"""
    }

    /**
     * Check whether Gemini Nano is available, downloadable, or unavailable.
     */
    suspend fun checkStatus(): Int {
        return generativeModel.checkStatus()
    }

    /**
     * Trigger download of Gemini Nano if it's in DOWNLOADABLE state.
     */
    suspend fun downloadModel() {
        generativeModel.download().first()
    }

    /**
     * Analyze a food photo on-device and return a structured result.
     */
    suspend fun analyzeFood(photo: Bitmap): AnalysisResult {
        val request = GenerateContentRequest.Builder(
            ImagePart(photo),
            TextPart(FOOD_ANALYSIS_PROMPT.trimIndent()),
        ).build()

        val response = generativeModel.generateContent(request)
        val responseText = response.candidates.firstOrNull()?.text ?: throw Exception("Empty response from model")

        return parseResponse(responseText)
    }

    /**
     * Parse the labeled plaintext response from Gemini Nano.
     */
    private fun parseResponse(raw: String): AnalysisResult {
        val sections = mutableMapOf<String, String>()
        var currentKey: String? = null

        raw.lines().forEach { line ->
            val colonIndex = line.indexOf(':')
            if (colonIndex > 0) {
                val potentialKey = line.substring(0, colonIndex).trim().uppercase()
                if (potentialKey in listOf("ITEMS", "VERDICT", "REASON", "SUGGESTION")) {
                    currentKey = potentialKey
                    sections[potentialKey] = line.substring(colonIndex + 1).trim()
                    return@forEach
                }
            }
            
            // Append to current section if it's a continuation line
            currentKey?.let { key ->
                if (line.isNotBlank()) {
                    val existing = sections[key] ?: ""
                    sections[key] = if (existing.isEmpty()) line.trim() else "$existing $line".trim()
                }
            }
        }

        val itemsStr = sections["ITEMS"] ?: ""
        val foodItems = itemsStr.split(",")
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        val verdictStr = sections["VERDICT"]?.uppercase() ?: ""
        val verdict = when {
            verdictStr.contains("NOT_RECOMMENDED") || verdictStr.contains("NOT RECOMMENDED") -> Verdict.NOT_RECOMMENDED
            verdictStr.contains("MODIFY") -> Verdict.MODIFY
            else -> Verdict.GOOD
        }

        val reason = sections["REASON"] ?: "No explanation provided."
        val suggestion = sections["SUGGESTION"]?.takeIf {
            (it.uppercase() != "NONE") && it.isNotBlank()
        } ?: ""

        return AnalysisResult(
            foodItems = foodItems,
            verdict = verdict,
            reason = reason,
            suggestion = suggestion,
            rawResponse = raw,
        )
    }
}
