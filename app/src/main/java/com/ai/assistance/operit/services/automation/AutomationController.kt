package com.ai.assistance.operit.services.automation

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.automation.Eyes
import com.ai.assistance.operit.api.automation.Finger
import com.ai.assistance.operit.core.agent.Agent
import com.ai.assistance.operit.core.agent.v2.Agent as V2Agent
import com.ai.assistance.operit.core.agent.actions.Action
import com.ai.assistance.operit.core.agent.actions.ActionExecutor
import com.ai.assistance.operit.core.agent.fs.FileSystem
import com.ai.assistance.operit.core.agent.llm.LlmApi
import com.ai.assistance.operit.core.agent.llm.LlmMessage
import com.ai.assistance.operit.core.agent.message.MessageManager
import com.ai.assistance.operit.core.agent.model.AgentOutput
import com.ai.assistance.operit.core.agent.model.AgentSettings
import com.ai.assistance.operit.core.agent.perception.Perception
import com.ai.assistance.operit.core.agent.perception.SemanticParser
import com.ai.assistance.operit.core.agent.perception.ScreenAnalysis
import com.ai.assistance.operit.api.chat.EnhancedAIService
import com.ai.assistance.operit.api.chat.llmprovider.AIService
import com.ai.assistance.operit.data.model.ChatMessage
import com.ai.assistance.operit.core.agent.llm.MessageRole
import com.ai.assistance.operit.data.preferences.UserPreferencesManager
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

@RequiresApi(Build.VERSION_CODES.R)
class AutomationController private constructor(private val context: Context) {

