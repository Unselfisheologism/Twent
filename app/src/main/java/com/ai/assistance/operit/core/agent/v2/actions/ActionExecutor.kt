package com.ai.assistance.operit.core.agent.v2.actions

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.api.automation.Finger as OperitFinger
import com.ai.assistance.operit.core.agent.v2.actions.Action
import com.ai.assistance.operit.core.agent.v2.ActionResult
import com.ai.assistance.operit.core.agent.v2.fs.FileSystem
import com.ai.assistance.operit.core.agent.v2.perception.ScreenAnalysis

import com.ai.assistance.operit.services.automation.OperitAutomationService
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis
import kotlin.text.removePrefix

class ActionExecutor(private val finger: OperitFinger, private val context: Context) {

    private val executorScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private fun getExtraInfo(node: AccessibilityNodeInfo): String {
        val infoParts = mutableListOf<String>()
        if (node.isCheckable) infoParts.add("checkable")
        if (node.isChecked) infoParts.add("checked")
        if (node.isClickable) infoParts.add("clickable")
        if (node.isEnabled) infoParts.add("enabled")
        if (node.isFocusable) infoParts.add("focusable")
        if (node.isFocused) infoParts.add("focused")
        if (node.isScrollable) infoParts.add("scrollable")
        if (node.isLongClickable) infoParts.add("long clickable")
        if (node.isSelected) infoParts.add("selected")

        return if (infoParts.isNotEmpty()) {
            "This element is ${infoParts.joinToString(", ")}."
        } else {
            ""
        }
    }

    private fun findPackageNameFromAppName(appName: String, context: Context): String? {
        val pm = context.packageManager
        val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.contains(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        return null
    }

    private fun getVisibleText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        return (if (text.isNotBlank()) text else contentDesc).replace("\n", " ")
    }
    
