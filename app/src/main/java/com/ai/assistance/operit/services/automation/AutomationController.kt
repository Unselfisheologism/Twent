package com.ai.assistance.operit.services.automation

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.api.automation.Eyes
import com.ai.assistance.operit.api.automation.Finger
import com.ai.assistance.operit.core.agent.v2.Agent
import com.ai.assistance.operit.core.agent.v2.AgentSettings
import com.ai.assistance.operit.core.agent.v2.actions.Action
import com.ai.assistance.operit.core.agent.v2.actions.ActionExecutor
import com.ai.assistance.operit.core.agent.v2.message.MemoryManager
import com.ai.assistance.operit.core.agent.v2.llm.OperitLlmApi
import com.ai.assistance.operit.core.agent.v2.perception.Perception
import com.ai.assistance.operit.core.agent.v2.perception.ScreenAnalysis
import com.ai.assistance.operit.core.agent.v2.fs.FileSystem
import kotlinx.coroutines.*

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
    private val semanticParser by lazy { com.ai.assistance.operit.core.agent.v2.perception.SemanticParser() }
    private val perception by lazy { Perception(eyes, semanticParser) }
    private val fileSystem by lazy { FileSystem(context) }
    
    private var agent: Agent? = null
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
 6. NEVER use "done" action in the first 3 steps - MUST explore the screen and attempt meaningful interactions first
 7. Do NOT declare task completed until you have actually confirmed the intended action worked (checked screen changed)
 8. If your action has no visible effect (screen unchanged from evaluationPreviousGoal), try a DIFFERENT action - do NOT immediately use "done" - continue to try alternatives
 9. If the UI hierarchy is unavailable or null, use "wait" action and try again - the screen may be off or locked

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
 - {"done": {"success": true, "message": "Task completed"}} - ONLY use after confirming screen changed from your actions
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

If no elements match what you need:- Use scroll actions to reveal more elements
- If you don't see the target, assume it's in a different section - scroll to find it
- Keep exploring until you find the correct element

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
        val messageManager = MemoryManager(
            context = context,
            task = task,
            fileSystem = fileSystem,
            settings = AgentSettings(maxSteps = maxSteps)
        )
        
        val operitLlmApi = OperitLlmApi(
            modelName = "default",
            context = context
        )
        
        val settings = AgentSettings(maxSteps = maxSteps)
        
        agent = Agent(
            settings = settings,
            memoryManager = messageManager,
            perception = perception,
            llmApi = operitLlmApi,
            actionExecutor = ActionExecutor(finger, context),
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

    fun stopAutomation() {
        Log.d(TAG, "Stopping automation")
        isRunning = false
        agentScope.cancel()
        agent = null
    }

    fun isRunning(): Boolean = isRunning

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
        val messageManager = MemoryManager(
            context = context,
            task = task,
            fileSystem = fileSystem,
            settings = AgentSettings(maxSteps = maxSteps)
        )
        
        val operitLlmApi = OperitLlmApi(
            modelName = "default",
            context = context
        )
        
        val settings = AgentSettings(maxSteps = maxSteps)
        
        agent = Agent(
            settings = settings,
            memoryManager = messageManager,
            perception = perception,
            llmApi = operitLlmApi,
            actionExecutor = ActionExecutor(finger, context),
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

    suspend fun getScreenAnalysis(): ScreenAnalysis? {
        return try {
            perception.analyze()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to analyze screen", e)
            null
        }
    }

    suspend fun performAction(action: Action): Boolean {
        return try {
            val screenState = perception.analyze()
            val executor = ActionExecutor(finger, context)
            val result = executor.execute(action, screenState, context, fileSystem)
            result.error == null
        } catch (e: Exception) {
            Log.e(TAG, "Failed to perform action", e)
            false
        }
    }

    fun tap(x: Int, y: Int) = finger.tap(x, y)
    fun longPress(x: Int, y: Int) = finger.longPress(x, y)
    fun swipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int = 1000) = finger.swipe(x1, y1, x2, y2, duration)
    fun typeText(text: String) = finger.type(text)
    fun goBack() = finger.back()
    fun goHome() = finger.home()
    fun switchApp() = finger.switchApp()
    fun openApp(packageName: String) = finger.openApp(packageName)
}
