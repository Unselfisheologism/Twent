package com.ai.assistance.operit.core.agent.actions

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.ai.assistance.operit.api.automation.Finger
import com.ai.assistance.operit.core.agent.perception.ScreenAnalysis
import com.ai.assistance.operit.services.automation.OperitAutomationService

sealed class Action {
    // Element-based actions (by index)
    data class TapElement(val index: Int) : Action()
    data class LongPressElement(val index: Int) : Action()
    data class TapElementInputTextPressEnter(val index: Int, val text: String) : Action()
    data class ClickElement(val index: Int) : Action()
    data class ClickElementByText(val text: String) : Action()
    data class ClickElementByDesc(val contentDescription: String) : Action()
    
    // Coordinate-based actions (more reliable)
    data class TapAt(val x: Int, val y: Int) : Action()
    data class LongPressAt(val x: Int, val y: Int, val durationMs: Long = 1500) : Action()
    data class DoubleTapAt(val x: Int, val y: Int) : Action()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long = 500) : Action()
    data class SwipeLeft(val pixels: Int = 500) : Action()
    data class SwipeRight(val pixels: Int = 500) : Action()
    data class SwipeUp(val pixels: Int = 500) : Action()
    data class SwipeDown(val pixels: Int = 500) : Action()
    
    // System actions
    data class InputText(val text: String) : Action()
    data class PressKey(val key: String) : Action()
    data class OpenApp(val packageName: String) : Action()
    object Back : Action()
    object Home : Action()
    object SwitchApp : Action()
    object PressEnter : Action()
    
    // Meta actions
    data class Speak(val text: String) : Action()
    data class Ask(val question: String) : Action()
    data class Done(val result: String, val success: Boolean = true) : Action()
    object Wait : Action()
    object Unknown : Action()
}

data class ActionResult(
    val isDone: Boolean = false,
    val error: String? = null,
    val longTermMemory: String? = null
)

object ActionExecutor {
    private val TAG = "ActionExecutor"

    private val service: OperitAutomationService?
        get() = OperitAutomationService.instance

