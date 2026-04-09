package com.ai.assistance.operit.voice.v2.actions

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import kotlin.reflect.KClass

// Data class to hold parameter metadata without reflection
data class ParamSpec(val name: String, val type: KClass<*>, val description: String)

/** 
 * A sealed class representing all possible type-safe commands the agent can execute.
 * It is annotated to use the custom, data-driven ActionSerializer.
 */
@Serializable(with = Action.ActionSerializer::class)
sealed class Action {
    // Each action is a data class (if it has args) or an object (if it doesn't).
    // Note: Property names here follow Kotlin's camelCase convention.
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
    // New: Launch an Android AppIntent by name with parameters
    data class LaunchIntent(val intentName: String, val parameters: Map<String, String>) : Action()

    // Coordinate-based actions for full UI automation parity
    data class TapAt(val x: Int, val y: Int) : Action()
    data class LongPressAt(val x: Int, val y: Int, val durationMs: Long = 1500) : Action()
    data class DoubleTapAt(val x: Int, val y: Int) : Action()
    data class Swipe(val startX: Int, val startY: Int, val endX: Int, val endY: Int, val durationMs: Long = 500) : Action()
    data class SwipeLeft(val pixels: Int = 500) : Action()
    data class SwipeRight(val pixels: Int = 500) : Action()
    data class SwipeUp(val pixels: Int = 500) : Action()
    data class SwipeDown(val pixels: Int = 500) : Action()
    data class PressKey(val key: String) : Action()

    // ========== Operit System Tools (bridged from core/tools/) ==========

    // System Operation Tools
    data class Toast(val message: String) : Action()
    data class SendNotification(val title: String, val message: String) : Action()
    data class ModifySystemSetting(val settingType: String, val key: String, val value: String) : Action()
    data class GetSystemSetting(val settingType: String, val key: String) : Action()
    data class StopApp(val packageName: String) : Action()
    data class ListInstalledApps(val limit: Int = 50) : Action()

    // HTTP / Network Tools (HEADLESS - no login, no browser UI)
    data class HttpRequest(
        val method: String = "GET",
        val url: String,
        val headers: Map<String, String>? = null,
        val body: String? = null,
        val timeoutSeconds: Int = 30
    ) : Action()
    data class VisitWeb(val url: String, val maxContentLength: Int = 10000) : Action()

    // Shell / Terminal Tools
    data class ExecuteShell(val command: String, val timeoutSeconds: Int = 30) : Action()

    // Calculator Tool
    data class Calculate(val expression: String) : Action()

    // Device Info Tool
    data object GetDeviceInfo : Action()

    // Memory Tools
    data class QueryMemory(val query: String, val limit: Int = 5) : Action()
    data class CreateMemory(val title: String, val content: String, val tags: List<String>? = null) : Action()
    data class UpdateMemory(val title: String, val content: String) : Action()
    data class DeleteMemory(val title: String) : Action()

    // URL Launch Tool (opens in default browser, NOT headless)
    data class LaunchUrlInBrowser(val url: String) : Action()

    // Screenshot Tool
    data class CaptureScreenshot(val saveAs: String? = null) : Action()

    // Mini-App Tools
    data class CreateMiniApp(
        val name: String,
        val html: String,
        val type: String = "persistent",
        val css: String = "",
        val javascript: String = "",
        val description: String = ""
    ) : Action()
    data object ListMiniApps : Action()
    data class DeleteMiniApp(val appId: String) : Action()

    // --- The Custom Serializer ---
    // This serializer is now data-driven, using the `allSpecs` map as its source of truth.
    object ActionSerializer : KSerializer<Action> {
        override val descriptor: SerialDescriptor = buildClassSerialDescriptor("Action")

        override fun serialize(encoder: Encoder, value: Action) {
            throw NotImplementedError("Serialization is not supported for this agent.")
        }

