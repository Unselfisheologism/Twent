package com.ai.assistance.operit.services.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Point
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.Xml
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.services.OperitAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlSerializer
import java.io.StringReader
import java.io.StringWriter
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class SimplifiedElement(
    val description: String,
    val bounds: Rect,
    val center: Point,
    val isClickable: Boolean,
    val className: String
)

data class RawScreenData(
    val rootNode: AccessibilityNodeInfo?,
    val activityName: String?,
    val pixelsAbove: Int,
    val pixelsBelow: Int,
    val screenWidth: Int,
    val screenHeight: Int
)

private data class BoundingBoxView(
    val boxView: View,
    val labelView: TextView
)

class OperitAutomationService : AccessibilityService() {

    companion object {
        var instance: OperitAutomationService? = null

        const val DEBUG_SHOW_TAPS = true
        const val DEBUG_SHOW_BOUNDING_BOXES = false
    }

    private var windowManager: WindowManager? = null
    private var statusBarHeight = -1
    private var currentActivityName: String? = null

    private val boundingBoxViews = mutableListOf<BoundingBoxView>()
    private var glowBorderView: View? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        OperitAccessibilityService.setInstance(this)
        this.windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        Log.d("OperitAutomation", "Accessibility Service connected.")
    }

    fun getForegroundAppPackageName(): String? {
        return rootInActiveWindow?.packageName?.toString()
    }

    fun showDebugTap(tapX: Float, tapY: Float) {
        if (!DEBUG_SHOW_TAPS) return

        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val overlayView = ImageView(this)

        val tapIndicator = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(0x80FF0000.toInt())
            setSize(100, 100)
            setStroke(4, 0xFFFF0000.toInt())
        }
        overlayView.setImageDrawable(tapIndicator)

        val params = WindowManager.LayoutParams(
            100, 100,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = tapX.toInt() - 50
            y = tapY.toInt() - 50
        }

        mainHandler.post {
            try {
                windowManager.addView(overlayView, params)
                mainHandler.postDelayed({
                    if (overlayView.isAttachedToWindow) windowManager.removeView(overlayView)
                }, 500L)
            } catch (e: Exception) {
                Log.e("OperitAutomation", "Failed to add debug tap view", e)
            }
        }
    }

    /**
     * Draws labeled bounding boxes for each element on the screen.
     */
    fun drawDebugBoundingBoxes(elements: List<SimplifiedElement>, durationMs: Long = 5000) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("OperitAutomation", "Cannot draw bounding boxes: 'Draw over other apps' permission not granted.")
            return
        }

        clearBoundingBoxes()

        if (statusBarHeight < 0) {
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            statusBarHeight = if (resourceId > 0) resources.getDimensionPixelSize(resourceId) else 0
        }

        val wm = this.windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager

        mainHandler.post {
            elements.forEach { element ->
                try {
                    val boxView = View(this).apply {
                        background = GradientDrawable().apply {
                            shape = GradientDrawable.RECTANGLE
                            val color = if (element.isClickable) 0xFF00FF00.toInt() else 0xFFFFFF00.toInt()
                            setStroke(4, color)
                        }
                    }
                    val boxParams = WindowManager.LayoutParams(
                        element.bounds.width(), element.bounds.height(),
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = element.bounds.left
                        y = element.bounds.top - statusBarHeight
                    }
                    wm.addView(boxView, boxParams)

                    val labelView = TextView(this).apply {
                        text = element.description
                        setBackgroundColor(0xAA000000.toInt())
                        setTextColor(0xFFFFFFFF.toInt())
                        textSize = 10f
                        setPadding(4, 2, 4, 2)
                    }
                    val labelParams = WindowManager.LayoutParams(
                        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
                        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                        PixelFormat.TRANSLUCENT
                    ).apply {
                        gravity = Gravity.TOP or Gravity.START
                        x = element.bounds.left
                        y = (element.bounds.top - 35).coerceAtLeast(0) - statusBarHeight
                    }
                    wm.addView(labelView, labelParams)

                    boundingBoxViews.add(BoundingBoxView(boxView, labelView))

                } catch (e: Exception) {
                    Log.e("OperitAutomation", "Failed to add debug bounding box for: ${element.description}", e)
                }
            }

            mainHandler.postDelayed({
                clearBoundingBoxes()
            }, durationMs)
        }
    }

    /**
     * Clears all bounding box overlays.
     */
    fun clearBoundingBoxes() {
        val wm = this.windowManager ?: return
        mainHandler.post {
            boundingBoxViews.forEach { (boxView, labelView) ->
                try {
                    if (boxView.isAttachedToWindow) wm.removeView(boxView)
                    if (labelView.isAttachedToWindow) wm.removeView(labelView)
                } catch (e: Exception) {
                    Log.e("OperitAutomation", "Error removing bounding box views", e)
                }
            }
            boundingBoxViews.clear()
        }
    }

    /**
     * Shows a glowing border around the entire screen.
     */
    fun showGlowBorder(durationMs: Long = 300) {
        if (!Settings.canDrawOverlays(this)) {
            Log.w("OperitAutomation", "Cannot show glow border: 'Draw over other apps' permission not granted.")
            return
        }

        clearGlowBorder()

        val wm = this.windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager

        mainHandler.post {
            try {
                val borderView = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        setColor(Color.TRANSPARENT)
                        setStroke(8, Color.WHITE)
                    }
                }

                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                )

                wm.addView(borderView, params)
                glowBorderView = borderView

                mainHandler.postDelayed({
                    clearGlowBorder()
                }, durationMs)

            } catch (e: Exception) {
                Log.e("OperitAutomation", "Failed to show glow border", e)
            }
        }
    }

    /**
     * Clears the glow border overlay.
     */
    fun clearGlowBorder() {
        val wm = this.windowManager ?: return
        val view = glowBorderView ?: return
        mainHandler.post {
            if (view.isAttachedToWindow) {
                try {
                    wm.removeView(view)
                } catch (e: Exception) {
                    Log.e("OperitAutomation", "Error removing glow border", e)
                }
            }
            glowBorderView = null
        }
    }

    /**
     * Shows tap indicator at coordinates with animation.
     */
    fun showTapIndicator(x: Int, y: Int, durationMs: Long = 1000) {
        if (!Settings.canDrawOverlays(this)) return

        val wm = this.windowManager ?: getSystemService(WINDOW_SERVICE) as WindowManager

        mainHandler.post {
            try {
                val tapView = View(this).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(0x802196F3.toInt())
                        setStroke(4, 0xFF2196F3.toInt())
                    }
                }

                val params = WindowManager.LayoutParams(
                    80, 80,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                    this.x = x - 40
                    this.y = y - 40
                }

                wm.addView(tapView, params)

                mainHandler.postDelayed({
                    if (tapView.isAttachedToWindow) wm.removeView(tapView)
                }, durationMs)

            } catch (e: Exception) {
                Log.e("OperitAutomation", "Failed to show tap indicator", e)
            }
        }
    }

    private fun parseXmlToSimplifiedElements(xmlString: String): List<SimplifiedElement> {
        val allElements = mutableListOf<SimplifiedElement>()
        try {
            val parser: XmlPullParser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(StringReader(xmlString))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "node") {
                    val boundsString = parser.getAttributeValue(null, "bounds")
                    val bounds = try {
                        val numbers = boundsString?.replace(Regex("[\\[\\]]"), ",")?.split(",")?.filter { it.isNotEmpty() }
                        if (numbers != null && numbers.size == 4) {
                            Rect(numbers[0].toInt(), numbers[1].toInt(), numbers[2].toInt(), numbers[3].toInt())
                        } else {
                            Rect()
                        }
                    } catch (e: Exception) { Rect() }

                    if (bounds.width() <= 0 || bounds.height() <= 0) {
                        eventType = parser.next()
                        continue
                    }

                    val isClickable = parser.getAttributeValue(null, "clickable") == "true"
                    val text = parser.getAttributeValue(null, "text")
                    val contentDesc = parser.getAttributeValue(null, "content-desc")
                    val resourceId = parser.getAttributeValue(null, "resource-id")
                    val className = parser.getAttributeValue(null, "class") ?: "Element"

                    if (isClickable || !text.isNullOrEmpty() || (contentDesc != null && contentDesc != "null" && contentDesc.isNotEmpty())) {
                        val description = when {
                            !contentDesc.isNullOrEmpty() && contentDesc != "null" -> contentDesc
                            !text.isNullOrEmpty() -> text
                            !resourceId.isNullOrEmpty() -> resourceId.substringAfterLast('/')
                            else -> ""
                        }
                        if (description.isNotEmpty()) {
                            val center = Point(bounds.centerX(), bounds.centerY())
                            allElements.add(SimplifiedElement(description, bounds, center, isClickable, className))
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("OperitAutomation", "Error parsing XML for simplified elements", e)
        }
        return allElements
    }

    private fun formatElementsForLlm(elements: List<SimplifiedElement>): String {
        if (elements.isEmpty()) {
            return "No interactable or textual elements found on the screen."
        }
        val elementStrings = elements.map {
            val action = if (it.isClickable) "Action: Clickable" else "Action: Not-Clickable (Text only)"
            val elementType = it.className.substringAfterLast('.')
            "- $elementType: \"${it.description}\" | $action | Center: (${it.center.x}, ${it.center.y})"
        }
        return "Interactable Screen Elements:\n" + elementStrings.joinToString("\n")
    }

    fun getWindowHierarchySignature(): String {
        val rootNode = rootInActiveWindow ?: return "null_root"

        val stringWriter = StringWriter()
        try {
            val serializer: XmlSerializer = Xml.newSerializer()
            serializer.setOutput(stringWriter)
            serializer.startDocument("UTF-8", true)
            serializer.startTag(null, "hierarchy")
            dumpNode(rootNode, serializer, 0)
            serializer.endTag(null, "hierarchy")
            serializer.endDocument()
            return stringWriter.toString()
        } catch (e: Exception) {
            Log.e("OperitAutomation", "Error generating signature", e)
            return "error_generating_signature"
        }
    }

    suspend fun getAllScreenAnalysisData(): RawScreenData {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val maxRetries = 5
        val retryDelay = 200L
        val ownPackageName = packageName

        for (attempt in 1..maxRetries) {
            val allWindows = windows
            // Filter out our own app's windows (floating overlay) to get the actual screen content
            val targetWindow = allWindows
                .filter { it.type == TYPE_APPLICATION }
                .filter { 
                    // Exclude windows from our own package to avoid capturing the floating overlay
                    val windowPackageName = it.root?.packageName?.toString()
                    windowPackageName != ownPackageName
                }
                .maxByOrNull {
                    val bounds = Rect()
                    it.getBoundsInScreen(bounds)
                    bounds.width() * bounds.height()
                }

            // Fallback to any application window if no non-own window found
            val fallbackWindow = if (targetWindow == null) {
                allWindows
                    .filter { it.type == TYPE_APPLICATION }
                    .maxByOrNull {
                        val bounds = Rect()
                        it.getBoundsInScreen(bounds)
                        bounds.width() * bounds.height()
                    }
            } else null

            val rootNode = targetWindow?.root ?: fallbackWindow?.root ?: rootInActiveWindow

            if (rootNode != null) {
                Log.d("OperitAutomation", "Analyzing window: ${rootNode.packageName}")
                val (pixelsAbove, pixelsBelow) = findScrollableNodeAndGetInfo(rootNode)
                return RawScreenData(rootNode, rootNode.packageName?.toString(), pixelsAbove, pixelsBelow, screenWidth, screenHeight)
            }

            if (attempt < maxRetries) {
                delay(retryDelay)
            }
        }

        Log.e("OperitAutomation", "Failed to get any valid root node after $maxRetries attempts.")
        return RawScreenData(null, null, 0, 0, screenWidth, screenHeight)
    }

    suspend fun dumpWindowHierarchy(pureXML: Boolean = false): String {
        return withContext(Dispatchers.Default) {
            val ownPackageName = packageName
            
            // Try to get a window that is not our own floating overlay
            val allWindows = windows
            val targetWindow = allWindows
                .filter { it.type == TYPE_APPLICATION }
                .filter { 
                    val windowPackageName = it.root?.packageName?.toString()
                    windowPackageName != ownPackageName
                }
                .maxByOrNull {
                    val bounds = Rect()
                    it.getBoundsInScreen(bounds)
                    bounds.width() * bounds.height()
                }
            
            // Use the target window if found, otherwise fall back to rootInActiveWindow
            val rootNode = targetWindow?.root ?: rootInActiveWindow ?: run {
                Log.e("OperitAutomation", "Root node is null, cannot dump hierarchy.")
                return@withContext "Error: UI hierarchy is not available."
            }

            // Log which package we're analyzing
            Log.d("OperitAutomation", "Dumping hierarchy for package: ${rootNode.packageName}")

            val stringWriter = StringWriter()
            try {
                val serializer: XmlSerializer = Xml.newSerializer()
                serializer.setOutput(stringWriter)
                serializer.startDocument("UTF-8", true)
                serializer.startTag(null, "hierarchy")
                dumpNode(rootNode, serializer, 0)
                serializer.endTag(null, "hierarchy")
                serializer.endDocument()

                val rawXml = stringWriter.toString()
                val simplifiedElements = parseXmlToSimplifiedElements(rawXml)

                if (DEBUG_SHOW_BOUNDING_BOXES) {
                    // Debug bounding boxes - disabled for now
                }

                if (pureXML) {
                    return@withContext rawXml
                }
                return@withContext formatElementsForLlm(simplifiedElements)

            } catch (e: Exception) {
                Log.e("OperitAutomation", "Error dumping or transforming UI hierarchy", e)
                return@withContext "Error processing UI."
            }
        }
    }

    private fun dumpNode(node: android.view.accessibility.AccessibilityNodeInfo?, serializer: XmlSerializer, index: Int) {
        if (node == null) return

        serializer.startTag(null, "node")
        serializer.attribute(null, "index", index.toString())
        serializer.attribute(null, "text", node.text?.toString() ?: "")
        serializer.attribute(null, "resource-id", node.viewIdResourceName ?: "")
        serializer.attribute(null, "class", node.className?.toString() ?: "")
        serializer.attribute(null, "package", node.packageName?.toString() ?: "")
        serializer.attribute(null, "content-desc", node.contentDescription?.toString() ?: "")
        serializer.attribute(null, "checkable", node.isCheckable.toString())
        serializer.attribute(null, "checked", node.isChecked.toString())
        serializer.attribute(null, "clickable", node.isClickable.toString())
        serializer.attribute(null, "enabled", node.isEnabled.toString())
        serializer.attribute(null, "focusable", node.isFocusable.toString())
        serializer.attribute(null, "focused", node.isFocused.toString())
        serializer.attribute(null, "scrollable", node.isScrollable.toString())
        serializer.attribute(null, "long-clickable", node.isLongClickable.toString())
        serializer.attribute(null, "password", node.isPassword.toString())
        serializer.attribute(null, "selected", node.isSelected.toString())

        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        serializer.attribute(null, "bounds", bounds.toShortString())

        for (i in 0 until node.childCount) {
            dumpNode(node.getChild(i), serializer, i)
        }

        serializer.endTag(null, "node")
    }

    fun logLongString(tag: String, message: String) {
        val maxLogSize = 2000
        for (i in 0..message.length / maxLogSize) {
            val start = i * maxLogSize
            var end = (i + 1) * maxLogSize
            end = if (end > message.length) message.length else end
            Log.d(tag, message.substring(start, end))
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageName = event.packageName?.toString()
            val className = event.className?.toString()

            if (!packageName.isNullOrBlank() && !className.isNullOrBlank()) {
                this.currentActivityName = ComponentName(packageName, className).flattenToString()
                Log.d("OperitAutomation", "Current Activity Updated: $currentActivityName")
            }
        }
    }

    fun getCurrentActivityName(): String {
        return this.currentActivityName ?: "Unknown"
    }

    override fun onInterrupt() {
        Log.e("OperitAutomation", "Accessibility Service interrupted.")
    }

    override fun onDestroy() {
        super.onDestroy()
        clearBoundingBoxes()
        clearGlowBorder()
        instance = null
        Log.d("OperitAutomation", "Accessibility Service destroyed.")
    }

    fun isTypingAvailable(): Boolean {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        return focusedNode != null && focusedNode.isEditable && focusedNode.isEnabled
    }

    fun clickOnPoint(x: Float, y: Float) {
        if (DEBUG_SHOW_TAPS) {
            showDebugTap(x, y)
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 10))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun longClickOnPoint(x: Float, y: Float) {
        if (DEBUG_SHOW_TAPS) {
            showDebugTap(x, y)
        }

        val path = Path().apply {
            moveTo(x, y)
        }
        val longPressStroke = GestureDescription.StrokeDescription(path, 0, 2000L)

        val gesture = GestureDescription.Builder()
            .addStroke(longPressStroke)
            .build()

        dispatchGesture(gesture, null, null)
    }

    fun scrollDownPrecisely(pixels: Int, pixelsPerSecond: Int = 1000) {
        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val x = screenWidth / 2
        val y1 = (screenHeight * 0.8).toInt()
        val y2 = (y1 - pixels).coerceAtLeast(0)

        val distance = y1 - y2
        if (distance <= 0) {
            Log.w("Scroll", "Scroll distance is zero or negative. Aborting.")
            return
        }

        val duration = (distance.toFloat() / pixelsPerSecond * 1000).toInt()
        Log.d("Scroll", "Scrolling down by $pixels pixels: swipe from ($x, $y1) to ($x, $y2) over $duration ms")
        swipe(x.toFloat(), y1.toFloat(), x.toFloat(), y2.toFloat(), duration.toLong())
    }

    fun typeTextInFocusedField(textToType: String) {
        val focusedNode = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)

        if (focusedNode != null && focusedNode.isEditable) {
            val arguments = Bundle()
            val existingText = ""
            val newText = existingText.toString() + textToType

            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            focusedNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        } else {
            Log.e("OperitAutomation", "Could not find a focused editable field to type in.")
        }
    }

    fun performBack() {
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun performHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
    }

    fun performRecents() {
        performGlobalAction(GLOBAL_ACTION_RECENTS)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    fun performEnter() {
        val rootNode: AccessibilityNodeInfo? = rootInActiveWindow
        if (rootNode == null) {
            Log.e("OperitAutomation", "Cannot perform Enter: rootInActiveWindow is null.")
            return
        }

        val focusedNode = rootNode.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focusedNode == null) {
            Log.w("OperitAutomation", "Could not find a focused input node to perform 'Enter' on.")
            return
        }

        try {
            val supportedActions = focusedNode.actionList
            val imeAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER

            if (supportedActions.contains(imeAction)) {
                Log.d("OperitAutomation", "Attempting primary action: ACTION_IME_ENTER")
                val success = focusedNode.performAction(imeAction.id)
                if (success) {
                    Log.d("OperitAutomation", "Successfully performed ACTION_IME_ENTER.")
                    return
                }
                Log.w("OperitAutomation", "ACTION_IME_ENTER was supported but failed to execute. Proceeding to fallback.")
            }

            Log.w("OperitAutomation", "ACTION_IME_ENTER not available or failed. Trying ACTION_CLICK as a fallback.")
            val clickAction = AccessibilityNodeInfo.AccessibilityAction.ACTION_CLICK
            if (supportedActions.contains(clickAction)) {
                val success = focusedNode.performAction(clickAction.id)
                if (success) {
                    Log.d("OperitAutomation", "Fallback ACTION_CLICK succeeded.")
                } else {
                    Log.e("OperitAutomation", "Fallback ACTION_CLICK also failed.")
                }
            } else {
                Log.e("OperitAutomation", "No supported 'Enter' or 'Click' action was found on the focused node.")
            }

        } catch (e: Exception) {
            Log.e("OperitAutomation", "Exception while trying to perform Enter action", e)
        } finally {
            focusedNode.recycle()
        }
    }

    private fun findScrollableNodeAndGetInfo(rootNode: AccessibilityNodeInfo?): Pair<Int, Int> {
        if (rootNode == null) return Pair(0, 0)

        val queue: ArrayDeque<AccessibilityNodeInfo> = ArrayDeque()
        queue.addLast(rootNode)

        var bestNode: AccessibilityNodeInfo? = null
        var maxNodeSize = -1

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isScrollable) {
                val rect = android.graphics.Rect()
                node.getBoundsInScreen(rect)
                val size = rect.width() * rect.height()
                if (size > maxNodeSize) {
                    maxNodeSize = size
                    bestNode = node
                }
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.addLast(it) }
            }
        }

        var pixelsAbove = 0
        var pixelsBelow = 0

        bestNode?.let {
            val rangeInfo = it.rangeInfo
            if (rangeInfo != null) {
                pixelsAbove = (rangeInfo.current - rangeInfo.min).toInt()
                pixelsBelow = (rangeInfo.max - rangeInfo.current).toInt()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                pixelsAbove = 10
                pixelsBelow = 5.coerceAtLeast(0)
            }
            it.recycle()
        }

        return Pair(pixelsAbove, pixelsBelow)
    }

    private fun getScreenDimensions(): Pair<Int, Int> {
        val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val metrics = windowManager.currentWindowMetrics
            val width = metrics.bounds.width()
            val height = metrics.bounds.height()
            Pair(width, height)
        } else {
            val display = windowManager.defaultDisplay
            val displayMetrics = android.util.DisplayMetrics()
            display.getRealMetrics(displayMetrics)
            val width = displayMetrics.widthPixels
            val height = displayMetrics.heightPixels
            Pair(width, height)
        }
    }

    suspend fun getScreenAnalysisData(): RawScreenData {
        val (screenWidth, screenHeight) = getScreenDimensions()
        val maxRetries = 5
        val retryDelay = 800L
        val ownPackageName = packageName

        for (attempt in 1..maxRetries) {
            // Try to get a window that is not our own floating overlay
            val allWindows = windows
            val targetWindow = allWindows
                .filter { it.type == TYPE_APPLICATION }
                .filter { 
                    val windowPackageName = it.root?.packageName?.toString()
                    windowPackageName != ownPackageName
                }
                .maxByOrNull {
                    val bounds = Rect()
                    it.getBoundsInScreen(bounds)
                    bounds.width() * bounds.height()
                }
            
            val rootNode = targetWindow?.root ?: rootInActiveWindow

            if (rootNode != null) {
                Log.d("OperitAutomation", "Got root node on attempt $attempt. Package: ${rootNode.packageName}")
                val (pixelsAbove, pixelsBelow) = findScrollableNodeAndGetInfo(rootNode)
                val activityName = rootNode.packageName?.toString()
                return RawScreenData(rootNode, activityName, pixelsAbove, pixelsBelow, screenWidth, screenHeight)
            }

            if (attempt < maxRetries) {
                Log.d("OperitAutomation", "root node is null on attempt $attempt. Retrying in ${retryDelay}ms...")
                delay(retryDelay)
            }
        }

        Log.e("OperitAutomation", "Failed to get root node after $maxRetries attempts.")
        return RawScreenData(null, null, 0, 0, screenWidth, screenHeight)
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun captureScreenshot(): Bitmap? {
        // Hide floating window before capture to avoid overlay in screenshot
        val floatingService = com.ai.assistance.operit.services.FloatingChatService.getInstance()
        val wasFloatingVisible = floatingService != null
        
        try {
            // Temporarily hide the floating window
            if (wasFloatingVisible) {
                floatingService?.setFloatingWindowVisible(false)
                // Small delay to ensure the window is hidden before capture
                delay(100)
            }
            
            return suspendCancellableCoroutine { continuation ->
                val executor = ContextCompat.getMainExecutor(this)

                takeScreenshot(
                    Display.DEFAULT_DISPLAY,
                    executor,
                    object : TakeScreenshotCallback {
                        override fun onSuccess(screenshotResult: ScreenshotResult) {
                            val hardwareBuffer = screenshotResult.hardwareBuffer

                            if (hardwareBuffer == null) {
                                continuation.resumeWithException(Exception("Screenshot hardware buffer was null."))
                                return
                            }

                            val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false)

                            hardwareBuffer.close()

                            if (bitmap != null) {
                                continuation.resume(bitmap)
                            } else {
                                continuation.resumeWithException(Exception("Failed to wrap hardware buffer into a Bitmap."))
                            }
                        }

                        override fun onFailure(errorCode: Int) {
                            continuation.resumeWithException(Exception("Screenshot failed with error code: $errorCode"))
                        }
                    }
                )
            }
        } catch (e: Exception) {
            Log.e("ScreenshotUtil", "Screenshot capture failed", e)
            null
        } finally {
            // Restore floating window visibility
            if (wasFloatingVisible) {
                try {
                    floatingService?.setFloatingWindowVisible(true)
                } catch (e: Exception) {
                    Log.e("ScreenshotUtil", "Failed to restore floating window visibility", e)
                }
            }
        }
    }

    fun getUIHierarchyXml(): String {
        return getWindowHierarchySignature()
    }

    fun performClickAt(x: Int, y: Int): Boolean {
        clickOnPoint(x.toFloat(), y.toFloat())
        return true
    }

    fun performLongPressAt(x: Int, y: Int): Boolean {
        longClickOnPoint(x.toFloat(), y.toFloat())
        return true
    }

    fun performSwipeGesture(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
        return true
    }

    fun getCurrentActivity(): String {
        return getCurrentActivityName()
    }
}

data class InteractableElement(
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val className: String?,
    val bounds: android.graphics.Rect,
    val node: android.view.accessibility.AccessibilityNodeInfo
) {
    fun getCenter(): android.graphics.Point {
        return android.graphics.Point(bounds.centerX(), bounds.centerY())
    }
}