    private fun getCenterFromNode(node: AccessibilityNodeInfo): Pair<Int, Int>? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            return null
        }
        return Pair(bounds.centerX(), bounds.centerY())
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun execute(
        action: Action,
        screenAnalysis: ScreenAnalysis,
        context: Context,
        fileSystem: FileSystem
    ): ActionResult {
        return when (action) {
            is Action.TapElement -> {
                val elementId = action.elementId
                Log.d("ActionExecutor", "TapElement action - looking for element ID $elementId")
                
                // First try exact match
                var elementNode = screenAnalysis.elementMap[elementId]
                
                // If not found, try +/- 1 (LLM might use 1-based indexing)
                if (elementNode == null) {
                    Log.d("ActionExecutor", "Exact match not found, trying offset...")
                    elementNode = screenAnalysis.elementMap[elementId] 
                        ?: screenAnalysis.elementMap[elementId + 1]
                        ?: screenAnalysis.elementMap[elementId - 1]
                        ?: screenAnalysis.elementMap[elementId + 10]
                        ?: screenAnalysis.elementMap[elementId - 10]
                }
                
                Log.d("ActionExecutor", "Available element IDs in map: ${screenAnalysis.elementMap.keys.sorted().take(20)}")
                
                if (elementNode != null) {
                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val bounds = Rect()
                    elementNode.getBoundsInScreen(bounds)
                    Log.d("ActionExecutor", "Found element: text='$text', id='$resourceId', bounds=$bounds")
                    
                    // PRIMARY: Use direct coordinate tap for speed and accuracy
                    if (!bounds.isEmpty) {
                        val center = Pair(bounds.centerX(), bounds.centerY())
                        Log.d("ActionExecutor", "Using direct tap at coordinates (${center.first}, ${center.second})")
                        finger.tap(center.first, center.second)
                        delay(50)
                        
                        ActionResult(longTermMemory = "Tapped element '$text' at ${center.first},${center.second}")
                    } else {
                        Log.d("ActionExecutor", "Element has no bounds, trying accessibility click")
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(100)
                        ActionResult(longTermMemory = "Tapped element '$text' via accessibility API")
                    }
                } else {
                    Log.w("ActionExecutor", "Element with ID $elementId not found in elementMap!")
                    ActionResult(error = "Element with ID $elementId not found.")
                }
            }
            is Action.Speak -> {
                val message = action.message
                executorScope.launch {
                    try {
                        val voiceService = com.ai.assistance.operit.api.voice.VoiceServiceFactory.getInstance(context)
                        voiceService.speak(message)
                    } catch (e: Exception) {
                        Log.e("ActionExecutor", "TTS failed", e)
                    }
                }
                ActionResult(longTermMemory = "Spoke the message: \"${message.take(50)}...\"")
            }
            is Action.Ask -> {
                val question = action.question
                val memory = "Asked user: '$question'. User response needed."
                ActionResult(
                    longTermMemory = memory,
                    extractedContent = null,
                    includeExtractedContentOnlyOnce = true
                )
            }
            is Action.LongPressElement -> {
                val elementNode = screenAnalysis.elementMap[action.elementId]
                if (elementNode != null) {
                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val extraInfo = getExtraInfo(elementNode)
                    val className = (elementNode.className ?: "").removePrefix("android.")

                    val center = getCenterFromNode(elementNode)
                    if (center != null) {
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)
                        ActionResult(longTermMemory = "Long-pressed element text:$text <$resourceId> <$extraInfo> <$className>")
                    } else {
                        ActionResult(error = "Element with ID ${action.elementId} has no visible bounds.")
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.elementId} not found in the current screen state.")
                }
            }
            is Action.OpenApp -> {
                val packageName = findPackageNameFromAppName(action.appName, context)
                if (packageName != null) {
                    val success = finger.openApp(packageName)
                    if (success) {
                        ActionResult(longTermMemory = "Opened app '${action.appName}'.")
                    } else {
                        ActionResult(error = "Failed to open app '${action.appName}' (package: $packageName). Maybe try using different name or use app drawer by scrolling up.")
                    }
                } else {
                    ActionResult(error = "App '${action.appName}' not found. Maybe try using different name or use app drawer by scrolling up.")
                }
            }
            Action.Back -> {
                finger.back()
                ActionResult(longTermMemory = "Pressed the back button.")
            }
            Action.Home -> {
                finger.home()
                ActionResult(longTermMemory = "Pressed the home button.")
            }
            Action.SwitchApp -> {
                finger.switchApp()
                ActionResult(longTermMemory = "Opened the app switcher.")
            }
            Action.Wait -> {
                delay(5_000)
                ActionResult(longTermMemory = "Waited for 5 seconds.")
            }
            is Action.ScrollDown -> {
                finger.scrollDown(action.amount)
                ActionResult(longTermMemory = "Scrolled down by ${action.amount} pixels.")
            }
            is Action.ScrollUp -> {
                finger.scrollUp(action.amount)
                ActionResult(longTermMemory = "Scrolled up by ${action.amount} pixels.")
            }
            is Action.SearchGoogle -> {
                finger.openApp("com.android.chrome")
                ActionResult(longTermMemory = "Opened Chrome to search Google.")
            }
            is Action.Done -> {
                ActionResult(
                    isDone = true,
                    success = action.success,
                    longTermMemory = "Task finished: ${action.text}",
                    attachments = action.filesToDisplay
                )
            }
            is Action.InputText -> {
                finger.type(action.text)
                ActionResult(longTermMemory = "Input text ${action.text}.")
            }
            is Action.AppendFile -> {
                val success = fileSystem.appendFile(action.fileName, action.content)
                if (success) {
                    ActionResult(longTermMemory = "Appended content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to append to file '${action.fileName}'.")
                }
            }
            is Action.ReadFile -> {
                val content = fileSystem.readFile(action.fileName)
                if (content.startsWith("Error:")) {
                    ActionResult(error = content)
                } else {
                    ActionResult(
                        longTermMemory = "Read content from '${action.fileName}'.",
                        extractedContent = content,
                        includeExtractedContentOnlyOnce = true
                    )
                }
            }
            is Action.WriteFile -> {
                val success = fileSystem.writeFile(action.fileName, action.content)
                if (success) {
                    ActionResult(longTermMemory = "Wrote content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to write to file '${action.fileName}'.")
                }
            }
            is Action.TapElementInputTextPressEnter -> {
                val elementNode = screenAnalysis.elementMap[action.index]
                if (elementNode != null) {
                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val extraInfo = getExtraInfo(elementNode)
                    val className = (elementNode.className ?: "").removePrefix("android.")

                    val center = getCenterFromNode(elementNode)
                    if (center != null) {
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        delay(200)
                        finger.type(action.text)
                        delay(100)
                        finger.enter()
                        ActionResult(longTermMemory = "Tapped, typed '${action.text}', and pressed Enter on element: text:$text <$resourceId> <$extraInfo> <$className>.")
                    } else {
                        ActionResult(error = "Element with ID ${action.index} has no visible bounds.")
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.index} for input not found.")
                }
            }
            is Action.LaunchIntent -> {
                val name = action.intentName
                val params = action.parameters
                val intent = com.ai.assistance.operit.intents.IntentRegistry.findByName(context, name)
                if (intent != null) {
                    val androidIntent = intent.buildIntent(context, params)
                    if (androidIntent != null) {
                        try {
                            androidIntent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(androidIntent)
                            ActionResult(longTermMemory = "Launched intent '$name' with parameters: $params")
                        } catch (e: Exception) {
                            ActionResult(error = "Failed to launch intent '$name': ${e.message}")
                        }
                    } else {
                        ActionResult(error = "Failed to build intent '$name'. Check parameters.")
                    }
                } else {
                    ActionResult(error = "Intent '$name' not found. Check intents catalog for valid names.")
                }
            }
            is Action.TapAt -> {
                finger.tap(action.x, action.y)
                ActionResult(longTermMemory = "Tapped at (${action.x}, ${action.y})")
            }
            is Action.LongPressAt -> {
                finger.longPress(action.x, action.y)
                ActionResult(longTermMemory = "Long pressed at (${action.x}, ${action.y})")
            }
            is Action.DoubleTapAt -> {
                finger.tap(action.x, action.y)
                kotlinx.coroutines.delay(200)
                finger.tap(action.x, action.y)
                ActionResult(longTermMemory = "Double tapped at (${action.x}, ${action.y})")
            }
            is Action.Swipe -> {
                finger.swipe(action.startX, action.startY, action.endX, action.endY, action.durationMs.toInt())
                ActionResult(longTermMemory = "Swiped from (${action.startX}, ${action.startY}) to (${action.endX}, ${action.endY})")
            }
            is Action.SwipeUp -> {
                finger.swipeUp(action.pixels)
                ActionResult(longTermMemory = "Swiped up ${action.pixels} pixels")
            }
            is Action.SwipeDown -> {
                finger.swipeDown(action.pixels)
                ActionResult(longTermMemory = "Swiped down ${action.pixels} pixels")
            }
            is Action.SwipeLeft -> {
                finger.swipeLeft(action.pixels)
                ActionResult(longTermMemory = "Swiped left ${action.pixels} pixels")
            }
            is Action.SwipeRight -> {
                finger.swipeRight(action.pixels)
                ActionResult(longTermMemory = "Swiped right ${action.pixels} pixels")
            }
            is Action.PressKey -> {
                when (action.key.lowercase()) {
                    "enter" -> finger.enter()
                    "back" -> finger.back()
                    "home" -> finger.home()
                }
                ActionResult(longTermMemory = "Pressed key: ${action.key}")
            }
        }
    }
}
