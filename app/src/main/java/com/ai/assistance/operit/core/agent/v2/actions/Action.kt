package com.ai.assistance.operit.core.agent.v2.actions

import kotlin.reflect.KClass

data class ParamSpec(val name: String, val type: KClass<*>, val description: String)

sealed class Action {
    data class LongPressElement(val elementId: Int) : Action()
    data class TapElement(val elementId: Int) : Action()
    data object SwitchApp : Action()
    data object Back : Action()
    data object Home : Action()
    data object Wait : Action()
    data class Speak(val message: String) : Action()
    data class Ask(val question: String) : Action()
    data class OpenApp(val appName: String) : Action()
    data class ScrollDown(val amount: Int) : Action()
    data class ScrollUp(val amount: Int) : Action()
    data class SearchGoogle(val query: String) : Action()
    data class TapElementInputTextPressEnter(val index: Int, val text: String) : Action()
    data class InputText(val text: String) : Action()
    data class WriteFile(val fileName: String, val content: String) : Action()
    data class AppendFile(val fileName: String, val content: String) : Action()
    data class ReadFile(val fileName: String) : Action()
    data class Done(val success: Boolean, val text: String, val filesToDisplay: List<String>? = null) : Action()
    data class LaunchIntent(val intentName: String, val parameters: Map<String, String>) : Action()
    
