package com.ai.assistance.operit.core.tools.defaultTool.accessbility

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.SimplifiedUINode
import com.ai.assistance.operit.core.tools.UIPageResultData
import com.ai.assistance.operit.core.tools.defaultTool.standard.StandardUITools
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.services.FloatingChatService
import com.ai.assistance.operit.services.automation.OperitAutomationService
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.delay

open class AccessibilityUITools(context: Context) : StandardUITools(context) {

    private val TAG = "AccessibilityUITools"
    private val mainHandler = Handler(Looper.getMainLooper())

    private val service: OperitAutomationService?
        get() = OperitAutomationService.instance

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun getPageInfo(tool: AITool): ToolResult {
        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        // Hide floating window before capturing screen info
        val floatingService = FloatingChatService.getInstance()
        val wasFloatingVisible = floatingService != null
        if (wasFloatingVisible) {
            try {
                floatingService?.setFloatingWindowVisible(false)
                delay(100)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide floating window", e)
            }
        }

        return try {
            val rawData = svc.getScreenAnalysisData()
            val rootNode = rawData.rootNode

            if (rootNode == null) {
                return ToolResult(toolName = tool.name, success = true, result = UIPageResultData("No content", "Unknown", emptyList()))
            }

            val nodes = mutableListOf<SimplifiedUINode>()
            extractNodes(rootNode, nodes, 0)

            val simplifiedNodes = nodes.take(50)

            ToolResult(toolName = tool.name, success = true, result = UIPageResultData(rawData.activityName ?: "Unknown", rawData.activityName ?: "Unknown", simplifiedNodes))
        } catch (e: Exception) {
            AppLogger.e(TAG, "getPageInfo failed", e)
            ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        } finally {
            // Restore floating window visibility
            if (wasFloatingVisible) {
                try {
                    floatingService?.setFloatingWindowVisible(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore floating window visibility", e)
                }
            }
        }
    }

    private fun extractNodes(node: AccessibilityNodeInfo?, list: MutableList<SimplifiedUINode>, depth: Int) {
        if (node == null || depth > 10) return

        val uiNode = SimplifiedUINode(
            className = node.className?.toString(),
            text = node.text?.toString(),
            contentDesc = node.contentDescription?.toString(),
            resourceId = node.viewIdResourceName,
            bounds = getNodeBounds(node)?.toShortString(),
            isClickable = node.isClickable || node.isLongClickable,
            children = emptyList()
        )

        list.add(uiNode)

        for (i in 0 until node.childCount) {
            extractNodes(node.getChild(i), list, depth + 1)
        }
    }

    private fun getNodeBounds(node: AccessibilityNodeInfo): Rect? {
        val rect = Rect()
        node.getBoundsInScreen(rect)
        return if (rect.width() > 0 && rect.height() > 0) rect else null
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun tap(tool: AITool): ToolResult {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull() ?: return missingParam("x")
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull() ?: return missingParam("y")

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        try {
            svc.showTapIndicator(x, y)
            svc.clickOnPoint(x.toFloat(), y.toFloat())
            AppLogger.d(TAG, "tap at ($x, $y)")
            return ToolResult(toolName = tool.name, success = true, result = StringResultData("Tapped at ($x, $y)"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "tap failed", e)
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun longPress(tool: AITool): ToolResult {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull() ?: return missingParam("x")
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull() ?: return missingParam("y")
        val durationMs = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 1500L

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        try {
            svc.longClickOnPoint(x.toFloat(), y.toFloat())
            AppLogger.d(TAG, "longPress at ($x, $y) for ${durationMs}ms")
            return ToolResult(toolName = tool.name, success = true, result = StringResultData("Long pressed at ($x, $y)"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "longPress failed", e)
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun clickElement(tool: AITool): ToolResult {
        val index = tool.parameters.find { it.name == "index" }?.value?.toIntOrNull()
        val text = tool.parameters.find { it.name == "text" }?.value
        val contentDesc = tool.parameters.find { it.name == "content_description" }?.value

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        return try {
            val rootNode = svc.getScreenAnalysisData().rootNode
            val targetNode = findNode(rootNode, index, text, contentDesc)

            if (targetNode != null) {
                val bounds = Rect()
                targetNode.getBoundsInScreen(bounds)
                val centerX = bounds.centerX()
                val centerY = bounds.centerY()

                svc.showTapIndicator(centerX, centerY)
                svc.clickOnPoint(centerX.toFloat(), centerY.toFloat())

                targetNode.recycle()
                return ToolResult(toolName = tool.name, success = true, result = StringResultData("Clicked element"))
            } else {
                return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Element not found")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "clickElement failed", e)
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        }
    }

    private fun findNode(root: AccessibilityNodeInfo?, index: Int?, text: String?, contentDesc: String?): AccessibilityNodeInfo? {
        if (root == null) return null

        if (index != null) {
            var currentIndex = 0
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)

            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (isInteractive(node)) {
                    if (currentIndex == index) return node
                    currentIndex++
                }
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            }
        } else if (text != null || contentDesc != null) {
            val queue = ArrayDeque<AccessibilityNodeInfo>()
            queue.add(root)

            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val nodeText = node.text?.toString()
                val nodeDesc = node.contentDescription?.toString()

                if ((text != null && nodeText?.contains(text, ignoreCase = true) == true) ||
                    (contentDesc != null && nodeDesc?.contains(contentDesc, ignoreCase = true) == true)) {
                    return node
                }

                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.addLast(it) }
                }
            }
        }

        return null
    }

    private fun isInteractive(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable || node.isLongClickable || !node.text.isNullOrEmpty() || !node.contentDescription.isNullOrEmpty()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun setInputText(tool: AITool): ToolResult {
        val text = tool.parameters.find { it.name == "text" }?.value ?: return missingParam("text")

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        try {
            svc.typeTextInFocusedField(text)
            AppLogger.d(TAG, "typed text: $text")
            return ToolResult(toolName = tool.name, success = true, result = StringResultData("Typed: $text"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "setInputText failed", e)
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun pressKey(tool: AITool): ToolResult {
        val key = tool.parameters.find { it.name == "key" }?.value ?: return missingParam("key")

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        return try {
            when (key.lowercase()) {
                "back" -> { svc.performBack(); ToolResult(toolName = tool.name, success = true, result = StringResultData("Pressed back")) }
                "home" -> { svc.performHome(); ToolResult(toolName = tool.name, success = true, result = StringResultData("Pressed home")) }
                "enter", "return" -> { svc.performEnter(); ToolResult(toolName = tool.name, success = true, result = StringResultData("Pressed enter")) }
                "recents", "switch" -> { svc.performRecents(); ToolResult(toolName = tool.name, success = true, result = StringResultData("Opened recents")) }
                else -> ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Unknown key: $key")
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "pressKey failed", e)
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun swipe(tool: AITool): ToolResult {
        val startX = tool.parameters.find { it.name == "start_x" }?.value?.toIntOrNull() ?: return missingParam("start_x")
        val startY = tool.parameters.find { it.name == "start_y" }?.value?.toIntOrNull() ?: return missingParam("start_y")
        val endX = tool.parameters.find { it.name == "end_x" }?.value?.toIntOrNull() ?: return missingParam("end_x")
        val endY = tool.parameters.find { it.name == "end_y" }?.value?.toIntOrNull() ?: return missingParam("end_y")
        val duration = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 500L

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        try {
            svc.swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
            AppLogger.d(TAG, "swiped from ($startX, $startY) to ($endX, $endY)")
            return ToolResult(toolName = tool.name, success = true, result = StringResultData("Swiped"))
        } catch (e: Exception) {
            AppLogger.e(TAG, "swipe failed", e)
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        }
    }

    override suspend fun swipeLeft(tool: AITool): ToolResult {
        val pixels = tool.parameters.find { it.name == "pixels" }?.value?.toIntOrNull() ?: 500
        val duration = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 500L

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val startX = (screenWidth * 0.8).toInt()
        val startY = screenHeight / 2
        val endX = startX - pixels
        val endY = startY

        svc.swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
        return ToolResult(toolName = tool.name, success = true, result = StringResultData("Swiped left $pixels pixels"))
    }

    override suspend fun swipeRight(tool: AITool): ToolResult {
        val pixels = tool.parameters.find { it.name == "pixels" }?.value?.toIntOrNull() ?: 500
        val duration = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 500L

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val startX = (screenWidth * 0.2).toInt()
        val startY = screenHeight / 2
        val endX = startX + pixels
        val endY = startY

        svc.swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
        return ToolResult(toolName = tool.name, success = true, result = StringResultData("Swiped right $pixels pixels"))
    }

    override suspend fun swipeUp(tool: AITool): ToolResult {
        val pixels = tool.parameters.find { it.name == "pixels" }?.value?.toIntOrNull() ?: 500
        val duration = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 500L

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val startX = screenWidth / 2
        val startY = (screenHeight * 0.8).toInt()
        val endX = startX
        val endY = startY - pixels

        svc.swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
        return ToolResult(toolName = tool.name, success = true, result = StringResultData("Swiped up $pixels pixels"))
    }

    override suspend fun swipeDown(tool: AITool): ToolResult {
        val pixels = tool.parameters.find { it.name == "pixels" }?.value?.toIntOrNull() ?: 500
        val duration = tool.parameters.find { it.name == "duration_ms" }?.value?.toLongOrNull() ?: 500L

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        val startX = screenWidth / 2
        val startY = (screenHeight * 0.2).toInt()
        val endX = startX
        val endY = startY + pixels

        svc.swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
        return ToolResult(toolName = tool.name, success = true, result = StringResultData("Swiped down $pixels pixels"))
    }

    override suspend fun scrollDown(tool: AITool): ToolResult = swipeDown(tool)
    override suspend fun scrollUp(tool: AITool): ToolResult = swipeUp(tool)
    override suspend fun scrollLeft(tool: AITool): ToolResult = swipeLeft(tool)
    override suspend fun scrollRight(tool: AITool): ToolResult = swipeRight(tool)

    override suspend fun doubleTap(tool: AITool): ToolResult {
        val x = tool.parameters.find { it.name == "x" }?.value?.toIntOrNull() ?: return missingParam("x")
        val y = tool.parameters.find { it.name == "y" }?.value?.toIntOrNull() ?: return missingParam("y")

        val svc = service
        if (svc == null) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        }

        svc.clickOnPoint(x.toFloat(), y.toFloat())
        mainHandler.postDelayed({
            svc.clickOnPoint(x.toFloat(), y.toFloat())
        }, 200)

        return ToolResult(toolName = tool.name, success = true, result = StringResultData("Double tapped at ($x, $y)"))
    }

    override suspend fun hold(tool: AITool): ToolResult = longPress(tool)

    @RequiresApi(Build.VERSION_CODES.R)
    override suspend fun openApp(tool: AITool): ToolResult {
        val packageName = tool.parameters.find { it.name == "package_name" }?.value
            ?: tool.parameters.find { it.name == "app_name" }?.value
            ?: return missingParam("package_name")

        val resolvedPackage = APP_PACKAGES[packageName] ?: packageName

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(resolvedPackage)
            if (intent != null) {
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                return ToolResult(toolName = tool.name, success = true, result = StringResultData("Opened: $resolvedPackage"))
            } else {
                return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "App not found: $resolvedPackage")
            }
        } catch (e: Exception) {
            return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = e.message)
        }
    }

    override suspend fun back(tool: AITool): ToolResult {
        val svc = service ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        svc.performBack()
        return ToolResult(toolName = tool.name, success = true, result = StringResultData("Back"))
    }

    override suspend fun home(tool: AITool): ToolResult {
        val svc = service ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        svc.performHome()
        return ToolResult(toolName = tool.name, success = true, result = StringResultData("Home"))
    }

    override suspend fun getCurrentActivity(tool: AITool): ToolResult {
        val svc = service ?: return ToolResult(toolName = tool.name, success = false, result = StringResultData(""), error = "Accessibility service not available")
        
        // Hide floating window before getting current activity to avoid returning our own activity
        val floatingService = FloatingChatService.getInstance()
        val wasFloatingVisible = floatingService != null
        if (wasFloatingVisible) {
            try {
                floatingService?.setFloatingWindowVisible(false)
                delay(100)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to hide floating window", e)
            }
        }
        
        return try {
            // Get the current foreground app that is not our own
            val allWindows = svc.windows
            val ownPackageName = svc.packageName
            val targetWindow = allWindows
                .filter { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION }
                .filter { 
                    val windowPackageName = it.root?.packageName?.toString()
                    windowPackageName != ownPackageName
                }
                .maxByOrNull {
                    val bounds = android.graphics.Rect()
                    it.getBoundsInScreen(bounds)
                    bounds.width() * bounds.height()
                }
            
            val activity = if (targetWindow != null) {
                targetWindow.root?.packageName?.toString() ?: svc.getCurrentActivityName()
            } else {
                svc.getCurrentActivityName()
            }
            
            ToolResult(toolName = tool.name, success = true, result = StringResultData("Current activity: $activity"))
        } finally {
            // Restore floating window visibility
            if (wasFloatingVisible) {
                try {
                    floatingService?.setFloatingWindowVisible(true)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to restore floating window visibility", e)
                }
            }
        }
    }

    private fun missingParam(name: String) = ToolResult(toolName = "missing_param", success = false, result = StringResultData(""), error = "Missing parameter: $name")
}