    companion object {
        private const val TAG = "AutomationController"
        
        @Volatile
        private var INSTANCE: AutomationController? = null

        fun getInstance(context: Context): AutomationController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AutomationController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val eyes by lazy { Eyes(context) }
    private val finger by lazy { Finger(context) }
    private val semanticParser by lazy { SemanticParser() }
    private val perception by lazy { Perception(eyes, semanticParser) }
    private val fileSystem by lazy { FileSystem(context) }
    
    private var agent: V2Agent? = null
    private var isRunning = false
    
    private val agentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val systemPrompt = """
You are an automation agent that controls an Android phone. Your ONLY output format is JSON. NO plain text. NO explanations. NO conversational responses.

═══════════════════════════════════════════════════════════════════════════════
ABSOLUTE RULES - VIOLATION = TASK FAILURE
═══════════════════════════════════════════════════════════════════════════════

1. OUTPUT MUST BE VALID JSON - Nothing else. No text before or after.
2. START with "{" and END with "}" - No markdown, no code blocks, no backticks
3. Every response MUST have an "action" array with at least one action
4. NEVER write plain text like "I'll tap the button" or "Let me do that"
5. ALWAYS respond in JSON even for errors or acknowledgment

═══════════════════════════════════════════════════════════════════════════════
JSON RESPONSE FORMAT (Copy this EXACT structure)
═══════════════════════════════════════════════════════════════════════════════

{
  "thinking": "Brief reasoning about current state and next action",
  "evaluationPreviousGoal": "What happened from the previous action result",
  "memory": "What important info to remember for future steps",
  "nextGoal": "What you plan to do in this step",
  "action": [
    {"tap": {"x": 500, "y": 800}},
    {"type_text": {"text": "hello"}}
  ]
}

═══════════════════════════════════════════════════════════════════════════════
AVAILABLE ACTIONS (Use ONLY these exact names)
═══════════════════════════════════════════════════════════════════════════════

COORDINATE ACTIONS:
- {"tap": {"x": 500, "y": 800}} - Tap at coordinates
- {"long_press": {"x": 500, "y": 800, "duration_ms": 1500}} - Long press
- {"double_tap": {"x": 500, "y": 800}} - Double tap
- {"swipe": {"start_x": 500, "start_y": 1000, "end_x": 500, "end_y": 500, "duration_ms": 500}} - Swipe
- {"swipe_left": {"pixels": 500}} - Swipe left
- {"swipe_right": {"pixels": 500}} - Swipe right
- {"swipe_up": {"pixels": 500}} - Swipe up
- {"swipe_down": {"pixels": 500}} - Swipe down

ELEMENT ACTIONS:
- {"click_element": {"index": 5}} - Click element by index
- {"click_element": {"text": "Submit"}} - Click by text
- {"click_element": {"content_description": "Menu"}} - Click by description

INPUT ACTIONS:
- {"type_text": {"text": "hello world"}} - Type text
- {"press_key": {"key": "enter"}} - Press key (enter, back, home, etc.)

SYSTEM ACTIONS:
- {"open_app": {"package_name": "com.whatsapp"}} - Open app
- {"back": {}} - Press back
- {"home": {}} - Go home
- {"switch_app": {}} - App switcher

SPECIAL ACTIONS:
- {"wait": {}} - Wait 1 second
- {"done": {"success": true, "message": "Task completed"}} - Finish task
- {"speak": {"text": "Task done"}} - Speak to user

═══════════════════════════════════════════════════════════════════════════════
SCREEN CONTEXT (Received at each step)
═══════════════════════════════════════════════════════════════════════════════

You receive:
- Current Activity name
- UI elements with indices (e.g., [0] Button, [1] TextField)
- Keyboard state
- Scroll availability

Use these indices to interact with elements. Numbers in [brackets] are indices.

═══════════════════════════════════════════════════════════════════════════════
EXAMPLES (These are the ONLY correct response formats)
═══════════════════════════════════════════════════════════════════════════════

Example 1 - Tap a button:
{"thinking":"I see a login button at index 0. I should tap it.","evaluationPreviousGoal":"App opened successfully","memory":"Login screen is now visible","nextGoal":"Tap the login button","action":[{"click_element":{"index":0}}]}

Example 2 - Type and swipe:
{"thinking":"I need to type 'hello' and scroll down to see more options","evaluationPreviousGoal":"Button tapped","memory":"Text field is now focused","nextGoal":"Type 'hello' then scroll","action":[{"type_text":{"text":"hello"}},{"swipe_down":{"pixels":300}}]}

Example 3 - Open app:
{"thinking":"User wants to open WhatsApp","evaluationPreviousGoal":"Previous task done","memory":"","nextGoal":"Open WhatsApp","action":[{"open_app":{"package_name":"com.whatsapp"}}]}

Example 4 - Done:
{"thinking":"I've found and saved the phone number as requested","evaluationPreviousGoal":"Phone number saved to file","memory":"Task complete","nextGoal":"Finish automation","action":[{"done":{"success":true,"message":"Found and saved phone number"}}]}

═══════════════════════════════════════════════════════════════════════════════
FORBIDDEN PATTERNS (Your response must NEVER contain these)
═══════════════════════════════════════════════════════════════════════════════

❌ "I'll tap the button"
❌ "Let me scroll down first"
❌ "The button is at the top of the screen"
❌ "Okay, I'll open the app"
❌ "Sure, I can help with that"
❌ Any text that isn't JSON

✅ ALWAYS: {"action":[{"tap":{"x":500,"y":800}}]}

═══════════════════════════════════════════════════════════════════════════════
USER REQUEST (Your task to accomplish)
═══════════════════════════════════════════════════════════════════════════════

{user_request}

Now respond with JSON only. No text.
""".trimIndent()

    /**
     * Start an automation task
     * @param task The task description
     * @param maxSteps Maximum steps before stopping
     * @param onStatusChange Callback for status updates
     * @param onComplete Callback when task completes
     */
    fun startAutomation(
        task: String,
        maxSteps: Int = 150,
        onStatusChange: ((String) -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (isRunning) {
            Log.w(TAG, "Automation already running")
            onComplete?.invoke(false, "Automation already running")
            return
        }

        isRunning = true
        onStatusChange?.invoke("Starting automation: $task")
        
        val modifiedPrompt = systemPrompt.replace("{user_request}", task)
        val messageManager = com.ai.assistance.operit.core.agent.v2.message.MessageManager(
            context = context,
            task = task,
            fileSystem = fileSystem,
            settings = com.ai.assistance.operit.core.agent.v2.AgentModels.AgentSettings(maxSteps = maxSteps)
        )
        
        // Create LLM API wrapper that uses Operit's existing AI service
        val operitLlmApi = com.ai.assistance.operit.core.agent.v2.llm.OperitLlmApi(
            modelName = "default",
            context = context
        )
        
        val settings = com.ai.assistance.operit.core.agent.v2.AgentModels.AgentSettings(maxSteps = maxSteps)
        
        agent = com.ai.assistance.operit.core.agent.v2.Agent(
            settings = settings,
            memoryManager = messageManager,
            perception = perception,
            llmApi = operitLlmApi,
            actionExecutor = com.ai.assistance.operit.core.agent.v2.actions.ActionExecutor(finger),
            fileSystem = fileSystem,
            context = context
        )

        agentScope.launch {
            try {
                Log.d(TAG, "Starting agent loop for task: $task")
                agent?.run(task, maxSteps)
                Log.d(TAG, "Automation completed successfully")
                onComplete?.invoke(true, "Task completed")
            } catch (e: Exception) {
                Log.e(TAG, "Automation failed", e)
                onComplete?.invoke(false, "Error: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    /**
     * Stop the current automation
     */
    fun stopAutomation() {
        Log.d(TAG, "Stopping automation")
        isRunning = false
        agentScope.cancel()
        agent = null
    }

    /**
     * Check if automation is running
     */
    fun isRunning(): Boolean = isRunning

    /**
     * Run automation task synchronously - waits for completion
     */
    fun runAutomationTask(
        task: String,
        maxSteps: Int = 150,
        onStatusChange: ((String) -> Unit)? = null,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        if (isRunning) {
            Log.w(TAG, "Automation already running")
            onComplete?.invoke(false, "Automation already running")
            return
        }

        isRunning = true
        onStatusChange?.invoke("Starting automation: $task")
        
        val modifiedPrompt = systemPrompt.replace("{user_request}", task)
        val messageManager = com.ai.assistance.operit.core.agent.v2.message.MessageManager(
            context = context,
            task = task,
            fileSystem = fileSystem,
            settings = com.ai.assistance.operit.core.agent.v2.AgentModels.AgentSettings(maxSteps = maxSteps)
        )
        
        val operitLlmApi = com.ai.assistance.operit.core.agent.v2.llm.OperitLlmApi(
            modelName = "default",
            context = context
        )
        
        val settings = com.ai.assistance.operit.core.agent.v2.AgentModels.AgentSettings(maxSteps = maxSteps)
        
        agent = com.ai.assistance.operit.core.agent.v2.Agent(
            settings = settings,
            memoryManager = messageManager,
            perception = perception,
            llmApi = operitLlmApi,
            actionExecutor = com.ai.assistance.operit.core.agent.v2.actions.ActionExecutor(finger),
            fileSystem = fileSystem,
            context = context
        )

        agentScope.launch {
            try {
                Log.d(TAG, "Starting agent loop for task: $task")
                agent?.run(task, maxSteps)
                Log.d(TAG, "Automation completed successfully")
                onComplete?.invoke(true, "Task completed")
            } catch (e: Exception) {
                Log.e(TAG, "Automation failed", e)
                onComplete?.invoke(false, "Error: ${e.message}")
            } finally {
                isRunning = false
            }
        }
    }

    /**
     * Get current screen analysis
     */
    suspend fun getScreenAnalysis(): ScreenAnalysis? {
        return try {
            perception.analyze()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze screen", e)
            null
        }
    }

    /**
     * Perform a single action directly (without full agent loop)
     */
    suspend fun performAction(action: Action): Boolean {
        return try {
            val screenState = perception.analyze()
            val result = ActionExecutor.execute(action, screenState, context, fileSystem)
            result.error == null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform action", e)
            false
        }
    }

    /**
     * Quick actions for simple interactions
     */
    fun tap(x: Int, y: Int) = finger.tap(x, y)
    fun longPress(x: Int, y: Int) = finger.longPress(x, y)
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 1000) = finger.swipe(x1, y1, x2, y2, duration)
    fun typeText(text: String) = finger.type(text)
    fun goBack() = finger.back()
    fun goHome() = finger.home()
    fun switchApp() = finger.switchApp()
    fun openApp(packageName: String) = finger.openApp(packageName)
}

/**
 * LLM API implementation that uses Operit's existing AI service
 */
@RequiresApi(Build.VERSION_CODES.R)
class OperitLlmApi(private val context: Context) : LlmApi {
    private val TAG = "OperitLlmApi"
    
    override suspend fun generateAgentOutput(messages: List<LlmMessage>): AgentOutput? {
        return try {
            // Get the enhanced AI service from the running chat service
            val aiService = getAiService() ?: run {
                Log.e(TAG, "No AI service available")
                return null
            }
            
            // Convert messages to chat format
            val systemMessage = messages.find { it.role == MessageRole.SYSTEM }?.content ?: ""
            val userMessages = messages.filter { it.role == MessageRole.USER }.map { it.content }
            
            // Build prompt with system and context
            val prompt = buildString {
                append(systemMessage)
                append("\n\n")
                userMessages.lastOrNull()?.let { append(it) }
            }
            
            Log.d(TAG, "Sending prompt to LLM")
            
            // Call the AI service (non-streaming for agent loop)
            val response = aiService.completions(
                prompt = prompt,
                imageUris = emptyList(),
                toolInvocations = emptyList()
            )
            
            // Parse JSON response to AgentOutput
            parseAgentOutput(response)
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate output", e)
            null
        }
    }
    
    private fun getAiService(): com.ai.assistance.operit.api.chat.EnhancedAIService? {
        // Try to get from FloatingChatService
        val chatService = com.ai.assistance.operit.services.FloatingChatService.getInstance()
        return chatService?.getAiService()
    }
    
    private fun parseAgentOutput(response: String): AgentOutput? {
        return try {
            val json = org.json.JSONObject(response)
            
            val thinking = json.optString("thinking", null).takeIf { it.isNotEmpty() }
            val evaluationPreviousGoal = json.optString("evaluationPreviousGoal", null).takeIf { it.isNotEmpty() }
            val memory = json.optString("memory", null).takeIf { it.isNotEmpty() }
            val nextGoal = json.optString("nextGoal", null).takeIf { it.isNotEmpty() }
            
            val actionArray = json.optJSONArray("action")
            val actions = mutableListOf<Action>()
            
            actionArray?.let { arr ->
                for (i in 0 until arr.length()) {
                    val actionObj = arr.getJSONObject(i)
                    val actionName = actionObj.keys().next()
                    val actionParams = actionObj.optJSONObject(actionName)
                    
                    val action = parseAction(actionName, actionParams)
                    if (action != null) {
                        actions.add(action)
                    }
                }
            }
            
            AgentOutput(
                thinking = thinking,
                memory = memory,
                nextGoal = nextGoal,
                action = actions
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse agent output", e)
            null
        }
    }
    
    private fun parseAction(name: String, params: org.json.JSONObject?): Action? {
        return when (name) {
            // Element-based actions
            "click_element" -> {
                val index = params?.optInt("index", -1) ?: -1
                val text = params?.optString("text", "")
                val contentDesc = params?.optString("content_description", "")
                when {
                    index >= 0 -> Action.ClickElement(index)
                    !text.isNullOrEmpty() -> Action.ClickElementByText(text)
                    !contentDesc.isNullOrEmpty() -> Action.ClickElementByDesc(contentDesc)
                    else -> null
                }
            }
            "tap_element" -> Action.TapElement(params?.optInt("element_id") ?: 0)
            "long_press_element" -> Action.LongPressElement(params?.optInt("element_id") ?: 0)
            "tap_element_input_text_and_enter" -> Action.TapElementInputTextPressEnter(
                params?.optInt("index") ?: 0,
                params?.optString("text") ?: ""
            )
            
            // Coordinate-based actions
            "tap" -> Action.TapAt(
                params?.optInt("x") ?: 0,
                params?.optInt("y") ?: 0
            )
            "tap_at" -> Action.TapAt(
                params?.optInt("x") ?: 0,
                params?.optInt("y") ?: 0
            )
            "long_press" -> Action.LongPressAt(
                params?.optInt("x") ?: 0,
                params?.optInt("y") ?: 0,
                params?.optLong("duration_ms") ?: 1500
            )
            "double_tap" -> Action.DoubleTapAt(
                params?.optInt("x") ?: 0,
                params?.optInt("y") ?: 0
            )
            "swipe" -> Action.Swipe(
                params?.optInt("start_x") ?: 0,
                params?.optInt("start_y") ?: 0,
                params?.optInt("end_x") ?: 0,
                params?.optInt("end_y") ?: 0,
                params?.optLong("duration_ms") ?: 500
            )
            "swipe_left" -> Action.SwipeLeft(params?.optInt("pixels") ?: 500)
            "swipe_right" -> Action.SwipeRight(params?.optInt("pixels") ?: 500)
            "swipe_up" -> Action.SwipeUp(params?.optInt("pixels") ?: 500)
            "swipe_down" -> Action.SwipeDown(params?.optInt("pixels") ?: 500)
            
            // Text input
            "type" -> Action.InputText(params?.optString("text") ?: "")
            "input_text" -> Action.InputText(params?.optString("text") ?: "")
            "type_text" -> Action.InputText(params?.optString("text") ?: "")
            
            // Key press
            "press_key" -> Action.PressKey(params?.optString("key") ?: "enter")
            
            // System actions
            "open_app" -> Action.OpenApp(params?.optString("package_name") ?: params?.optString("app_name") ?: "")
            "back" -> Action.Back
            "home" -> Action.Home
            "switch_app" -> Action.SwitchApp
            "press_enter" -> Action.PressEnter
            
            // Meta actions
            "speak" -> Action.Speak(params?.optString("text") ?: params?.optString("message") ?: "")
            "wait" -> Action.Wait
            "done" -> {
                val success = params?.optBoolean("success", true) ?: true
                Action.Done(params?.optString("message") ?: "Task completed", success)
            }
            else -> null
        }
    }
}