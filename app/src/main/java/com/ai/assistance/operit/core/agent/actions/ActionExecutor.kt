package com.ai.assistance.operit.core.agent.actions

import android.view.accessibility.AccessibilityNodeInfo

sealed class Action {
    data class TapElement(val index: Int) : Action()
    data class LongPressElement(val index: Int) : Action()
    data class InputText(val text: String) : Action()
    data class TapElementInputTextPressEnter(val index: Int, val text: String) : Action()
    data class ScrollDown(val pixels: Int) : Action()
    data class ScrollUp(val pixels: Int) : Action()
    data class OpenApp(val packageName: String) : Action()
    object Back : Action()
    object Home : Action()
    object SwitchApp : Action()
    data class Speak(val text: String) : Action()
    data class Ask(val question: String) : Action()
    data class Done(val result: String) : Action()
    object Unknown : Action()
}

data class ActionResult(
    val isDone: Boolean = false,
    val error: String? = null,
    val longTermMemory: String? = null
)

object ActionExecutor {
    private val TAG = "ActionExecutor"

    suspend fun execute(
        action: Action,
        screenState: com.ai.assistance.operit.core.agent.perception.ScreenAnalysis,
        context: android.content.Context,
        fileSystem: com.ai.assistance.operit.core.agent.fs.FileSystem?
    ): ActionResult {
        val finger = com.ai.assistance.operit.api.automation.Finger(context)

        return when (action) {
            is Action.TapElement -> {
                val node = screenState.elementMap[action.index]
                if (node != null) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()
                    finger.tap(centerX, centerY)
                    ActionResult(longTermMemory = "Tapped element ${action.index}")
                } else {
                    ActionResult(error = "Element ${action.index} not found")
                }
            }

            is Action.LongPressElement -> {
                val node = screenState.elementMap[action.index]
                if (node != null) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()
                    finger.longPress(centerX, centerY)
                    ActionResult(longTermMemory = "Long pressed element ${action.index}")
                } else {
                    ActionResult(error = "Element ${action.index} not found")
                }
            }

            is Action.InputText -> {
                finger.type(action.text)
                ActionResult(longTermMemory = "Input text: ${action.text}")
            }

            is Action.TapElementInputTextPressEnter -> {
                val node = screenState.elementMap[action.index]
                if (node != null) {
                    val bounds = android.graphics.Rect()
                    node.getBoundsInScreen(bounds)
                    val centerX = bounds.centerX()
                    val centerY = bounds.centerY()
                    finger.tap(centerX, centerY)
                    finger.type(action.text)
                    ActionResult(longTermMemory = "Tapped element ${action.index} and typed: ${action.text}")
                } else {
                    ActionResult(error = "Element ${action.index} not found")
                }
            }

            is Action.ScrollDown -> {
                finger.scrollDown(action.pixels)
                ActionResult(longTermMemory = "Scrolled down by ${action.pixels} pixels")
            }

            is Action.ScrollUp -> {
                finger.scrollUp(action.pixels)
                ActionResult(longTermMemory = "Scrolled up by ${action.pixels} pixels")
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

            is Action.Speak -> {
                // TTS would be handled by a separate service
                ActionResult(longTermMemory = "Spoke: ${action.text}")
            }

            is Action.Ask -> {
                // This would pause execution and ask the user
                ActionResult(longTermMemory = "Asked user: ${action.question}")
            }

            is Action.Done -> {
                ActionResult(isDone = true, longTermMemory = action.result)
            }

            is Action.Unknown -> {
                ActionResult(error = "Unknown action")
            }
        }
    }
}