        override fun deserialize(decoder: Decoder): Action {
            val jsonInput = (decoder as JsonDecoder).decodeJsonElement().jsonObject
            val actionName = jsonInput.keys.first()
            val paramsJson = jsonInput[actionName]?.jsonObject

            // Look up the action's specification from our single source of truth.
            val spec = allSpecs[actionName]
                ?: throw IllegalArgumentException("Unknown action received from LLM: $actionName")

            val args = mutableMapOf<String, Any?>()

            // If the action has parameters, parse them according to the spec.
            paramsJson?.let {
                for (paramSpec in spec.params) {
                    val paramName = paramSpec.name
                    val jsonValue = it[paramName]
                        ?: continue // Allow optional parameters

                    // Convert JSON element to the correct Kotlin type.
                    val value = when (paramSpec.type) {
                        Int::class -> jsonValue.jsonPrimitive.int
                        String::class -> jsonValue.jsonPrimitive.content
                        Boolean::class -> jsonValue.jsonPrimitive.boolean
                        List::class -> jsonValue.jsonArray.map { el -> el.jsonPrimitive.content }
                        Map::class -> jsonValue.jsonObject.mapValues { entry ->
                            // We coerce all values to string for intent parameter passing
                            entry.value.jsonPrimitive.content
                        }
                        else -> throw IllegalStateException("Unsupported parameter type in Spec: ${paramSpec.type}")
                    }
                    args[paramName] = value
                }
            }
            // Use the 'build' lambda from the spec to construct the final, type-safe Action object.
            return spec.build(args)
        }
    }