    // Coordinate-based actions
    data class TapAt(val x: Int, val y: Int) : Action()
    data class LongPressAt(val x: Int, val y: Int, val durationMs: Long = 1500) : Action()
    data class DoubleTapAt(val x: Int, val y: Int) : Action()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long = 500) : Action()
    data class SwipeLeft(val pixels: Int = 500) : Action()
    data class SwipeRight(val pixels: Int = 500) : Action()
    data class SwipeUp(val pixels: Int = 500) : Action()
    data class SwipeDown(val pixels: Int = 500) : Action()
    data class PressKey(val key: String) : Action()

    companion object {
        data class Spec(
            val name: String,
            val description: String,
            val params: List<ParamSpec>,
            val build: (args: Map<String, Any?>) -> Action
        )

        private val allSpecs: Map<String, Spec> = mapOf(
            "tap_element" to Spec(
                name = "tap_element",
                description = "Tap the element with the specified numeric ID.",
                params = listOf(ParamSpec("element_id", Int::class, "The numeric ID of the element.")),
                build = { args -> TapElement(args["element_id"] as Int) }
            ),
            "tap" to Spec(
                name = "tap",
                description = "Tap at coordinates.",
                params = listOf(
                    ParamSpec("x", Int::class, "X coordinate"),
                    ParamSpec("y", Int::class, "Y coordinate")
                ),
                build = { args -> TapAt(args["x"] as Int, args["y"] as Int) }
            ),
            "switch_app" to Spec("switch_app", "Show the App switcher.", emptyList()) { SwitchApp },
            "back" to Spec("back", "Go back to the previous screen.", emptyList()) { Back },
            "home" to Spec("home", "Go to the device's home screen.", emptyList()) { Home },
            "wait" to Spec("wait", "Wait for a few seconds for loading.", emptyList()) { Wait },
            "speak" to Spec(
                name = "speak",
                description = "Speak the 'message' to the user.",
                params = listOf(ParamSpec("message", String::class, "The message to speak.")),
                build = { args -> Speak(args["message"] as String) }
            ),
            "ask" to Spec(
                name = "ask",
                description = "Ask the 'question' to the user and await a response.",
                params = listOf(ParamSpec("question", String::class, "The question to ask.")),
                build = { args -> Ask(args["question"] as String) }
            ),
            "open_app" to Spec(
                name = "open_app",
                description = "Open the app named 'app_name'.",
                params = listOf(ParamSpec("app_name", String::class, "The name of the app.")),
                build = { args -> OpenApp(args["app_name"] as String) }
            ),
            "swipe_down" to Spec(
                name = "swipe_down",
                description = "swipe down by the specified amount of pixels.",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to swipe down.")),
                build = { args -> ScrollDown(args["amount"] as Int) }
            ),
            "long_press_element" to Spec(
                name = "long_press_element",
                description = "Press and hold the element with the specified numeric ID.",
                params = listOf(ParamSpec("element_id", Int::class, "The numeric ID of the element to long press.")),
                build = { args -> LongPressElement(args["element_id"] as Int) }
            ),
            "swipe_up" to Spec(
                name = "swipe_up",
                description = "swipe up by the specified amount of pixels.",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to swipe up.")),
                build = { args -> ScrollUp(args["amount"] as Int) }
            ),
            "search_google" to Spec(
                name = "search_google",
                description = "Search Google with the specified query.",
                params = listOf(ParamSpec("query", String::class, "The search query to perform on Google")),
                build = { args -> SearchGoogle(args["query"] as String) }
            ),
            "tap_element_input_text_and_enter" to Spec(
                name = "tap_element_input_text_and_enter",
                description = "Taps an element, inputs text, and presses enter.",
                params = listOf(
                    ParamSpec("index", Int::class, "The numerical index of the input element."),
                    ParamSpec("text", String::class, "The text to be typed into the element.")
                ),
                build = { args -> TapElementInputTextPressEnter(args["index"] as Int, args["text"] as String) }
            ),
            "done" to Spec(
                name = "done",
                description = "Completes the current task.",
                params = listOf(
                    ParamSpec("success", Boolean::class, "True if the task was completed successfully."),
                    ParamSpec("text", String::class, "A summary of the results.")
                ),
                build = { args -> Done(args["success"] as Boolean, args["text"] as String, null) }
            ),
            "write_file" to Spec(
                name = "write_file",
                description = "Write content to a file, overwriting existing content.",
                params = listOf(
                    ParamSpec("file_name", String::class, "The name of the file."),
                    ParamSpec("content", String::class, "The content to write.")
                ),
                build = { args -> WriteFile(args["file_name"] as String, args["content"] as String) }
            ),
            "append_file" to Spec(
                name = "append_file",
                description = "Append content to the end of a file.",
                params = listOf(
                    ParamSpec("file_name", String::class, "The name of the file to append to."),
                    ParamSpec("content", String::class, "The content to append.")
                ),
                build = { args -> AppendFile(args["file_name"] as String, args["content"] as String) }
            ),
            "read_file" to Spec(
                name = "read_file",
                description = "Read the entire content of a file.",
                params = listOf(ParamSpec("file_name", String::class, "The name of the file to read.")),
                build = { args -> ReadFile(args["file_name"] as String) }
            ),
            "type_text" to Spec(
                name = "type_text",
                description = "Type text into a focused input field.",
                params = listOf(ParamSpec("text", String::class, "The text to type.")),
                build = { args -> InputText(args["text"] as String) }
            ),
            "type" to Spec(
                name = "type",
                description = "Type text into a focused input field.",
                params = listOf(ParamSpec("text", String::class, "The text to type.")),
                build = { args -> InputText(args["text"] as String) }
            ),
            "long_press" to Spec(
                name = "long_press",
                description = "Long press at coordinates.",
                params = listOf(
                    ParamSpec("x", Int::class, "X coordinate"),
                    ParamSpec("y", Int::class, "Y coordinate"),
                    ParamSpec("duration_ms", Int::class, "Duration in milliseconds")
                ),
                build = { args -> LongPressAt(args["x"] as Int, args["y"] as Int, (args["duration_ms"] as? Int ?: 1500).toLong()) }
            ),
            "swipe_left" to Spec(
                name = "swipe_left",
                description = "Swipe left.",
                params = listOf(ParamSpec("pixels", Int::class, "Pixels to swipe.")),
                build = { args -> SwipeLeft(args["pixels"] as Int) }
            ),
            "swipe_right" to Spec(
                name = "swipe_right",
                description = "Swipe right.",
                params = listOf(ParamSpec("pixels", Int::class, "Pixels to swipe.")),
                build = { args -> SwipeRight(args["pixels"] as Int) }
            ),
            "launch_intent" to Spec(
                name = "launch_intent",
                description = "Launch an Android AppIntent by name with parameters.",
                params = listOf(
                    ParamSpec("intent_name", String::class, "The name of the intent."),
                    ParamSpec("parameters", Map::class, "A map of parameter names to values.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    LaunchIntent(
                        intentName = args["intent_name"] as String,
                        parameters = args["parameters"] as? Map<String, String> ?: emptyMap()
                    )
                }
            ),
        )

        fun getAllSpecs(): Collection<Spec> = allSpecs.values
    }
}