    suspend fun execute(
        action: Action,
        screenState: ScreenAnalysis,
        context: android.content.Context,
        fileSystem: com.ai.assistance.operit.core.agent.fs.FileSystem?
    ): ActionResult {
        val finger = Finger(context)

        return when (action) {
            // Element-based actions
            is Action.TapElement -> {
                val node = screenState.elementMap[action.index]
                if (node != null) {
                    executeWithFallback(node, finger, screenState) { centerX, centerY ->
                        finger.tap(centerX, centerY)
                    }
                    ActionResult(longTermMemory = "Tapped element ${action.index}")
                } else {
                    ActionResult(error = "Element ${action.index} not found")
                }
            }

            is Action.LongPressElement -> {
                val node = screenState.elementMap[action.index]
                if (node != null) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()
                    service?.showTapIndicator(centerX, centerY)
                    finger.longPress(centerX, centerY)
                    ActionResult(longTermMemory = "Long pressed element ${action.index}")
                } else {
                    ActionResult(error = "Element ${action.index} not found")
                }
            }

            is Action.TapElementInputTextPressEnter -> {
                val node = screenState.elementMap[action.index]
                if (node != null) {
                    val bounds = Rect()
                    node.getBoundsInScreen(bounds)
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()
                    service?.showTapIndicator(centerX, centerY)
                    finger.tap(centerX, centerY)
                    finger.type(action.text)
                    ActionResult(longTermMemory = "Tapped element ${action.index} and typed: ${action.text}")
                } else {
                    ActionResult(error = "Element ${action.index} not found")
                }
            }

            is Action.ClickElement -> {
                val node = screenState.elementMap[action.index]
                if (node != null) {
                    executeWithFallback(node, finger, screenState) { centerX, centerY ->
                        finger.tap(centerX, centerY)
                    }
                    ActionResult(longTermMemory = "Clicked element ${action.index}")
                } else {
                    ActionResult(error = "Element ${action.index} not found")
                }
            }

            is Action.ClickElementByText -> {
                val node = screenState.orderedNodes.find { n ->
                    n.text?.toString()?.contains(action.text, ignoreCase = true) == true
                }
                if (node != null) {
                    executeWithFallback(node, finger, screenState) { centerX, centerY ->
                        finger.tap(centerX, centerY)
                    }
                    ActionResult(longTermMemory = "Clicked element with text: ${action.text}")
                } else {
                    ActionResult(error = "Element with text '${action.text}' not found")
                }
            }

            is Action.ClickElementByDesc -> {
                val node = screenState.orderedNodes.find { n ->
                    n.contentDescription?.toString()?.contains(action.contentDescription, ignoreCase = true) == true
                }
                if (node != null) {
                    executeWithFallback(node, finger, screenState) { centerX, centerY ->
                        finger.tap(centerX, centerY)
                    }
                    ActionResult(longTermMemory = "Clicked element with description: ${action.contentDescription}")
                } else {
                    ActionResult(error = "Element with description '${action.contentDescription}' not found")
                }
            }

            // Coordinate-based actions
            is Action.TapAt -> {
                service?.showTapIndicator(action.x, action.y)
                finger.tap(action.x, action.y)
                ActionResult(longTermMemory = "Tapped at (${action.x}, ${action.y})")
            }

            is Action.LongPressAt -> {
                finger.longPress(action.x, action.y)
                ActionResult(longTermMemory = "Long pressed at (${action.x}, ${action.y})")
            }

            is Action.DoubleTapAt -> {
                service?.showTapIndicator(action.x, action.y)
                finger.tap(action.x, action.y)
                kotlinx.coroutines.delay(200)
                finger.tap(action.x, action.y)
                ActionResult(longTermMemory = "Double tapped at (${action.x}, ${action.y})")
            }

            is Action.Swipe -> {
                finger.swipe(action.startX, action.startY, action.endX, action.endY, action.durationMs.toInt())
                ActionResult(longTermMemory = "Swiped from (${action.startX}, ${action.startY}) to (${action.endX}, ${action.endY})")
            }

            is Action.SwipeLeft -> finger.swipeLeft(action.pixels).let {
                ActionResult(longTermMemory = "Swiped left ${action.pixels} pixels")
            }

            is Action.SwipeRight -> finger.swipeRight(action.pixels).let {
                ActionResult(longTermMemory = "Swiped right ${action.pixels} pixels")
            }

            is Action.SwipeUp -> finger.swipeUp(action.pixels).let {
                ActionResult(longTermMemory = "Swiped up ${action.pixels} pixels")
            }

            is Action.SwipeDown -> finger.swipeDown(action.pixels).let {
                ActionResult(longTermMemory = "Swiped down ${action.pixels} pixels")
            }

            is Action.InputText -> {
                finger.type(action.text)
                ActionResult(longTermMemory = "Input text: ${action.text}")
            }

            is Action.OpenApp -> {
                val success = finger.openApp(action.packageName)
                if (success) {
                    ActionResult(longTermMemory = "Opened app: ${action.packageName}")
                } else {
                    ActionResult(error = "Failed to open app: ${action.packageName}")
                }
            }

            is Action.Back -> {
                finger.back()
                ActionResult(longTermMemory = "Performed back action")
            }

            is Action.Home -> {
                finger.home()
                ActionResult(longTermMemory = "Performed home action")
            }

            is Action.SwitchApp -> {
                finger.switchApp()
                ActionResult(longTermMemory = "Performed app switch action")
            }

            is Action.PressEnter -> {
                finger.enter()
                ActionResult(longTermMemory = "Pressed enter")
            }

            is Action.PressKey -> {
                when (action.key.lowercase()) {
                    "enter" -> finger.enter()
                    "back" -> finger.back()
                    "home" -> finger.home()
                    else -> finger.enter()
                }
                ActionResult(longTermMemory = "Pressed key: ${action.key}")
            }

            is Action.Speak -> {
                ActionResult(longTermMemory = "Spoke: ${action.text}")
            }

            is Action.Ask -> {
                ActionResult(longTermMemory = "Asked user: ${action.question}")
            }

            is Action.Wait -> {
                kotlinx.coroutines.delay(5000)
                ActionResult(longTermMemory = "Waited 5 seconds")
            }

            is Action.Done -> {
                ActionResult(isDone = true, longTermMemory = action.result)
            }

            is Action.Unknown -> {
                ActionResult(error = "Unknown action")
            }
        }
    }

    private suspend fun executeWithFallback(
        node: AccessibilityNodeInfo,
        finger: Finger,
        screenState: ScreenAnalysis,
        tapAction: (Int, Int) -> Unit
    ): ActionResult {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val centerX = bounds.centerX()
        val centerY = bounds.centerY()

        val service = OperitAutomationService.instance
        val signatureBefore = service?.getWindowHierarchySignature() ?: ""

        node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        kotlinx.coroutines.delay(100)

        val signatureAfter = service?.getWindowHierarchySignature() ?: ""
        val screenChanged = signatureBefore != signatureAfter

        return if (screenChanged) {
            ActionResult(longTermMemory = "Accessibility click succeeded")
        } else {
            service?.showTapIndicator(centerX, centerY)
            tapAction(centerX, centerY)
            ActionResult(longTermMemory = "Escalated to physical tap at ($centerX, $centerY)")
        }
    }
}