    // --- Companion Object: The Registry and Single Source of Truth ---
    companion object {
        data class Spec(
            val name: String,
            val description: String,
            val params: List<ParamSpec>,
            val build: (args: Map<String, Any?>) -> Action
        )

        // The single source of truth for all actions.
        // Keys and names are now consistently in snake_case for the LLM.
        private val allSpecs: Map<String, Spec> = mapOf(
            "tap_element" to Spec(
                name = "tap_element",
                description = "Tap the element with the specified numeric ID.",
                params = listOf(ParamSpec("element_id", Int::class, "The numeric ID of the element.")),
                build = { args -> TapElement(args["element_id"] as Int) }
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
                description = "Open an installed app by its user-friendly name (e.g., 'Brave', 'Gmail'). Use ONLY when the app is confirmed installed. For websites, prefer launch_url_in_browser instead.",
                params = listOf(ParamSpec("app_name", String::class, "The name of the app.")),
                build = { args -> OpenApp(args["app_name"] as String) }
            ),
            "launch_url_in_browser" to Spec(
                name = "launch_url_in_browser",
                description = "OPENS A URL IN THE DEFAULT BROWSER (Brave, Chrome, etc.) with full JavaScript, cookies, and login sessions. THIS IS THE PRIMARY ACTION FOR ANY WEBSITE TASK. Use direct URLs like 'https://x.com/notifications'. DO NOT use open_app + typing for websites - use this instead. The user must be logged in for authenticated pages.",
                params = listOf(ParamSpec("url", String::class, "The full URL to open, e.g. 'https://x.com/notifications'.")),
                build = { args -> LaunchUrlInBrowser(args["url"] as String) }
            ),
            "swipe_down" to Spec(
                name = "swipe_down",
                description = "swipe down by the specified amount of pixels.",
                params = listOf(ParamSpec("amount", Int::class, "Amount of pixels to swipe down.")),
                build = { args -> ScrollDown(args["amount"] as Int) }
            ),
            "long_press_element" to Spec(
                name = "long_press_element",
                description = "Press and hold the element with the specified numeric ID. Useful for context menus, selecting text, etc.",
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
                description = "Taps an element, inputs text, and presses enter. Useful for search bars.",
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
                    ParamSpec("success", Boolean::class, "True if the task was completed successfully, False otherwise."),
                    ParamSpec("text", String::class, "A summary of the results or a final message for the user."),
                    ParamSpec("files_to_display", List::class, "A list of filenames (e.g., ['report.pdf']) to show the user.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    Done(
                        args["success"] as Boolean,
                        args["text"] as String,
                        args["files_to_display"] as? List<String>
                    )
                }
            ),
            "write_file" to Spec(
                name = "write_file",
                description = "Write content to a file, overwriting existing content.",
                params = listOf(
                    ParamSpec("file_name", String::class, "The name of the file (e.g., 'notes.txt')."),
                    ParamSpec("content", String::class, "The content to write to the file.")
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
            "type" to Spec(
                name = "type",
                description = "Type text into a focused input field.",
                params = listOf(ParamSpec("text", String::class, "The text to type.")),
                build = { args -> InputText(args["text"] as String) }
            ),
            // New action spec: launch_intent
            "launch_intent" to Spec(
                name = "launch_intent",
                description = "Launch an Android AppIntent by name with parameters. Use this for OS-level actions like Dial, Share, etc.",
                params = listOf(
                    ParamSpec("intent_name", String::class, "The name of the intent to launch (see intents catalog)."),
                    ParamSpec("parameters", Map::class, "A map of parameter names to their string values as required by the intent.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    LaunchIntent(
                        intentName = args["intent_name"] as String,
                        parameters = args["parameters"] as? Map<String, String> ?: emptyMap()
                    )
                }
            ),
            // Coordinate-based action specs for full UI automation parity
            "tap" to Spec(
                name = "tap",
                description = "Tap at coordinates.",
                params = listOf(
                    ParamSpec("x", Int::class, "X coordinate"),
                    ParamSpec("y", Int::class, "Y coordinate")
                ),
                build = { args -> TapAt(args["x"] as Int, args["y"] as Int) }
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
            "double_tap" to Spec(
                name = "double_tap",
                description = "Double tap at coordinates.",
                params = listOf(
                    ParamSpec("x", Int::class, "X coordinate"),
                    ParamSpec("y", Int::class, "Y coordinate")
                ),
                build = { args -> DoubleTapAt(args["x"] as Int, args["y"] as Int) }
            ),
            "swipe" to Spec(
                name = "swipe",
                description = "Swipe from one coordinate to another.",
                params = listOf(
                    ParamSpec("start_x", Int::class, "Start X coordinate"),
                    ParamSpec("start_y", Int::class, "Start Y coordinate"),
                    ParamSpec("end_x", Int::class, "End X coordinate"),
                    ParamSpec("end_y", Int::class, "End Y coordinate"),
                    ParamSpec("duration_ms", Int::class, "Duration in milliseconds")
                ),
                build = { args -> Swipe(
                    startX = args["start_x"] as Int,
                    startY = args["start_y"] as Int,
                    endX = args["end_x"] as Int,
                    endY = args["end_y"] as Int,
                    durationMs = (args["duration_ms"] as? Int ?: 500).toLong()
                )}
            ),
            "swipe_left" to Spec(
                name = "swipe_left",
                description = "Swipe left by pixels.",
                params = listOf(ParamSpec("pixels", Int::class, "Pixels to swipe left.")),
                build = { args -> SwipeLeft(args["pixels"] as Int) }
            ),
            "swipe_right" to Spec(
                name = "swipe_right",
                description = "Swipe right by pixels.",
                params = listOf(ParamSpec("pixels", Int::class, "Pixels to swipe right.")),
                build = { args -> SwipeRight(args["pixels"] as Int) }
            ),
            "press_key" to Spec(
                name = "press_key",
                description = "Press a system key.",
                params = listOf(ParamSpec("key", String::class, "Key to press: enter, back, home, recents.")),
                build = { args -> PressKey(args["key"] as String) }
            ),

            // ========== Operit System Tools ==========
            "toast" to Spec(
                name = "toast",
                description = "Show a brief toast notification on screen.",
                params = listOf(ParamSpec("message", String::class, "The message to display in the toast.")),
                build = { args -> Toast(args["message"] as String) }
            ),
            "send_notification" to Spec(
                name = "send_notification",
                description = "Send a system notification with title and message.",
                params = listOf(
                    ParamSpec("title", String::class, "The notification title."),
                    ParamSpec("message", String::class, "The notification body text.")
                ),
                build = { args -> SendNotification(args["title"] as String, args["message"] as String) }
            ),
            "modify_system_setting" to Spec(
                name = "modify_system_setting",
                description = "Change an Android system setting value.",
                params = listOf(
                    ParamSpec("setting_type", String::class, "Type: 'system', 'secure', or 'global'."),
                    ParamSpec("key", String::class, "The setting key name."),
                    ParamSpec("value", String::class, "The new value to set.")
                ),
                build = { args -> ModifySystemSetting(args["setting_type"] as String, args["key"] as String, args["value"] as String) }
            ),
            "get_system_setting" to Spec(
                name = "get_system_setting",
                description = "Read an Android system setting value.",
                params = listOf(
                    ParamSpec("setting_type", String::class, "Type: 'system', 'secure', or 'global'."),
                    ParamSpec("key", String::class, "The setting key name.")
                ),
                build = { args -> GetSystemSetting(args["setting_type"] as String, args["key"] as String) }
            ),
            "stop_app" to Spec(
                name = "stop_app",
                description = "Force-stop an app by its package name.",
                params = listOf(ParamSpec("package_name", String::class, "The app package name to stop.")),
                build = { args -> StopApp(args["package_name"] as String) }
            ),
            "list_installed_apps" to Spec(
                name = "list_installed_apps",
                description = "List installed application package names.",
                params = listOf(ParamSpec("limit", Int::class, "Maximum number of apps to return (default 50).")),
                build = { args -> ListInstalledApps(args["limit"] as? Int ?: 50) }
            ),

            // HTTP / Network
            "http_request" to Spec(
                name = "http_request",
                description = "Send a HEADLESS HTTP request to a URL. Use ONLY for public APIs that don't require browser login or cookies. NEVER use for websites requiring login, JavaScript rendering, or UI interaction. For logged-in sites, use open_app + UI automation (tap_element, type, etc.) instead. Supports GET, POST, PUT, DELETE with custom headers and body.",
                params = listOf(
                    ParamSpec("method", String::class, "HTTP method: GET, POST, PUT, DELETE, PATCH (default GET)."),
                    ParamSpec("url", String::class, "The full URL to request."),
                    ParamSpec("headers", Map::class, "Optional headers as a map of name to value."),
                    ParamSpec("body", String::class, "Optional request body string."),
                    ParamSpec("timeout_seconds", Int::class, "Request timeout in seconds (default 30).")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    HttpRequest(
                        method = args["method"] as? String ?: "GET",
                        url = args["url"] as String,
                        headers = args["headers"] as? Map<String, String>,
                        body = args["body"] as? String,
                        timeoutSeconds = args["timeout_seconds"] as? Int ?: 30
                    )
                }
            ),
            "visit_web" to Spec(
                name = "visit_web",
                description = "HEADLESS web page fetch - fetches raw HTML, NOT a real browser. Use ONLY for extracting content from PUBLIC pages that don't require login, JavaScript, or cookies. NEVER use for: (a) sites requiring login like x.com, gmail, facebook; (b) sites needing JavaScript rendering; (c) interactive tasks like clicking buttons or checking notifications. For those, use open_app + UI automation (tap_element, type, etc.) instead.",
                params = listOf(
                    ParamSpec("url", String::class, "The URL to visit."),
                    ParamSpec("max_content_length", Int::class, "Maximum content length to return (default 10000 chars).")
                ),
                build = { args -> VisitWeb(args["url"] as String, args["max_content_length"] as? Int ?: 10000) }
            ),

            // Shell
            "execute_shell" to Spec(
                name = "execute_shell",
                description = "Execute a shell command on the device and return the output.",
                params = listOf(
                    ParamSpec("command", String::class, "The shell command to execute."),
                    ParamSpec("timeout_seconds", Int::class, "Command timeout in seconds (default 30).")
                ),
                build = { args -> ExecuteShell(args["command"] as String, args["timeout_seconds"] as? Int ?: 30) }
            ),

            // Calculator
            "calculate" to Spec(
                name = "calculate",
                description = "Evaluate a mathematical expression. Supports arithmetic, unit conversions, date math, and statistics.",
                params = listOf(ParamSpec("expression", String::class, "The math expression to evaluate.")),
                build = { args -> Calculate(args["expression"] as String) }
            ),

            // Device Info
            "device_info" to Spec(
                name = "device_info",
                description = "Get comprehensive device information including model, OS version, screen size, memory, storage, battery, and CPU.",
                params = emptyList(),
                build = { _ -> GetDeviceInfo }
            ),

            // Memory
            "query_memory" to Spec(
                name = "query_memory",
                description = "Search the AI's memory graph for relevant memories using similarity search.",
                params = listOf(
                    ParamSpec("query", String::class, "The search query."),
                    ParamSpec("limit", Int::class, "Maximum number of results to return (default 5).")
                ),
                build = { args -> QueryMemory(args["query"] as String, args["limit"] as? Int ?: 5) }
            ),
            "create_memory" to Spec(
                name = "create_memory",
                description = "Create a new memory entry in the AI's memory graph.",
                params = listOf(
                    ParamSpec("title", String::class, "The memory title/identifier."),
                    ParamSpec("content", String::class, "The memory content."),
                    ParamSpec("tags", List::class, "Optional list of tags for categorization.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    CreateMemory(
                        title = args["title"] as String,
                        content = args["content"] as String,
                        tags = args["tags"] as? List<String>
                    )
                }
            ),
            "update_memory" to Spec(
                name = "update_memory",
                description = "Update an existing memory by title.",
                params = listOf(
                    ParamSpec("title", String::class, "The memory title to update."),
                    ParamSpec("content", String::class, "The new content.")
                ),
                build = { args -> UpdateMemory(args["title"] as String, args["content"] as String) }
            ),
            "delete_memory" to Spec(
                name = "delete_memory",
                description = "Delete a memory by title.",
                params = listOf(ParamSpec("title", String::class, "The memory title to delete.")),
                build = { args -> DeleteMemory(args["title"] as String) }
            ),

            // Screenshot
            "capture_screenshot" to Spec(
                name = "capture_screenshot",
                description = "Capture the current screen and optionally save it. Returns the screenshot file path.",
                params = listOf(
                    ParamSpec("save_as", String::class, "Optional filename to save the screenshot as (e.g., 'screen.png').")
                ),
                build = { args -> CaptureScreenshot(args["save_as"] as? String) }
            ),

            // Mini-App Creation
            "create_mini_app" to Spec(
                name = "create_mini_app",
                description = "Create an interactive mini-app (HTML/CSS/JS) that the user can launch from the app. Generate complete, self-contained HTML. Use for calculators, trackers, dashboards, or any interactive tool the user requests.",
                params = listOf(
                    ParamSpec("name", String::class, "Name of the mini-app."),
                    ParamSpec("html", String::class, "Complete HTML content (include CSS in <style> and JS in <script> tags)."),
                    ParamSpec("type", String::class, "App type: 'persistent' (default) or 'ephemeral'."),
                    ParamSpec("css", String::class, "Optional separate CSS. Can embed in HTML instead."),
                    ParamSpec("javascript", String::class, "Optional separate JS. Can embed in HTML instead."),
                    ParamSpec("description", String::class, "Brief description of what the app does.")
                ),
                build = { args ->
                    CreateMiniApp(
                        name = args["name"] as String,
                        html = args["html"] as String,
                        type = args["type"] as? String ?: "persistent",
                        css = args["css"] as? String ?: "",
                        javascript = args["javascript"] as? String ?: "",
                        description = args["description"] as? String ?: ""
                    )
                }
            ),
            "list_mini_apps" to Spec(
                name = "list_mini_apps",
                description = "List all existing mini-apps with their names, IDs, and types.",
                params = emptyList(),
                build = { _ -> ListMiniApps }
            ),
            "delete_mini_app" to Spec(
                name = "delete_mini_app",
                description = "Delete an existing mini-app by its ID.",
                params = listOf(ParamSpec("app_id", String::class, "The ID of the mini-app to delete.")),
                build = { args -> DeleteMiniApp(args["app_id"] as String) }
            ),
        )

        fun getAllSpecs(): Collection<Spec> {
            return allSpecs.values
        }
    }
}
