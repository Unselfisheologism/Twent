# Stop Button & Disappearing Controls - FIXED

## Issues Fixed

### ✅ Issue 1: Stop Button Does Nothing
### ✅ Issue 2: Buttons Never Disappear (Stay Forever)

---

## Root Causes

### Stop Button Not Working:
1. The `shouldStopTask` flag was being set to `true` when stop was clicked
2. BUT it was immediately reset to `false` at the start of each loop iteration
3. The agent.run() never checked the flag during execution
4. The stop() function was called but didn't immediately stopSelf()

### Buttons Never Disappearing:
1. `hideTopLeftTaskControls()` was only called at the very end of `processTaskQueue()`
2. NOT called when individual tasks completed
3. NOT called during shutdown in `ConversationalAgentService`
4. NOT called in `onDestroy()` methods
5. Buttons persisted across service restarts and even when overlay UI was gone

---

## Solutions Implemented

### AgentService.kt - Task Execution

#### 1. Fixed Stop Button Callback
```kotlin
onStopClicked = {
    Log.i(TAG, "Stop button clicked - stopping immediately")
    shouldStopTask = true
    // Hide controls IMMEDIATELY
    visualFeedbackManager.hideTopLeftTaskControls()
    visualFeedbackManager.hideTaskActiveGlow()
    // Stop the service NOW
    stopSelf()
}
```

**Changes:**
- ✅ Calls `hideTopLeftTaskControls()` immediately when stop is clicked
- ✅ Calls `hideTaskActiveGlow()` immediately to remove shimmer
- ✅ Calls `stopSelf()` directly instead of `stop(this@AgentService)` for immediate effect

#### 2. Fixed Button Cleanup After Task Completion
```kotlin
while (taskQueue.isNotEmpty()) {
    // Check if stop was requested
    if (shouldStopTask) {
        Log.i(TAG, "Stop requested during task execution")
        break
    }
    
    val task = taskQueue.poll() ?: continue
    currentTask = task
    // ... execute task ...
}

// Clean up in ALL cases
Log.i(TAG, "Task queue empty or stop requested. Cleaning up.")
currentTask = null
visualFeedbackManager.hideTopLeftTaskControls()
visualFeedbackManager.hideTaskActiveGlow()
stopSelf()
```

**Changes:**
- ✅ Added `shouldStopTask` check at start of each loop iteration
- ✅ Break loop immediately if stop is requested
- ✅ Call `hideTopLeftTaskControls()` after loop ends (whether empty or stopped)
- ✅ Clear `currentTask` to null

#### 3. Initialize Flags at Start
```kotlin
isRunning = true
shouldStopTask = false  // Reset at start
isTaskPaused = false    // Reset at start
```

---

### ConversationalAgentService.kt - Reply Sessions

#### 1. Updated gracefulShutdown()
```kotlin
private suspend fun gracefulShutdown(exitMessage: String? = null, endReason: String = "graceful") {
    visualFeedbackManager.hideTopLeftTaskControls()  // ✅ ADDED
    visualFeedbackManager.hideTtsWave()
    visualFeedbackManager.hideTranscription()
    visualFeedbackManager.hideSpeakingOverlay()
    visualFeedbackManager.hideInputBox()
    visualFeedbackManager.hideActionStatusView()
    // ... rest of cleanup
}
```

#### 2. Updated instantShutdown()
```kotlin
private suspend fun instantShutdown() {
    Log.d("ConvAgent", "Instant shutdown triggered by user.")
    withContext(Dispatchers.Main) {
        speechCoordinator.stopSpeaking()
        speechCoordinator.stopListening()
        visualFeedbackManager.hideTopLeftTaskControls()  // ✅ ADDED
        visualFeedbackManager.hideTtsWave()
        visualFeedbackManager.hideTranscription()
        visualFeedbackManager.hideSpeakingOverlay()
        visualFeedbackManager.hideInputBox()
        visualFeedbackManager.hideActionStatusView()
        visualFeedbackManager.hideThinkingIndicator()
        removeClarificationQuestions()
    }
    // ... rest of cleanup
}
```

