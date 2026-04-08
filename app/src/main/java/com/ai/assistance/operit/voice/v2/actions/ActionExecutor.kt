package com.ai.assistance.operit.voice.v2.actions

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.services.automation.OperitAutomationService
import com.ai.assistance.operit.voice.api.Finger
import com.ai.assistance.operit.voice.utilities.SpeechCoordinator
import com.ai.assistance.operit.voice.utilities.UserInputManager
import com.ai.assistance.operit.overlay.OverlayManager
import com.ai.assistance.operit.voice.v2.ActionResult
import com.ai.assistance.operit.voice.v2.fs.FileSystem
import com.ai.assistance.operit.voice.v2.perception.ScreenAnalysis
import com.ai.assistance.operit.voice.intents.IntentRegistry
import com.ai.assistance.operit.overlay.OverlayDispatcher
import com.ai.assistance.operit.overlay.OverlayPriority
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlin.system.measureTimeMillis
import kotlin.text.removePrefix

/**
 * Executes a pre-validated, type-safe Action command.
 * The 'when' block is exhaustive, ensuring every action is handled.
 */
class ActionExecutor(private val finger: Finger) {

    // Add this function inside ActionExecutor.kt, outside the class, or as a private fun.
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

        // First, try for an exact match (case-insensitive)
        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.equals(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        // If no exact match, try for a partial match (contains)
        for (appInfo in packages) {
            val label = pm.getApplicationLabel(appInfo).toString()
            if (label.contains(appName, ignoreCase = true)) {
                return appInfo.packageName
            }
        }

        return null // Not found
    }

