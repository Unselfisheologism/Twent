package com.ai.assistance.operit.ui.features.miniapp

import android.content.Context
import com.ai.assistance.operit.data.preferences.ModelConfigManager
import com.ai.assistance.operit.api.chat.enhance.MultiServiceManager
import com.ai.assistance.operit.data.model.FunctionType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Native bridge that allows mini-apps to call the AI model configured by the user.
 *
 * Mini-apps call this via: `window.OperitMiniApp.ai.sendMessage(prompt, images)`
 * where `images` is an array of base64-encoded image data URIs.
 */
class MiniAppAiBridge(private val context: Context) {

    companion object {
        private const val TAG = "MiniAppAiBridge"
    }

    private val modelConfigManager = ModelConfigManager(context)
    private val multiServiceManager = MultiServiceManager(context)

    /**
     * Result of an AI call.
     */
    data class AiResponse(
        val success: Boolean,
        val content: String,
        val error: String? = null,
        val requiresVision: Boolean = false
    )

    /**
     * Check if the user's configured model supports vision (image input).
     */
    suspend fun isVisionSupported(): Boolean {
        return try {
            multiServiceManager.hasImageRecognitionConfigured()
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to check vision capability", e)
            false
        }
    }

    /**
     * Get the name of the currently configured AI model.
     */
    suspend fun getModelName(): String {
        return try {
            val config = multiServiceManager.getModelConfigForFunction(FunctionType.CHAT)
            config.modelName.ifEmpty { "Unknown" }
        } catch (e: Exception) {
            "Unknown"
        }
    }

    /**
     * Send a message to the AI model.
     *
     * @param prompt The text prompt to send.
     * @param images Optional list of image data URIs (e.g. "data:image/png;base64,...").
     *               If provided, vision capability is checked first.
     * @return AiResponse with the AI's reply or an error.
     */
    suspend fun sendMessage(
        prompt: String,
        images: List<String> = emptyList()
    ): AiResponse {
        return try {
            // If images are provided, check vision capability first
            if (images.isNotEmpty()) {
                if (!isVisionSupported()) {
                    return AiResponse(
                        success = false,
                        content = "",
                        error = "VISION_REQUIRED",
                        requiresVision = true
                    )
                }
            }

            val config = multiServiceManager.getModelConfigForFunction(FunctionType.CHAT)
            val modelParameters = multiServiceManager.getModelParametersForFunction(FunctionType.CHAT)
            val aiService = multiServiceManager.getServiceForFunction(FunctionType.CHAT)

            // If no images, use the standard sendMessage
            if (images.isEmpty()) {
                val stream = aiService.sendMessage(
                    context = context,
                    message = prompt,
                    chatHistory = emptyList(),
                    modelParameters = modelParameters,
                    enableThinking = false,
                    stream = false
                )
                val sb = StringBuilder()
                stream.collect { chunk -> sb.append(chunk) }
                val content = sb.toString()
                AiResponse(success = true, content = content)
            } else {
                // With images, we need to build a message that includes them.
                // The AIService expects image links in the format:
                // <link type="image" id="...">base64data</link>
                // We build the message with inline image link tags.
                val imageLinks = images.mapIndexed { index, dataUri ->
                    val (mimeType, base64Data) = parseDataUri(dataUri)
                        ?: return AiResponse(success = false, content = "", error = "Invalid image data at index $index")
                    "<link type=\"image\" id=\"miniapp_img_$index\">$base64Data</link>"
                }.joinToString("\n")

                val messageWithImages = "$prompt\n$imageLinks"

                val stream = aiService.sendMessage(
                    context = context,
                    message = messageWithImages,
                    chatHistory = emptyList(),
                    modelParameters = modelParameters,
                    enableThinking = false,
                    stream = false
                )
                val sb = StringBuilder()
                stream.collect { chunk -> sb.append(chunk) }
                val content = sb.toString()
                AiResponse(success = true, content = content)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "AI call failed", e)
            AiResponse(success = false, content = "", error = e.message ?: "Unknown error")
        }
    }

    /**
     * Parse a data URI into MIME type and base64 data.
     * Expected format: "data:image/png;base64,iVBORw0KGgo..."
     */
    private fun parseDataUri(dataUri: String): Pair<String, String>? {
        val prefix = "data:"
        if (!dataUri.startsWith(prefix)) return null

        val commaIndex = dataUri.indexOf(',')
        if (commaIndex == -1) return null

        val header = dataUri.substring(prefix.length, commaIndex)
        val data = dataUri.substring(commaIndex + 1)

        val semicolonIndex = header.indexOf(';')
        if (semicolonIndex == -1) return null

        val mimeType = header.substring(0, semicolonIndex)
        val encoding = header.substring(semicolonIndex + 1)

        // Only support base64 encoding
        if (!encoding.equals("base64", ignoreCase = true)) return null

        return mimeType to data
    }
}
