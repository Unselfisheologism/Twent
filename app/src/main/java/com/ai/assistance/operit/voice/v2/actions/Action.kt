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

    // OpenUI Generative UI Tool
    data class RenderOpenUI(
        val code: String,
        val title: String = "OpenUI",
        val type: String = "persistent"
    ) : Action()

    // ========== Workflow Tools ==========
    data object GetAllWorkflows : Action()
    data class GetWorkflow(val workflowId: String) : Action()
    data class CreateWorkflow(
        val name: String,
        val description: String = "",
        val nodes: String = "[]",
        val connections: String = "[]",
        val enabled: Boolean = true
    ) : Action()
    data class UpdateWorkflow(
        val workflowId: String,
        val name: String,
        val description: String = "",
        val nodes: String = "[]",
        val connections: String = "[]",
        val enabled: Boolean = true
    ) : Action()
    data class DeleteWorkflow(val workflowId: String) : Action()
    data class TriggerWorkflow(val workflowId: String) : Action()

    // ========== Chat Management Tools ==========
    data object StartChatService : Action()
    data object StopChatService : Action()
    data class CreateNewChat(val group: String = "") : Action()
    data object ListChats : Action()
    data class FindChat(val query: String) : Action()
    data class SwitchChat(val chatId: String) : Action()
    data class SendMessageToAI(val message: String) : Action()
    data object ListCharacterCards : Action()
    data class GetChatMessages(val chatId: String, val order: String = "desc", val limit: Int = 50) : Action()
    data class AgentStatus(val chatId: String) : Action()

    // ========== Tasker Tools ==========
    data class TriggerTaskerEvent(
        val taskType: String,
        val arg1: String = "",
        val arg2: String = "",
        val arg3: String = "",
        val arg4: String = "",
        val arg5: String = "",
        val argsJson: String = ""
    ) : Action()

    // ========== FFmpeg Tools ==========
    data class FFmpegExecute(val command: String) : Action()
    data object FFmpegInfo : Action()
    data class FFmpegConvert(
        val inputPath: String,
        val outputPath: String,
        val format: String = "",
        val resolution: String = "",
        val bitrate: String = "",
        val audioCodec: String = "",
        val videoCodec: String = ""
    ) : Action()

    // ========== Package System ==========
    data class UsePackage(val packageName: String) : Action()

    // ========== Extended File Tools ==========
    data class FileExists(val path: String, val environment: String = "android") : Action()
    data class MoveFile(
        val source: String,
        val destination: String,
        val environment: String = "android"
    ) : Action()
    data class CopyFile(
        val source: String,
        val destination: String,
        val recursive: Boolean = false,
        val sourceEnvironment: String = "android",
        val destEnvironment: String = "android"
    ) : Action()
    data class FileInfo(val path: String, val environment: String = "android") : Action()
    data class MakeDirectory(
        val path: String,
        val environment: String = "android",
        val createParents: Boolean = false
    ) : Action()
    data class FindFiles(
        val path: String,
        val environment: String = "android",
        val pattern: String = "*",
        val maxDepth: Int = 10,
        val usePathPattern: Boolean = false,
        val caseInsensitive: Boolean = false
    ) : Action()
    data class GrepCode(
        val path: String,
        val environment: String = "android",
        val pattern: String,
        val filePattern: String = "*",
        val caseInsensitive: Boolean = false,
        val contextLines: Int = 2,
        val maxResults: Int = 50
    ) : Action()
    data class GrepContext(
        val path: String,
        val environment: String = "android",
        val intent: String,
        val filePattern: String = "*",
        val maxResults: Int = 10
    ) : Action()
    data class DownloadFile(
        val url: String,
        val destination: String,
        val environment: String = "android",
        val headers: Map<String, String>? = null,
        val visitKey: String? = null,
        val linkNumber: Int? = null,
        val imageNumber: Int? = null
    ) : Action()
    data class ReadFilePart(
        val path: String,
        val environment: String = "android",
        val startLine: Int = 0,
        val endLine: Int = 100
    ) : Action()
    data class ReadFileFull(val path: String, val environment: String = "android") : Action()
    data class ReadFileBinary(val path: String, val environment: String = "android") : Action()
    data class WriteFileBinary(
        val path: String,
        val base64Content: String,
        val environment: String = "android"
    ) : Action()
    data class DeleteFile(
        val path: String,
        val environment: String = "android",
        val recursive: Boolean = false
    ) : Action()
    data class ApplyFile(
        val path: String,
        val environment: String = "android",
        val type: String = "text",
        val old: String,
        val new: String
    ) : Action()
    data class ZipFiles(
        val source: List<String>,
        val destination: String,
        val environment: String = "android"
    ) : Action()
    data class UnzipFiles(
        val source: String,
        val destination: String,
        val environment: String = "android"
    ) : Action()
    data class OpenFile(val path: String, val environment: String = "android") : Action()
    data class ShareFile(
        val path: String,
        val environment: String = "android",
        val title: String = ""
    ) : Action()

    // ========== Extended HTTP Tools ==========
    data class MultipartRequest(
        val url: String,
        val method: String = "POST",
        val headers: Map<String, String>? = null,
        val formData: Map<String, String>? = null,
        val files: List<Map<String, String>>? = null
    ) : Action()
    data class ManageCookies(
        val action: String,
        val domain: String? = null,
        val cookies: List<Map<String, String>>? = null
    ) : Action()

    // ========== SSH Tools ==========
    data class SSHLogin(
        val host: String,
        val port: Int = 22,
        val username: String,
        val password: String,
        val enableReverseMount: Boolean = false
    ) : Action()
    data object SSHExit : Action()

    // ========== Extended System Tools ==========
    data class InstallApp(val path: String) : Action()
    data class UninstallApp(val packageName: String) : Action()
    data class GetNotifications(val limit: Int = 20, val includeOngoing: Boolean = true) : Action()
    data class GetDeviceLocation(
        val timeout: Long = 10000,
        val highAccuracy: Boolean = true,
        val includeAddress: Boolean = true
    ) : Action()

    // ========== Extended UI Automation Tools ==========
    data class ClickElement(
        val index: Int? = null,
        val text: String? = null,
        val contentDescription: String? = null
    ) : Action()
    data class ScrollLeft(val pixels: Int = 500) : Action()
    data class ScrollRight(val pixels: Int = 500) : Action()
    data object GetPageInfo : Action()
    data object GetCurrentActivity : Action()

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
        // NOTE: create_mini_app is FIRST so the LLM always sees it even if prompt is truncated.
        private val allSpecs: Map<String, Spec> = mapOf(
            "create_mini_app" to Spec(
                name = "create_mini_app",
                description = "Create an interactive mini-app (HTML/CSS/JS) that the user can launch from the app. Generate COMPLETE, self-contained HTML with CSS in <style> and JS in <script> tags. Use when user asks for a calculator, tracker, dashboard, todo list, or any interactive tool.",
                params = listOf(
                    ParamSpec("name", String::class, "Name of the mini-app."),
                    ParamSpec("html", String::class, "Complete HTML with CSS in <style> and JS in <script>. Must be valid, self-contained HTML."),
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
            "render_openui" to Spec(
                name = "render_openui",
                description = "Render a professional, interactive UI from OpenUI Lang code — a compact declarative language that is 67% more token-efficient than raw HTML. Use INSTEAD of create_mini_app for dashboards, data tables, forms, charts, settings panels. OpenUI Lang: each line is `name = Component(args)`. Must include `root = Stack([...])`.",
                params = listOf(
                    ParamSpec("code", String::class, "OpenUI Lang code. Format: name = Component(args) per line. Must include root = ..."),
                    ParamSpec("title", String::class, "Title for the rendered UI."),
                    ParamSpec("type", String::class, "App type: 'persistent' (default) or 'ephemeral'.")
                ),
                build = { args ->
                    RenderOpenUI(
                        code = args["code"] as String,
                        title = args["title"] as? String ?: "OpenUI",
                        type = args["type"] as? String ?: "persistent"
                    )
                }
            ),
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

            // ========== Workflow Tools ==========
            "get_all_workflows" to Spec(
                name = "get_all_workflows",
                description = "List all automation workflows.",
                params = emptyList(),
                build = { _ -> GetAllWorkflows }
            ),
            "get_workflow" to Spec(
                name = "get_workflow",
                description = "Get details of a specific workflow by ID.",
                params = listOf(ParamSpec("workflow_id", String::class, "The workflow ID.")),
                build = { args -> GetWorkflow(args["workflow_id"] as String) }
            ),
            "create_workflow" to Spec(
                name = "create_workflow",
                description = "Create a new automation workflow.",
                params = listOf(
                    ParamSpec("name", String::class, "Workflow name."),
                    ParamSpec("description", String::class, "Workflow description."),
                    ParamSpec("nodes", String::class, "JSON array of workflow nodes."),
                    ParamSpec("connections", String::class, "JSON array of node connections."),
                    ParamSpec("enabled", Boolean::class, "Whether the workflow is enabled (default true).")
                ),
                build = { args ->
                    CreateWorkflow(
                        name = args["name"] as String,
                        description = args["description"] as? String ?: "",
                        nodes = args["nodes"] as? String ?: "[]",
                        connections = args["connections"] as? String ?: "[]",
                        enabled = args["enabled"] as? Boolean ?: true
                    )
                }
            ),
            "update_workflow" to Spec(
                name = "update_workflow",
                description = "Update an existing workflow.",
                params = listOf(
                    ParamSpec("workflow_id", String::class, "The workflow ID."),
                    ParamSpec("name", String::class, "Workflow name."),
                    ParamSpec("description", String::class, "Workflow description."),
                    ParamSpec("nodes", String::class, "JSON array of nodes."),
                    ParamSpec("connections", String::class, "JSON array of connections."),
                    ParamSpec("enabled", Boolean::class, "Whether enabled.")
                ),
                build = { args ->
                    UpdateWorkflow(
                        workflowId = args["workflow_id"] as String,
                        name = args["name"] as String,
                        description = args["description"] as? String ?: "",
                        nodes = args["nodes"] as? String ?: "[]",
                        connections = args["connections"] as? String ?: "[]",
                        enabled = args["enabled"] as? Boolean ?: true
                    )
                }
            ),
            "delete_workflow" to Spec(
                name = "delete_workflow",
                description = "Delete a workflow by ID.",
                params = listOf(ParamSpec("workflow_id", String::class, "The workflow ID to delete.")),
                build = { args -> DeleteWorkflow(args["workflow_id"] as String) }
            ),
            "trigger_workflow" to Spec(
                name = "trigger_workflow",
                description = "Execute/trigger a workflow by ID.",
                params = listOf(ParamSpec("workflow_id", String::class, "The workflow ID to trigger.")),
                build = { args -> TriggerWorkflow(args["workflow_id"] as String) }
            ),

            // ========== Chat Management Tools ==========
            "start_chat_service" to Spec(
                name = "start_chat_service",
                description = "Start the floating chat overlay service.",
                params = emptyList(),
                build = { _ -> StartChatService }
            ),
            "stop_chat_service" to Spec(
                name = "stop_chat_service",
                description = "Stop the chat overlay service.",
                params = emptyList(),
                build = { _ -> StopChatService }
            ),
            "create_new_chat" to Spec(
                name = "create_new_chat",
                description = "Create a new chat session.",
                params = listOf(ParamSpec("group", String::class, "Optional group name.")),
                build = { args -> CreateNewChat(args["group"] as? String ?: "") }
            ),
            "list_chats" to Spec(
                name = "list_chats",
                description = "List all chat sessions.",
                params = emptyList(),
                build = { _ -> ListChats }
            ),
            "find_chat" to Spec(
                name = "find_chat",
                description = "Find a chat session by title/query.",
                params = listOf(ParamSpec("query", String::class, "Search query for chat title.")),
                build = { args -> FindChat(args["query"] as String) }
            ),
            "switch_chat" to Spec(
                name = "switch_chat",
                description = "Switch to a different chat session.",
                params = listOf(ParamSpec("chat_id", String::class, "The chat ID to switch to.")),
                build = { args -> SwitchChat(args["chat_id"] as String) }
            ),
            "send_message_to_ai" to Spec(
                name = "send_message_to_ai",
                description = "Send a message to the AI in the current chat.",
                params = listOf(ParamSpec("message", String::class, "The message to send.")),
                build = { args -> SendMessageToAI(args["message"] as String) }
            ),
            "list_character_cards" to Spec(
                name = "list_character_cards",
                description = "List all character/role cards.",
                params = emptyList(),
                build = { _ -> ListCharacterCards }
            ),
            "get_chat_messages" to Spec(
                name = "get_chat_messages",
                description = "Get messages from a specific chat.",
                params = listOf(
                    ParamSpec("chat_id", String::class, "The chat ID."),
                    ParamSpec("order", String::class, "Order: 'asc' or 'desc' (default 'desc')."),
                    ParamSpec("limit", Int::class, "Max messages to return (default 50).")
                ),
                build = { args ->
                    GetChatMessages(
                        chatId = args["chat_id"] as String,
                        order = args["order"] as? String ?: "desc",
                        limit = args["limit"] as? Int ?: 50
                    )
                }
            ),
            "agent_status" to Spec(
                name = "agent_status",
                description = "Check if a chat is currently being processed.",
                params = listOf(ParamSpec("chat_id", String::class, "The chat ID to check.")),
                build = { args -> AgentStatus(args["chat_id"] as String) }
            ),

            // ========== Tasker Tools ==========
            "trigger_tasker_event" to Spec(
                name = "trigger_tasker_event",
                description = "Trigger a Tasker event with arguments.",
                params = listOf(
                    ParamSpec("task_type", String::class, "The Tasker task type."),
                    ParamSpec("arg1", String::class, "First argument."),
                    ParamSpec("arg2", String::class, "Second argument."),
                    ParamSpec("arg3", String::class, "Third argument."),
                    ParamSpec("arg4", String::class, "Fourth argument."),
                    ParamSpec("arg5", String::class, "Fifth argument."),
                    ParamSpec("args_json", String::class, "JSON string of additional arguments.")
                ),
                build = { args ->
                    TriggerTaskerEvent(
                        taskType = args["task_type"] as String,
                        arg1 = args["arg1"] as? String ?: "",
                        arg2 = args["arg2"] as? String ?: "",
                        arg3 = args["arg3"] as? String ?: "",
                        arg4 = args["arg4"] as? String ?: "",
                        arg5 = args["arg5"] as? String ?: "",
                        argsJson = args["args_json"] as? String ?: ""
                    )
                }
            ),

            // ========== FFmpeg Tools ==========
            "ffmpeg_execute" to Spec(
                name = "ffmpeg_execute",
                description = "Execute an FFmpeg command (DANGEROUS - can modify files).",
                params = listOf(ParamSpec("command", String::class, "The FFmpeg command to execute.")),
                build = { args -> FFmpegExecute(args["command"] as String) }
            ),
            "ffmpeg_info" to Spec(
                name = "ffmpeg_info",
                description = "Get FFmpeg capabilities and configuration.",
                params = emptyList(),
                build = { _ -> FFmpegInfo }
            ),
            "ffmpeg_convert" to Spec(
                name = "ffmpeg_convert",
                description = "Convert video/audio file using FFmpeg (DANGEROUS).",
                params = listOf(
                    ParamSpec("input_path", String::class, "Input file path."),
                    ParamSpec("output_path", String::class, "Output file path."),
                    ParamSpec("format", String::class, "Output format."),
                    ParamSpec("resolution", String::class, "Output resolution."),
                    ParamSpec("bitrate", String::class, "Target bitrate."),
                    ParamSpec("audio_codec", String::class, "Audio codec."),
                    ParamSpec("video_codec", String::class, "Video codec.")
                ),
                build = { args ->
                    FFmpegConvert(
                        inputPath = args["input_path"] as String,
                        outputPath = args["output_path"] as String,
                        format = args["format"] as? String ?: "",
                        resolution = args["resolution"] as? String ?: "",
                        bitrate = args["bitrate"] as? String ?: "",
                        audioCodec = args["audio_codec"] as? String ?: "",
                        videoCodec = args["video_codec"] as? String ?: ""
                    )
                }
            ),

            // ========== Package System ==========
            "use_package" to Spec(
                name = "use_package",
                description = "Activate a package (JS packages, MCP servers, Skills) to access its tools.",
                params = listOf(ParamSpec("package_name", String::class, "The package name to activate.")),
                build = { args -> UsePackage(args["package_name"] as String) }
            ),

            // ========== Extended File Tools ==========
            "file_exists" to Spec(
                name = "file_exists",
                description = "Check if a file or directory exists.",
                params = listOf(
                    ParamSpec("path", String::class, "The file/directory path."),
                    ParamSpec("environment", String::class, "Environment: 'android' or 'ssh' (default 'android').")
                ),
                build = { args ->
                    FileExists(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "move_file" to Spec(
                name = "move_file",
                description = "Move or rename a file (DANGEROUS).",
                params = listOf(
                    ParamSpec("source", String::class, "Source path."),
                    ParamSpec("destination", String::class, "Destination path."),
                    ParamSpec("environment", String::class, "Environment: 'android' or 'ssh'.")
                ),
                build = { args ->
                    MoveFile(
                        source = args["source"] as String,
                        destination = args["destination"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "copy_file" to Spec(
                name = "copy_file",
                description = "Copy a file or directory.",
                params = listOf(
                    ParamSpec("source", String::class, "Source path."),
                    ParamSpec("destination", String::class, "Destination path."),
                    ParamSpec("recursive", Boolean::class, "Copy recursively for directories."),
                    ParamSpec("source_environment", String::class, "Source environment."),
                    ParamSpec("dest_environment", String::class, "Destination environment.")
                ),
                build = { args ->
                    CopyFile(
                        source = args["source"] as String,
                        destination = args["destination"] as String,
                        recursive = args["recursive"] as? Boolean ?: false,
                        sourceEnvironment = args["source_environment"] as? String ?: "android",
                        destEnvironment = args["dest_environment"] as? String ?: "android"
                    )
                }
            ),
            "file_info" to Spec(
                name = "file_info",
                description = "Get detailed file information.",
                params = listOf(
                    ParamSpec("path", String::class, "The file path."),
                    ParamSpec("environment", String::class, "Environment: 'android' or 'ssh'.")
                ),
                build = { args ->
                    FileInfo(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "make_directory" to Spec(
                name = "make_directory",
                description = "Create a directory.",
                params = listOf(
                    ParamSpec("path", String::class, "Directory path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("create_parents", Boolean::class, "Create parent directories if needed.")
                ),
                build = { args ->
                    MakeDirectory(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        createParents = args["create_parents"] as? Boolean ?: false
                    )
                }
            ),
            "find_files" to Spec(
                name = "find_files",
                description = "Search for files by pattern.",
                params = listOf(
                    ParamSpec("path", String::class, "Search starting path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("pattern", String::class, "Search pattern (default '*')."),
                    ParamSpec("max_depth", Int::class, "Maximum search depth."),
                    ParamSpec("use_path_pattern", Boolean::class, "Use path-based pattern matching."),
                    ParamSpec("case_insensitive", Boolean::class, "Case insensitive search.")
                ),
                build = { args ->
                    FindFiles(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        pattern = args["pattern"] as? String ?: "*",
                        maxDepth = args["max_depth"] as? Int ?: 10,
                        usePathPattern = args["use_path_pattern"] as? Boolean ?: false,
                        caseInsensitive = args["case_insensitive"] as? Boolean ?: false
                    )
                }
            ),
            "grep_code" to Spec(
                name = "grep_code",
                description = "Search code files with regex pattern.",
                params = listOf(
                    ParamSpec("path", String::class, "Search path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("pattern", String::class, "Regex pattern."),
                    ParamSpec("file_pattern", String::class, "File pattern filter."),
                    ParamSpec("case_insensitive", Boolean::class, "Case insensitive."),
                    ParamSpec("context_lines", Int::class, "Lines of context."),
                    ParamSpec("max_results", Int::class, "Maximum results.")
                ),
                build = { args ->
                    GrepCode(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        pattern = args["pattern"] as String,
                        filePattern = args["file_pattern"] as? String ?: "*",
                        caseInsensitive = args["case_insensitive"] as? Boolean ?: false,
                        contextLines = args["context_lines"] as? Int ?: 2,
                        maxResults = args["max_results"] as? Int ?: 50
                    )
                }
            ),
            "grep_context" to Spec(
                name = "grep_context",
                description = "Semantic code search with context.",
                params = listOf(
                    ParamSpec("path", String::class, "Search path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("intent", String::class, "Search intent."),
                    ParamSpec("file_pattern", String::class, "File pattern filter."),
                    ParamSpec("max_results", Int::class, "Maximum results.")
                ),
                build = { args ->
                    GrepContext(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        intent = args["intent"] as String,
                        filePattern = args["file_pattern"] as? String ?: "*",
                        maxResults = args["max_results"] as? Int ?: 10
                    )
                }
            ),
            "download_file" to Spec(
                name = "download_file",
                description = "Download a file from the internet.",
                params = listOf(
                    ParamSpec("url", String::class, "Download URL."),
                    ParamSpec("destination", String::class, "Destination file path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("headers", Map::class, "Optional HTTP headers."),
                    ParamSpec("visit_key", String::class, "Visit key for linked downloads."),
                    ParamSpec("link_number", Int::class, "Link number to download."),
                    ParamSpec("image_number", Int::class, "Image number to download.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    DownloadFile(
                        url = args["url"] as String,
                        destination = args["destination"] as String,
                        environment = args["environment"] as? String ?: "android",
                        headers = args["headers"] as? Map<String, String>,
                        visitKey = args["visit_key"] as? String,
                        linkNumber = args["link_number"] as? Int,
                        imageNumber = args["image_number"] as? Int
                    )
                }
            ),
            "read_file_part" to Spec(
                name = "read_file_part",
                description = "Read a file by line range.",
                params = listOf(
                    ParamSpec("path", String::class, "File path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("start_line", Int::class, "Start line number."),
                    ParamSpec("end_line", Int::class, "End line number.")
                ),
                build = { args ->
                    ReadFilePart(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        startLine = args["start_line"] as? Int ?: 0,
                        endLine = args["end_line"] as? Int ?: 100
                    )
                }
            ),
            "read_file_full" to Spec(
                name = "read_file_full",
                description = "Read full file content (no size limit).",
                params = listOf(
                    ParamSpec("path", String::class, "File path."),
                    ParamSpec("environment", String::class, "Environment.")
                ),
                build = { args ->
                    ReadFileFull(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "read_file_binary" to Spec(
                name = "read_file_binary",
                description = "Read binary file content (Base64 encoded).",
                params = listOf(
                    ParamSpec("path", String::class, "File path."),
                    ParamSpec("environment", String::class, "Environment.")
                ),
                build = { args ->
                    ReadFileBinary(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "write_file_binary" to Spec(
                name = "write_file_binary",
                description = "Write binary file content (DANGEROUS).",
                params = listOf(
                    ParamSpec("path", String::class, "File path."),
                    ParamSpec("base64_content", String::class, "Base64 encoded content."),
                    ParamSpec("environment", String::class, "Environment.")
                ),
                build = { args ->
                    WriteFileBinary(
                        path = args["path"] as String,
                        base64Content = args["base64_content"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "delete_file" to Spec(
                name = "delete_file",
                description = "Delete a file or directory (DANGEROUS).",
                params = listOf(
                    ParamSpec("path", String::class, "File/directory path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("recursive", Boolean::class, "Delete recursively for directories.")
                ),
                build = { args ->
                    DeleteFile(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        recursive = args["recursive"] as? Boolean ?: false
                    )
                }
            ),
            "apply_file" to Spec(
                name = "apply_file",
                description = "Apply edits/diff to a file (DANGEROUS).",
                params = listOf(
                    ParamSpec("path", String::class, "File path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("type", String::class, "File type."),
                    ParamSpec("old", String::class, "Old content to replace."),
                    ParamSpec("new", String::class, "New content.")
                ),
                build = { args ->
                    ApplyFile(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        type = args["type"] as? String ?: "text",
                        old = args["old"] as String,
                        new = args["new"] as String
                    )
                }
            ),
            "zip_files" to Spec(
                name = "zip_files",
                description = "Compress files to ZIP archive.",
                params = listOf(
                    ParamSpec("source", List::class, "List of source file paths."),
                    ParamSpec("destination", String::class, "Destination ZIP path."),
                    ParamSpec("environment", String::class, "Environment.")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    ZipFiles(
                        source = args["source"] as List<String>,
                        destination = args["destination"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "unzip_files" to Spec(
                name = "unzip_files",
                description = "Extract ZIP archive.",
                params = listOf(
                    ParamSpec("source", String::class, "ZIP file path."),
                    ParamSpec("destination", String::class, "Extraction directory."),
                    ParamSpec("environment", String::class, "Environment.")
                ),
                build = { args ->
                    UnzipFiles(
                        source = args["source"] as String,
                        destination = args["destination"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "open_file" to Spec(
                name = "open_file",
                description = "Open a file with its default application.",
                params = listOf(
                    ParamSpec("path", String::class, "File path."),
                    ParamSpec("environment", String::class, "Environment.")
                ),
                build = { args ->
                    OpenFile(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android"
                    )
                }
            ),
            "share_file" to Spec(
                name = "share_file",
                description = "Share a file with other apps.",
                params = listOf(
                    ParamSpec("path", String::class, "File path."),
                    ParamSpec("environment", String::class, "Environment."),
                    ParamSpec("title", String::class, "Share dialog title.")
                ),
                build = { args ->
                    ShareFile(
                        path = args["path"] as String,
                        environment = args["environment"] as? String ?: "android",
                        title = args["title"] as? String ?: ""
                    )
                }
            ),

            // ========== Extended HTTP Tools ==========
            "multipart_request" to Spec(
                name = "multipart_request",
                description = "Upload files via multipart/form-data request.",
                params = listOf(
                    ParamSpec("url", String::class, "Request URL."),
                    ParamSpec("method", String::class, "HTTP method (default POST)."),
                    ParamSpec("headers", Map::class, "Optional headers."),
                    ParamSpec("form_data", Map::class, "Form data fields."),
                    ParamSpec("files", List::class, "Files to upload (list of file info maps).")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    MultipartRequest(
                        url = args["url"] as String,
                        method = args["method"] as? String ?: "POST",
                        headers = args["headers"] as? Map<String, String>,
                        formData = args["form_data"] as? Map<String, String>,
                        files = args["files"] as? List<Map<String, String>>
                    )
                }
            ),
            "manage_cookies" to Spec(
                name = "manage_cookies",
                description = "Manage browser cookies.",
                params = listOf(
                    ParamSpec("action", String::class, "Action: 'get', 'set', 'delete', 'clear'."),
                    ParamSpec("domain", String::class, "Domain for cookie operations."),
                    ParamSpec("cookies", List::class, "Cookies to set (list of cookie maps).")
                ),
                build = { args ->
                    @Suppress("UNCHECKED_CAST")
                    ManageCookies(
                        action = args["action"] as String,
                        domain = args["domain"] as? String,
                        cookies = args["cookies"] as? List<Map<String, String>>
                    )
                }
            ),

            // ========== SSH Tools ==========
            "ssh_login" to Spec(
                name = "ssh_login",
                description = "Login to an SSH server.",
                params = listOf(
                    ParamSpec("host", String::class, "SSH host address."),
                    ParamSpec("port", Int::class, "SSH port (default 22)."),
                    ParamSpec("username", String::class, "SSH username."),
                    ParamSpec("password", String::class, "SSH password."),
                    ParamSpec("enable_reverse_mount", Boolean::class, "Enable reverse file mount.")
                ),
                build = { args ->
                    SSHLogin(
                        host = args["host"] as String,
                        port = args["port"] as? Int ?: 22,
                        username = args["username"] as String,
                        password = args["password"] as String,
                        enableReverseMount = args["enable_reverse_mount"] as? Boolean ?: false
                    )
                }
            ),
            "ssh_exit" to Spec(
                name = "ssh_exit",
                description = "Logout from the current SSH session.",
                params = emptyList(),
                build = { _ -> SSHExit }
            ),

            // ========== Extended System Tools ==========
            "install_app" to Spec(
                name = "install_app",
                description = "Install an APK from file path (DANGEROUS).",
                params = listOf(ParamSpec("path", String::class, "APK file path.")),
                build = { args -> InstallApp(args["path"] as String) }
            ),
            "uninstall_app" to Spec(
                name = "uninstall_app",
                description = "Uninstall an app by package name (DANGEROUS).",
                params = listOf(ParamSpec("package_name", String::class, "App package name.")),
                build = { args -> UninstallApp(args["package_name"] as String) }
            ),
            "get_notifications" to Spec(
                name = "get_notifications",
                description = "Read device notifications.",
                params = listOf(
                    ParamSpec("limit", Int::class, "Max notifications to return (default 20)."),
                    ParamSpec("include_ongoing", Boolean::class, "Include ongoing notifications.")
                ),
                build = { args ->
                    GetNotifications(
                        limit = args["limit"] as? Int ?: 20,
                        includeOngoing = args["include_ongoing"] as? Boolean ?: true
                    )
                }
            ),
            "get_device_location" to Spec(
                name = "get_device_location",
                description = "Get device GPS location.",
                params = listOf(
                    ParamSpec("timeout", Int::class, "Timeout in milliseconds."),
                    ParamSpec("high_accuracy", Boolean::class, "Use high accuracy mode."),
                    ParamSpec("include_address", Boolean::class, "Include reverse geocoded address.")
                ),
                build = { args ->
                    GetDeviceLocation(
                        timeout = (args["timeout"] as? Int ?: 10000).toLong(),
                        highAccuracy = args["high_accuracy"] as? Boolean ?: true,
                        includeAddress = args["include_address"] as? Boolean ?: true
                    )
                }
            ),

            // ========== Extended UI Automation Tools ==========
            "click_element" to Spec(
                name = "click_element",
                description = "Click a UI element by index, text, or content description.",
                params = listOf(
                    ParamSpec("index", Int::class, "Element index."),
                    ParamSpec("text", String::class, "Element text to match."),
                    ParamSpec("content_description", String::class, "Element content description.")
                ),
                build = { args ->
                    ClickElement(
                        index = args["index"] as? Int,
                        text = args["text"] as? String,
                        contentDescription = args["content_description"] as? String
                    )
                }
            ),
            "scroll_left" to Spec(
                name = "scroll_left",
                description = "Scroll left by pixels.",
                params = listOf(ParamSpec("pixels", Int::class, "Pixels to scroll.")),
                build = { args -> ScrollLeft(args["pixels"] as Int) }
            ),
            "scroll_right" to Spec(
                name = "scroll_right",
                description = "Scroll right by pixels.",
                params = listOf(ParamSpec("pixels", Int::class, "Pixels to scroll.")),
                build = { args -> ScrollRight(args["pixels"] as Int) }
            ),
            "get_page_info" to Spec(
                name = "get_page_info",
                description = "Get current UI page information.",
                params = emptyList(),
                build = { _ -> GetPageInfo }
            ),
            "get_current_activity" to Spec(
                name = "get_current_activity",
                description = "Get the current foreground activity name.",
                params = emptyList(),
                build = { _ -> GetCurrentActivity }
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
