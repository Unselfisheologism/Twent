package com.ai.assistance.operit.services.automation

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.R
import com.ai.assistance.operit.api.automation.Eyes
import com.ai.assistance.operit.api.automation.Finger
import com.ai.assistance.operit.core.agent.Agent
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
    
    private var agent: Agent? = null
    private var isRunning = false
    
    private val agentScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val systemPrompt = """
You are a tool-using AI agent designed operating in an iterative loop to automate Phone tasks. Your ultimate goal is accomplishing the task provided in <user_request>.

<intro>
You excel at following tasks:
1. Navigating complex apps and extracting precise information
2. Automating form submissions and interactive app actions
3. Gathering and saving information 
4. Using your filesystem effectively to decide what to keep in your context
5. Operate effectively in an agent loop
6. Efficiently performing diverse phone tasks
</intro>

<user_info>
User: {user_name}
</user_info>

<input>
At every step, you will be given a state with: 
1. Agent History: A chronological event stream including your previous actions and their results
2. User Request: This is your ultimate objective and always remains visible
3. Android State: Contains current App-Activity, interactive elements indexed for actions, visible screen content
4. Keyboard State: Whether keyboard is open or closed
5. Scroll Info: How much more content is available above/below the screen
</input>

<android_state>
Current App-Activity: {current_activity}
Interactive Elements: {interactive_elements}
Keyboard Open: {keyboard_open}
Scroll Info: {scroll_info}
</android_state>

<android_rules>
Strictly follow these rules while using the Android Phone and navigating the apps:
- Only interact with elements that have a numeric [index] assigned
- If you need to use any app, open them by "open_app" action with package name
- Use system-level actions like back, switch_app, speak, and home to navigate the OS
- If the screen changes after an action, analyze if you need to interact with new elements
- Use scrolling actions if there are more pixels below or above the screen
- If expected elements are missing, try refreshing, swiping, or navigating back
- When searching for content, use tap_element_input_text_and_enter action
</android_rules>

<file_system>
- You have access to a persistent file system
- Use todo.md to track progress on multi-step tasks
- Use results.md to accumulate important findings
</file_system>

<task_completion_rules>
You must call the `done` action when:
- You have fully completed the USER REQUEST
- You reach the final allowed step (max_steps)
- It is ABSOLUTELY IMPOSSIBLE to continue

Set success=true only if the full task is completed.
</task_completion_rules>

<available_actions>
- tap_element: Tap the element with the specified numeric ID
- long_press_element: Press and hold the element with the specified numeric ID
- type: Type text into a focused input field
- tap_element_input_text_and_enter: Tap an element, input text, and press enter
- swipe_down: Scroll down by specified amount of pixels
- swipe_up: Scroll up by specified amount of pixels
- open_app: Open an app by package name (e.g., "com.instagram.android")
- back: Go back to the previous screen
- home: Go to the device's home screen
- switch_app: Show the app switcher
- speak: Speak a message to the user
- wait: Wait for a few seconds
- write_file: Write content to a file
- read_file: Read content from a file
- append_file: Append content to a file
- done: Complete the current task
</available_actions>

<output>
You must ALWAYS respond with a valid JSON in this exact format:
{
  "thinking": "A structured reasoning block...",
  "evaluationPreviousGoal": "One-sentence analysis of your last action...",
  "memory": "1-3 sentences of specific memory...",
  "nextGoal": "State the next immediate goals...",
  "action": [
    {"action_name": {"parameter": "value"}}
  ]
}
The action list must NEVER be empty.
IMPORTANT: Your entire response must be a single JSON object, starting with { and ending with }.
</output>
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
        
        val userName = "User"
        
        val modifiedPrompt = systemPrompt.replace("{user_name}", userName)
        val messageManager = MessageManager(modifiedPrompt)
        
        // Create LLM API wrapper that uses Operit's existing AI service
        val operitLlmApi = OperitLlmApi(context)
        
        val settings = AgentSettings(maxSteps = maxSteps)
        
        agent = Agent(
            settings = settings,
            messageManager = messageManager,
            perception = perception,
            llmApi = operitLlmApi,
            actionExecutor = ActionExecutor,
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
        
        val userName = "User"
        
        val modifiedPrompt = systemPrompt.replace("{user_name}", userName)
        val messageManager = MessageManager(modifiedPrompt)
        
        val operitLlmApi = OperitLlmApi(context)
        
        val settings = AgentSettings(maxSteps = maxSteps)
        
        agent = Agent(
            settings = settings,
            messageManager = messageManager,
            perception = perception,
            llmApi = operitLlmApi,
            actionExecutor = ActionExecutor,
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
            "tap_element" -> Action.TapElement(params?.optInt("element_id") ?: 0)
            "long_press_element" -> Action.LongPressElement(params?.optInt("element_id") ?: 0)
            "type" -> Action.InputText(params?.optString("text") ?: "")
            "tap_element_input_text_and_enter" -> Action.TapElementInputTextPressEnter(
                params?.optInt("index") ?: 0,
                params?.optString("text") ?: ""
            )
            "swipe_down" -> Action.ScrollDown(params?.optInt("amount") ?: 500)
            "swipe_up" -> Action.ScrollUp(params?.optInt("amount") ?: 500)
            "open_app" -> Action.OpenApp(params?.optString("app_name") ?: "")
            "back" -> Action.Back
            "home" -> Action.Home
            "switch_app" -> Action.SwitchApp
            "speak" -> Action.Speak(params?.optString("message") ?: "")
            "done" -> Action.Done(params?.optString("text") ?: "Task completed")
            else -> null
        }
    }
}