package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.api.voice.VoiceService
import com.ai.assistance.operit.api.voice.VoiceServiceFactory
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.model.ToolValidationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tool for Text-to-Speech functionality. This provides the ability to convert text to speech
 * using the configured TTS engine.
 */
class StandardTextToSpeechToolExecutor(private val context: Context) {

    companion object {
        private const val TAG = "TextToSpeechToolExecutor"
    }

    private val voiceService: VoiceService by lazy {
        VoiceServiceFactory.getInstance(context)
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

                val text = tool.parameters.find { it.name == "text" }?.value ?: ""
                val rate = tool.parameters.find { it.name == "rate" }?.value?.toFloatOrNull()
                val pitch = tool.parameters.find { it.name == "pitch" }?.value?.toFloatOrNull()
                val voiceId = tool.parameters.find { it.name == "voice_id" }?.value

                // Initialize the TTS service if not already initialized
                if (!voiceService.isInitialized) {
                    val initialized = voiceService.initialize()
                    if (!initialized) {
                        return@withContext ToolResult(
                            toolName = tool.name,
                            success = false,
                            result = StringResultData(""),
                            error = "Failed to initialize TTS service"
                        )
                    }
                }

                // Set voice if provided
                if (!voiceId.isNullOrBlank()) {
                    voiceService.setVoice(voiceId)
                }

                // Speak the text
                val success = voiceService.speak(
                    text = text,
                    interrupt = true,
                    rate = rate,
                    pitch = pitch
                )

                if (success) {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("Text spoken successfully: $text")
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Failed to speak text"
                    )
                }
            } catch (e: Exception) {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "TTS error: ${e.message}"
                )
            }
        }
    }

    fun validateParameters(tool: AITool): ToolValidationResult {
        val text = tool.parameters.find { it.name == "text" }?.value

        if (text.isNullOrBlank()) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Missing required parameter: text"
            )
        }

        // Validate rate if provided
        val rate = tool.parameters.find { it.name == "rate" }?.value
        if (rate != null && rate.toFloatOrNull() == null) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Invalid rate parameter: must be a number"
            )
        }

        // Validate pitch if provided
        val pitch = tool.parameters.find { it.name == "pitch" }?.value
        if (pitch != null && pitch.toFloatOrNull() == null) {
            return ToolValidationResult(
                valid = false,
                errorMessage = "Invalid pitch parameter: must be a number"
            )
        }

        return ToolValidationResult(valid = true)
    }

    /**
     * Get available voices from the TTS service
     */
    suspend fun getAvailableVoices(): List<VoiceService.Voice> {
        return withContext(Dispatchers.IO) {
            try {
                if (!voiceService.isInitialized) {
                    voiceService.initialize()
                }
                voiceService.getAvailableVoices()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