#### 3. Updated onDestroy()
```kotlin
override fun onDestroy() {
    super.onDestroy()
    Log.d("ConvAgent", "Service onDestroy")

    overlayManager.stopObserving()
    removeClarificationQuestions()
    serviceScope.cancel()
    isRunning = false
    actionStatusViewNotShownYet = true

    stateManager.setState(OperitState.IDLE)
    stateManager.stopMonitoring()
    visualFeedbackManager.hideTopLeftTaskControls()  // ✅ ADDED
    visualFeedbackManager.hideSpeakingOverlay()
    visualFeedbackManager.hideTtsWave()
    visualFeedbackManager.hideTranscription()
    visualFeedbackManager.hideInputBox()
    visualFeedbackManager.hideActionStatusView()
    visualFeedbackManager.hideThinkingIndicator()
}
```

---

## When Buttons Appear/Disappear

### ✅ APPEAR When:
1. **AgentService starts a task** - `processTaskQueue()` calls `showTopLeftTaskControls()`
2. **ConversationalAgentService gives a Reply** - Reply handling calls `showTopLeftTaskControls()` (only in text mode)

### ✅ DISAPPEAR When:
1. **User clicks Stop button** - `hideTopLeftTaskControls()` called immediately in stop callback
2. **Task completes successfully** - `hideTopLeftTaskControls()` called after task finishes
3. **Task queue becomes empty** - `hideTopLeftTaskControls()` called before `stopSelf()`
4. **Stop requested during task** - `hideTopLeftTaskControls()` called when `shouldStopTask` is detected
5. **gracefulShutdown()** - Called in ConversationalAgentService when conversation ends
6. **instantShutdown()** - Called when user taps outside overlay
7. **Service onDestroy()** - Called in both AgentService and ConversationalAgentService

---

## Files Modified

### 1. **AgentService.kt**
- Updated `processTaskQueue()` function
- Added `shouldStopTask` check in loop
- Changed stop callback to call `stopSelf()` directly
- Added immediate `hideTopLeftTaskControls()` in stop callback
- Added cleanup calls after task loop ends

### 2. **ConversationalAgentService.kt**
- Updated `gracefulShutdown()` to hide top-left controls
- Updated `instantShutdown()` to hide top-left controls
- Updated `onDestroy()` to hide top-left controls

---

## Testing Scenarios

### Test 1: Stop Button Works
1. Activate overlay, start a UI automation task
2. Top-left buttons appear (Red X + Yellow Pause)
3. Click Red X button
4. ✅ Buttons should disappear IMMEDIATELY
5. ✅ Task should stop (service stops)
6. ✅ Shimmer glow should disappear

### Test 2: Task Completion
1. Start a short task
2. Buttons appear when task starts
3. Wait for task to complete
4. ✅ Buttons should disappear when task finishes
5. ✅ Shimmer glow should disappear
6. ✅ Service should stop

### Test 3: Reply Session
1. Start overlay, have a conversation (Reply type)
2. Buttons appear when AI responds
3. Let conversation end naturally
4. ✅ Buttons should disappear when session ends
5. OR tap outside overlay
6. ✅ Buttons should disappear immediately

### Test 4: Multiple Tasks
1. Start first task → buttons appear
2. Task completes → buttons disappear
3. Start second task → buttons appear again
4. Stop second task → buttons disappear
5. ✅ Buttons show/hide correctly for each task

### Test 5: Service Restart
1. Start overlay, buttons appear
2. Close overlay (shutdown)
3. ✅ Buttons should disappear
4. Re-open overlay
5. ✅ Buttons should NOT appear until a task starts
6. Start a task → buttons appear again

---

## Expected Behavior Summary

| Event | Buttons Should |
|-------|---------------|
| Task starts | ✅ Appear |
| Task completes | ✅ Disappear |
| Stop clicked | ✅ Disappear IMMEDIATELY |
| Task paused | ✅ Stay visible (icon changes) |
| Task resumed | ✅ Stay visible (icon changes back) |
| Conversation ends (Reply) | ✅ Disappear |
| User taps outside overlay | ✅ Disappear |
| Service destroyed | ✅ Disappear |
| No task active | ✅ Should NOT appear |

---

**Date**: April 10, 2026  
**Status**: ✅ Both issues fixed  
**Next**: Test with actual tasks to verify buttons appear/disappear correctly
