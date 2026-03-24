package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.speech.SpeechService
import com.ai.assistance.operit.api.speech.SpeechServiceFactory
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Tool for Speech-to-Text functionality. This provides the ability to convert speech to text
 * using the configured speech recognition engine.
 */
class StandardSpeechToTextToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "SpeechToTextToolExecutor"
        private const val DEFAULT_TIMEOUT_MS = 10000L // 10 seconds timeout
    }

    private val speechService: SpeechService by lazy {
        SpeechServiceFactory.getInstance(context)
    }

    suspend fun invoke(tool: AITool): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                // Validate parameters
                val validationResult = validateParameters(tool)
                if (!validationResult.valid) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = validationResult.errorMessage
                    )
                }

                val languageCode = tool.parameters.find { it.name == "language_code" }?.value ?: "zh-CN"
                val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value?.toLongOrNull() 
                    ?: DEFAULT_TIMEOUT_MS
                val maxResults = tool.parameters.find { it.name == "max_results" }?.value?.toIntOrNull() ?: 1

                // Initialize the speech service if not already initialized
                if (!speechService.isInitialized.value) {
                    val initialized = speechService.initialize()
                    if (!initialized) {
                        return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to initialize speech recognition service"
                        )
                    }
                }

                // Start recognition
                val started = speechService.startRecognition(
                    languageCode = languageCode,
                    continuousMode = false,
                    partialResults = false
                )

                if (!started) {
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to start speech recognition"
                    )
                }

                // Wait for result with timeout
                val result = try {
                    val recognitionResult = speechService.recognitionResultFlow.first { it.isFinal }
                    recognitionResult.text
                } catch (e: Exception) {
                    // Timeout or error
                    speechService.cancelRecognition()
                    return@withContext ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Speech recognition timeout or error: ${e.message}"
                    )
                }

                // Stop recognition
                speechService.stopRecognition()

                if (result.isNotBlank()) {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("Recognized text: $result")
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "No speech recognized"
                    )
                }
            } catch (e: Exception) {
                // Make sure to clean up
                try {
                    speechService.cancelRecognition()
                } catch (ignored: Exception) {}
                
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Speech recognition error: ${e.message}"
                )
            }
        }
    }

    fun validateParameters(tool: AITool): ToolValidationResult {
        // All parameters are optional, so just validate they are correct types if provided
        val timeoutMs = tool.parameters.find { it.name == "timeout_ms" }?.value
        if (timeoutMs != null && timeoutMs.toLongOrNull() == null) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Invalid timeout_ms parameter: must be a number"
            )
        }

        val maxResults = tool.parameters.find { it.name == "max_results" }?.value
        if (maxResults != null && maxResults.toIntOrNull() == null) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Invalid max_results parameter: must be a number"
            )
        }

        return ToolValidationResult(valid = true)
    }

    /**
     * Get supported languages from the speech recognition service
     */
    suspend fun getSupportedLanguages(): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                if (!speechService.isInitialized.value) {
                    speechService.initialize()
                }
                speechService.getSupportedLanguages()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