    private fun getVisibleText(node: AccessibilityNodeInfo): String {
        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        // Prefer text, fall back to content description
        return (if (text.isNotBlank()) text else contentDesc).replace("\n", " ")
    }
    private fun getCenterFromNode(node: AccessibilityNodeInfo): Pair<Int, Int>? {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty) {
            return null // Node is not on screen or has no bounds
        }
        return Pair(bounds.centerX(), bounds.centerY())
    }
    /**
     * Executes a single action and returns the result.
     * @return An ActionResult detailing the outcome of the action.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun execute(
        action: Action,
        screenAnalysis: ScreenAnalysis,
        context: Context,
        fileSystem: FileSystem
    ): ActionResult {
        // This 'when' block now returns an ActionResult for every case.
        return when (action) {
            is Action.TapElement -> {
                val elementNode = screenAnalysis.elementMap[action.elementId]
                if (elementNode != null) {
                    val text = getVisibleText(elementNode)
                    val service = OperitAutomationService.instance

                    var signatureBefore = ""
                    var signatureAfter = ""
                    var screenChanged = false

                    // --- START: Time Measurement ---
                    val diffTime = measureTimeMillis {
                        // 1. GET SIGNATURE (The entire XML tree)
                        signatureBefore = service?.getWindowHierarchySignature() ?: ""

                        // 2. ATTEMPT 1: Polite Accessibility Action
                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)

                        // 3. WAIT & VERIFY
                        // We wait for the app to process the click and update the UI
                        delay(100)

                        signatureAfter = service?.getWindowHierarchySignature() ?: ""

                        // If the XML strings are different, the screen changed.
                        screenChanged = signatureBefore != signatureAfter
                    }

                    // --- LOG THE RESULT ---
                    Log.d("ActionExecutor", "Signature diff + 100ms delay took ${diffTime}ms. Screen changed: $screenChanged")

                    if (screenChanged) {
                        ActionResult(longTermMemory = "Clicked element '$text'. Screen updated successfully.")
                    } else {
                        // 4. ESCALATE: BRUTE FORCE TAP
                        // The XML is identical, so the app ignored the click.
                        val center = getCenterFromNode(elementNode)
                        if (center != null) {
                            finger.tap(center.first, center.second)
                            delay(500) // Wait for the physical tap to register
                            ActionResult(longTermMemory = "Accessibility click failed (screen didn't change). Escalated to physical tap at ${center.first},${center.second} on '$text'.")
                        } else {
                            ActionResult(error = "Click sent to '$text' but screen did not change, and cannot find coordinates for physical retry.")
                        }
                    }
                } else {
                    ActionResult(error = "Element with ID ${action.elementId} not found.")
                }
            }
//            is Action.TapElement -> {
//                val elementNode = screenAnalysis.elementMap[action.elementId]
//                if (elementNode != null) {
//                    val text = getVisibleText(elementNode)
//                    val service = OperitAutomationService.instance
//
//                    // 1. GET SIGNATURE (The entire XML tree)
//                    val signatureBefore = service?.getWindowHierarchySignature() ?: ""
//
//                    // 2. ATTEMPT 1: Polite Accessibility Action
//                    elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
//
//                    // 3. WAIT & VERIFY
//                    // We wait for the app to process the click and update the UI
//                    delay(600)
//
//                    val signatureAfter = service?.getWindowHierarchySignature() ?: ""
//
//                    // If the XML strings are different, the screen changed.
//                    val screenChanged = signatureBefore != signatureAfter
//
//                    if (screenChanged) {
//                        ActionResult(longTermMemory = "Clicked element '$text'. Screen updated successfully.")
//                    } else {
//                        // 4. ESCALATE: BRUTE FORCE TAP
//                        // The XML is identical, so the app ignored the click.
//                        val center = getCenterFromNode(elementNode)
//                        if (center != null) {
//                            finger.tap(center.first, center.second)
//                            delay(500) // Wait for the physical tap to register
//                            ActionResult(longTermMemory = "Accessibility click failed (screen didn't change). Escalated to physical tap at ${center.first},${center.second} on '$text'.")
//                        } else {
//                            ActionResult(error = "Click sent to '$text' but screen did not change, and cannot find coordinates for physical retry.")
//                        }
//                    }
//                } else {
//                    ActionResult(error = "Element with ID ${action.elementId} not found.")
//                }
//            }
//            is Action.TapElement -> {
//                // MODIFIED: 'elementNode' is now AccessibilityNodeInfo
//                val elementNode = screenAnalysis.elementMap[action.elementId]
//                if (elementNode != null) {
//                    // MODIFIED: Use new helpers
//                    val text = getVisibleText(elementNode)
//                    val resourceId = elementNode.viewIdResourceName ?: ""
//                    val extraInfo = getExtraInfo(elementNode)
//                    val className = (elementNode.className ?: "").removePrefix("android.")
//
//                    val center = getCenterFromNode(elementNode)
//                    if (center != null) {
//                        elementNode.performAction(AccessibilityNodeInfo.ACTION_CLICK)
////                        finger.tap(center.first, center.second)
//                        val si = OperitAutomationService.instance
//                        si?.showDebugTap(center.first.toFloat(), center.second.toFloat())
//                        ActionResult(longTermMemory = "Tapped element text:$text <$resourceId> <$extraInfo> <$className>")
//                    } else {
//                        ActionResult(error = "Element with ID ${action.elementId} has no visible bounds.")
//                    }
//                } else {
//                    ActionResult(error = "Element with ID ${action.elementId} not found in the current screen state.")
//                }
//            }
            is Action.Speak -> {
                // The message is taken directly from the type-safe action class.
                val message = action.message
                runBlocking {
                    SpeechCoordinator.getInstance(context).speakToUser(message)
                }
                ActionResult(longTermMemory = "Spoke the message: \"${message.take(50)}...\"")
            }
            is Action.Ask -> {
                val question = action.question
                val userResponse = withContext(Dispatchers.IO) { // User input is blocking
                    val userInputManager = UserInputManager(context)
                    userInputManager.askQuestion(question) // This internally speaks and listens
                }

                val memory = "Asked user: '$question'. User responded: '$userResponse'."
                ActionResult(
                    longTermMemory = memory,
                    extractedContent = userResponse, // The user's answer is the result
                    includeExtractedContentOnlyOnce = true
                )
            }
            is Action.LongPressElement -> {
                // MODIFIED: 'elementNode' is now AccessibilityNodeInfo
                val elementNode = screenAnalysis.elementMap[action.elementId]
                if (elementNode != null) {
                    // MODIFIED: Use new helpers
                    val text = getVisibleText(elementNode)
                    val resourceId = elementNode.viewIdResourceName ?: ""
                    val extraInfo = getExtraInfo(elementNode)
                    val className = (elementNode.className ?: "").removePrefix("android.")

                    val center = getCenterFromNode(elementNode)
                    if (center != null) {
//                        finger.longPress(center.first, center.second)
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
                // Use delay in a coroutine instead of Thread.sleep
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
                // This is a multi-step conceptual action. The executor should handle the concrete steps.
                finger.openApp("com.android.chrome") // More reliable to use package name
                // The next steps (typing, pressing enter) should be decided by the agent in the next turn.
                ActionResult(longTermMemory = "Opened Chrome to search Google.")
            }
            is Action.Done -> {
                // This action doesn't *do* anything. It's a signal to the main loop.
                // We just construct the final ActionResult.
                ActionResult(
                    isDone = true,
                    success = action.success,
                    longTermMemory = "Task finished: ${action.text}",
                    attachments = action.filesToDisplay
                )
            }
//            is Action.ExtractStructuredData -> {
//                // This is a placeholder for a complex action.
//                // A full implementation would require another LLM call with the screen content.
//                // For now, we return an error indicating it's not yet implemented.
//                ActionResult(error = "Action 'ExtractStructuredData' is not yet implemented.")
//            }
            is Action.InputText -> {
                finger.type(action.text)
                ActionResult(longTermMemory = "Input text ${action.text}.")
            }
//            is Action.ScrollToText -> {
//                // As requested, skipping implementation.
//                ActionResult(error = "Action 'ScrollToText' is not implemented.")
//            }
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
                    Log.d("ActionExecutor", "Wrote content to '${action.fileName} ${action.content}'.")
                        OverlayDispatcher.show(
                            action.content,
                            OverlayPriority.CAPTION
                        )
                    ActionResult(longTermMemory = "Wrote content to '${action.fileName}'.")
                } else {
                    ActionResult(error = "Failed to write to file '${action.fileName}'.")
                }
            }

//            is Action.ScrollToText -> TODO()
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
                val appIntent = IntentRegistry.findByName(context, name)
                if (appIntent == null) {
                    return ActionResult(error = "Intent '$name' not found. Check intents catalog for valid names.")
                }
                val intent = appIntent.buildIntent(context, params)
                return if (intent == null) {
                    ActionResult(error = "Intent '$name' missing or invalid parameters: ${params}")
                } else {
                    try {
                        val launchSuccess = finger.launchIntent(intent)
                        if (launchSuccess) {
                            ActionResult(longTermMemory = "Launched intent '$name' with params ${params}")
                        } else {
                            ActionResult(error = "Failed to launch intent '$name' with params ${params}")
                        }
                    } catch (t: Throwable) {
                        ActionResult(error = "Failed to launch intent '$name': ${t.message}")
                    }
                }
            }
            is Action.TapAt -> {
                finger.tap(action.x, action.y)
                ActionResult(longTermMemory = "Tapped at (${action.x}, ${action.y})")
            }
            is Action.LongPressAt -> {
                finger.longPress(action.x, action.y)
                ActionResult(longTermMemory = "Long pressed at (${action.x}, ${action.y}) for ${action.durationMs}ms")
            }
            is Action.DoubleTapAt -> {
                finger.tap(action.x, action.y)
                delay(200)
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
                    "recents", "switch_app" -> finger.switchApp()
                }
                ActionResult(longTermMemory = "Pressed key: ${action.key}")
            }

            // ========== Operit System Tools ==========

            is Action.Toast -> {
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, action.message, android.widget.Toast.LENGTH_SHORT).show()
                }
                ActionResult(longTermMemory = "Showed toast: \"${action.message.take(50)}...\"")
            }

            is Action.SendNotification -> {
                val notificationManager = context.getSystemService(android.app.NotificationManager::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val channel = android.app.NotificationChannel(
                        "operit_agent",
                        "Operit Agent",
                        android.app.NotificationManager.IMPORTANCE_DEFAULT
                    )
                    notificationManager.createNotificationChannel(channel)
                }
                val notification = androidx.core.app.NotificationCompat.Builder(context, "operit_agent")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle(action.title)
                    .setContentText(action.message)
                    .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)
                    .build()
                notificationManager.notify(System.currentTimeMillis().toInt() % 100000, notification)
                ActionResult(longTermMemory = "Sent notification: \"${action.title}\"")
            }

            is Action.ModifySystemSetting -> {
                try {
                    val settingUri = when (action.settingType.lowercase()) {
                        "system" -> android.provider.Settings.System.CONTENT_URI
                        "secure" -> android.provider.Settings.Secure.CONTENT_URI
                        "global" -> android.provider.Settings.Global.CONTENT_URI
                        else -> return ActionResult(error = "Invalid setting type: ${action.settingType}. Use 'system', 'secure', or 'global'.")
                    }
                    context.contentResolver.insert(settingUri, android.content.ContentValues().apply {
                        put(action.key, action.value)
                    })
                    ActionResult(longTermMemory = "Set system setting '${action.key}' to '${action.value}'.")
                } catch (e: Exception) {
                    ActionResult(error = "Failed to modify system setting '${action.key}': ${e.message}")
                }
            }

            is Action.GetSystemSetting -> {
                try {
                    val value = when (action.settingType.lowercase()) {
                        "system" -> android.provider.Settings.System.getString(context.contentResolver, action.key)
                        "secure" -> android.provider.Settings.Secure.getString(context.contentResolver, action.key)
                        "global" -> android.provider.Settings.Global.getString(context.contentResolver, action.key)
                        else -> return ActionResult(error = "Invalid setting type: ${action.settingType}.")
                    }
                    ActionResult(
                        longTermMemory = "Read system setting '${action.key}'.",
                        extractedContent = value ?: "(null)",
                        includeExtractedContentOnlyOnce = true
                    )
                } catch (e: Exception) {
                    ActionResult(error = "Failed to read system setting '${action.key}': ${e.message}")
                }
            }

            is Action.StopApp -> {
                try {
                    val pm = context.packageManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledApplications(0)
                    }
                    val process = java.lang.ProcessBuilder("am", "force-stop", action.packageName).start()
                    process.waitFor()
                    ActionResult(longTermMemory = "Force-stopped app '${action.packageName}'.")
                } catch (e: Exception) {
                    ActionResult(error = "Failed to stop app '${action.packageName}': ${e.message}")
                }
            }

            is Action.ListInstalledApps -> {
                try {
                    val pm = context.packageManager
                    val packages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0L))
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledApplications(0)
                    }
                    val apps = packages.take(action.limit).map { pm.getApplicationLabel(it).toString() }
                    ActionResult(
                        longTermMemory = "Listed ${apps.size} installed apps.",
                        extractedContent = apps.joinToString("\n"),
                        includeExtractedContentOnlyOnce = true
                    )
                } catch (e: Exception) {
                    ActionResult(error = "Failed to list installed apps: ${e.message}")
                }
            }

            is Action.HttpRequest -> {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(action.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(action.timeoutSeconds.toLong(), java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val requestBuilder = okhttp3.Request.Builder().url(action.url)
                    action.headers?.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

                    val body = action.body?.let {
                        it.toRequestBody("application/json; charset=utf-8".toMediaType())
                    }

                    val request = when (action.method.uppercase()) {
                        "POST" -> requestBuilder.post(body ?: "".toRequestBody())
                        "PUT" -> requestBuilder.put(body ?: "".toRequestBody())
                        "DELETE" -> requestBuilder.delete()
                        "PATCH" -> requestBuilder.patch(body ?: "".toRequestBody())
                        else -> requestBuilder.get()
                    }.build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""
                    ActionResult(
                        longTermMemory = "HTTP ${action.method} ${action.url} returned ${response.code}.",
                        extractedContent = "Status: ${response.code}\n$responseBody",
                        includeExtractedContentOnlyOnce = true
                    )
                } catch (e: Exception) {
                    ActionResult(error = "HTTP request failed: ${e.message}")
                }
            }

            is Action.VisitWeb -> {
                try {
                    val client = okhttp3.OkHttpClient.Builder()
                        .connectTimeout(30L, java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30L, java.util.concurrent.TimeUnit.SECONDS)
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url(action.url)
                        .addHeader("User-Agent", "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36")
                        .get()
                        .build()

                    val response = client.newCall(request).execute()
                    val responseBody = response.body?.string() ?: ""

                    // Extract text content (basic HTML stripping)
                    val textContent = responseBody
                        .replace(Regex("<[^>]+>"), " ")
                        .replace(Regex("\\s+"), " ")
                        .trim()
                        .take(action.maxContentLength)

                    ActionResult(
                        longTermMemory = "Visited ${action.url} and extracted ${textContent.length} chars of text content.",
                        extractedContent = textContent,
                        includeExtractedContentOnlyOnce = true
                    )
                } catch (e: Exception) {
                    ActionResult(error = "Failed to visit web page: ${e.message}")
                }
            }

            is Action.ExecuteShell -> {
                try {
                    val process = java.lang.ProcessBuilder("sh", "-c", action.command)
                        .redirectErrorStream(true)
                        .start()
                    val output = process.inputStream.bufferedReader().readText()
                    process.waitFor()
                    val exitCode = process.exitValue()
                    ActionResult(
                        longTermMemory = "Executed shell command (exit code: $exitCode).",
                        extractedContent = output.take(5000),
                        includeExtractedContentOnlyOnce = true
                    )
                } catch (e: Exception) {
                    ActionResult(error = "Shell command failed: ${e.message}")
                }
            }

            is Action.Calculate -> {
                try {
                    // Use Android's built-in JavaScript engine for math evaluation
                    val webView = android.webkit.WebView(context)
                    webView.settings.javaScriptEnabled = true

                    var result = ""
                    val latch = java.util.concurrent.CountDownLatch(1)

                    withContext(Dispatchers.Main) {
                        webView.evaluateJavascript("(function() { return String(${action.expression}); })();") { jsResult ->
                            result = jsResult?.replace("\"", "") ?: "null"
                            latch.countDown()
                        }
                    }

                    latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                    webView.destroy()

                    if (result.isBlank() || result == "null") {
                        ActionResult(error = "Calculator returned empty result for expression: ${action.expression}")
                    } else {
                        ActionResult(
                            longTermMemory = "Calculated: ${action.expression} = $result",
                            extractedContent = result,
                            includeExtractedContentOnlyOnce = true
                        )
                    }
                } catch (e: Exception) {
                    ActionResult(error = "Calculator failed: ${e.message}")
                }
            }

            is Action.GetDeviceInfo -> {
                try {
                    val info = buildString {
                        appendLine("Device: ${android.os.Build.MODEL}")
                        appendLine("Manufacturer: ${android.os.Build.MANUFACTURER}")
                        appendLine("Android Version: ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
                        appendLine("Display: ${context.resources.displayMetrics.widthPixels}x${context.resources.displayMetrics.heightPixels}")

                        // Memory
                        val activityManager = context.getSystemService(android.app.ActivityManager::class.java)
                        val memInfo = android.app.ActivityManager.MemoryInfo()
                        activityManager.getMemoryInfo(memInfo)
                        appendLine("Total RAM: ${memInfo.totalMem / (1024 * 1024)} MB")
                        appendLine("Available RAM: ${memInfo.availMem / (1024 * 1024)} MB")

                        // Storage
                        val stat = android.os.StatFs(context.filesDir.absolutePath)
                        appendLine("Storage Free: ${stat.availableBytes / (1024 * 1024 * 1024)} GB")
                        appendLine("Storage Total: ${stat.totalBytes / (1024 * 1024 * 1024)} GB")
                    }
                    ActionResult(
                        longTermMemory = "Retrieved device information.",
                        extractedContent = info,
                        includeExtractedContentOnlyOnce = true
                    )
                } catch (e: Exception) {
                    ActionResult(error = "Failed to get device info: ${e.message}")
                }
            }

            is Action.QueryMemory -> {
                // Memory query - return empty for now (memory system is simplified in overlay agent)
                ActionResult(
                    longTermMemory = "Queried memory for: '${action.query}'.",
                    extractedContent = "No memories found for query: ${action.query}",
                    includeExtractedContentOnlyOnce = true
                )
            }

            is Action.CreateMemory -> {
                // Memory creation - log for now (memory system is simplified)
                Log.d("ActionExecutor", "Memory created: ${action.title} - ${action.content}")
                ActionResult(longTermMemory = "Created memory: '${action.title}'.")
            }

            is Action.UpdateMemory -> {
                Log.d("ActionExecutor", "Memory updated: ${action.title}")
                ActionResult(longTermMemory = "Updated memory: '${action.title}'.")
            }

            is Action.DeleteMemory -> {
                Log.d("ActionExecutor", "Memory deleted: ${action.title}")
                ActionResult(longTermMemory = "Deleted memory: '${action.title}'.")
            }

            is Action.LaunchUrlInBrowser -> {
                try {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW)
                    intent.data = android.net.Uri.parse(action.url)
                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                    ActionResult(longTermMemory = "Opened URL in default browser: ${action.url}")
                } catch (e: Exception) {
                    ActionResult(error = "Failed to launch URL in browser: ${e.message}")
                }
            }

            is Action.CaptureScreenshot -> {
                try {
                    val screenshotDir = android.os.Environment.getExternalStoragePublicDirectory(
                        android.os.Environment.DIRECTORY_PICTURES
                    )
                    val filename = action.saveAs ?: "operit_screenshot_${System.currentTimeMillis()}.png"
                    val file = java.io.File(screenshotDir, filename)

                    // Use MediaProjection or root screenshot depending on permissions
                    // For now, use the simple screenshot via screencap command
                    val process = java.lang.ProcessBuilder("screencap", "-p", file.absolutePath).start()
                    process.waitFor()

                    if (file.exists() && file.length() > 0) {
                        ActionResult(
                            longTermMemory = "Screenshot captured and saved to ${file.absolutePath}.",
                            extractedContent = file.absolutePath,
                            includeExtractedContentOnlyOnce = true
                        )
                    } else {
                        ActionResult(error = "Screenshot capture failed: file not created or empty.")
                    }
                } catch (e: Exception) {
                    ActionResult(error = "Failed to capture screenshot: ${e.message}")
                }
            }
        }
    }
}
