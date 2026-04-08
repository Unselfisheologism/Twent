package com.ai.assistance.operit.voice.utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.Log
import com.ai.assistance.operit.services.automation.OperitAutomationService
import com.ai.assistance.operit.voice.api.Finger
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * Utility for capturing full-page content of lengthy screens that require scrolling.
 * Takes multiple screenshots while scrolling and combines them.
 */
class FullPageCapture private constructor(private val context: Context) {

    companion object {
        private const val TAG = "FullPageCapture"
        private const val SCROLL_AMOUNT = 800 // pixels per scroll
        private const val MAX_SCROLLS = 20 // maximum number of scrolls before stopping
        private const val SCROLL_DELAY_MS = 500L // delay after scroll for content to settle

        @Volatile private var INSTANCE: FullPageCapture? = null

        fun getInstance(context: Context): FullPageCapture {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: FullPageCapture(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val finger by lazy { Finger(context) }

    /**
     * Capture full page content by scrolling and taking multiple screenshots.
     * Returns a list of bitmap screenshots from top to bottom.
     */
    suspend fun captureFullPage(): List<Bitmap> {
        val screenshots = mutableListOf<Bitmap>()

        try {
            // Capture initial screen
            val initialScreenshot = captureCurrentScreen()
            if (initialScreenshot != null) {
                screenshots.add(initialScreenshot)
            }

            // Scroll and capture
            for (i in 0 until MAX_SCROLLS) {
                Log.d(TAG, "Scrolling down: ${i + 1}/$MAX_SCROLLS")
                finger.scrollDown(SCROLL_AMOUNT)
                delay(SCROLL_DELAY_MS)

                val screenshot = captureCurrentScreen()
                if (screenshot != null) {
                    // Check if we've reached the bottom (screenshot is similar to previous)
                    if (screenshots.isNotEmpty() && isSimilarContent(screenshots.last(), screenshot)) {
                        Log.d(TAG, "Reached bottom of page")
                        break
                    }
                    screenshots.add(screenshot)
                } else {
                    Log.w(TAG, "Failed to capture screenshot at scroll $i")
                    break
                }
            }

            Log.d(TAG, "Captured ${screenshots.size} screenshots for full page")
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing full page", e)
        }

        return screenshots
    }

    /**
     * Stitch multiple screenshots vertically into a single bitmap.
     */
    fun stitchScreenshots(screenshots: List<Bitmap>): Bitmap? {
        if (screenshots.isEmpty()) return null
        if (screenshots.size == 1) return screenshots.first()

        // Calculate total height
        val totalHeight = screenshots.sumOf { it.height }
        val width = screenshots.firstOrNull()?.width ?: return null

        val combinedBitmap = Bitmap.createBitmap(width, totalHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(combinedBitmap)
        val paint = Paint()

        var yOffset = 0
        for (screenshot in screenshots) {
            canvas.drawBitmap(screenshot, 0f, yOffset.toFloat(), paint)
            yOffset += screenshot.height
        }

        return combinedBitmap
    }

    /**
     * Capture the current screen using accessibility service screenshot.
     */
    private fun captureCurrentScreen(): Bitmap? {
        return try {
            val service = OperitAutomationService.instance
            if (service == null) {
                Log.e(TAG, "Accessibility service not available")
                return null
            }

            // Use the accessibility service to get the screen content
            val rootNode = service.rootInActiveWindow
            if (rootNode == null) {
                Log.e(TAG, "Root node not available")
                return null
            }

            // Create bitmap from the root node bounds
            val bounds = android.graphics.Rect()
            rootNode.getBoundsInScreen(bounds)

            val width = bounds.width()
            val height = bounds.height()

            // Use screencap command as fallback
            val tempFile = File(context.cacheDir, "temp_screenshot_${System.currentTimeMillis()}.png")
            val process = Runtime.getRuntime().exec("screencap -p ${tempFile.absolutePath}")
            process.waitFor()

            if (tempFile.exists()) {
                val bitmap = android.graphics.BitmapFactory.decodeFile(tempFile.absolutePath)
                tempFile.delete()
                bitmap
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing screen", e)
            null
        }
    }

    /**
     * Check if two bitmaps have similar content (to detect end of scrollable page).
     * Simple comparison using pixel difference percentage.
     */
    private fun isSimilarContent(bmp1: Bitmap, bmp2: Bitmap): Boolean {
        if (bmp1.width != bmp2.width || bmp1.height != bmp2.height) return false

        var similarPixels = 0
        val totalPixels = bmp1.width * bmp1.height

        for (x in 0 until bmp1.width) {
            for (y in 0 until bmp1.height) {
                if (bmp1.getPixel(x, y) == bmp2.getPixel(x, y)) {
                    similarPixels++
                }
            }
        }

        val similarity = similarPixels.toFloat() / totalPixels
        // If more than 85% similar, consider it same content (reached bottom)
        return similarity > 0.85f
    }

    /**
     * Convert bitmap to JPEG bytes for sending to LLM
     */
    fun bitmapToJpegBytes(bitmap: Bitmap, quality: Int = 80): ByteArray {
        val stream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, stream)
        return stream.toByteArray()
    }

    /**
     * Get text content from the current screen via accessibility service
     * This captures all visible text without needing screenshots
     */
    fun getCurrentScreenText(): String {
        return try {
            val service = OperitAutomationService.instance
            if (service == null) {
                Log.e(TAG, "Accessibility service not available")
                return "Accessibility service not available"
            }

            val rootNode = service.rootInActiveWindow
            if (rootNode == null) {
                Log.e(TAG, "Root node not available")
                return "Root node not available"
            }

            buildString {
                extractTextFromNode(rootNode, this, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting screen text", e)
            "Error extracting screen text: ${e.message}"
        }
    }

    /**
     * Recursively extract text from accessibility node
     */
    private fun extractTextFromNode(node: android.view.accessibility.AccessibilityNodeInfo, builder: StringBuilder, depth: Int) {
        try {
            // Get text content
            val text = node.text?.toString()?.trim()
            val contentDesc = node.contentDescription?.toString()?.trim()
            val className = node.className?.toString() ?: ""

            // Add text if present
            if (!text.isNullOrBlank()) {
                builder.append("  ".repeat(depth))
                builder.append(text)
                builder.append("\n")
            }

            // Add content description if present and different from text
            if (!contentDesc.isNullOrBlank() && contentDesc != text) {
                builder.append("  ".repeat(depth))
                builder.append("[Description: $contentDesc]")
                builder.append("\n")
            }

            // Recurse into children (limit depth to avoid too much output)
            if (depth < 10) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { child ->
                        extractTextFromNode(child, builder, depth + 1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting text from node", e)
        }
    }
}
