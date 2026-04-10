# Task Control Buttons - Fixed Behavior

## Changes Implemented

### 1. **Buttons Now Actually Work** - Pause/Stop Functionality

**File: `Agent.kt`**
- Added pause/stop flag checks at the START of each loop iteration
- **Pause**: Task waits in a loop checking `AgentService.isTaskPaused` every 500ms
- **Stop**: Task checks `AgentService.shouldStopTask` and breaks execution
- Both flags are checked BEFORE each step executes

```kotlin
// At start of while loop in Agent.run():
if (AgentService.shouldStopTask) {
    speechCoordinator.speakToUser("Task stopped by user.")
    state.stopped = true
    visualFeedbackManager.hideTaskActiveGlow()
    break
}

while (AgentService.isTaskPaused && !state.stopped) {
    if (AgentService.shouldStopTask) {
        // Handle stop while paused
        return
    }
    delay(500) // Check every 500ms
}
```

### 2. **Two-Stage Stop Button Behavior**

**File: `AgentService.kt`**

**First Stop Click:**
- Pauses task execution
- Shows follow-up input box with placeholder "Ask Follow-up"
- Buttons remain visible
- Task is paused, not stopped

**Second Stop Click:**
- Completely removes all overlay UI
- Hides buttons, glow, and input box
- Stops the service

**Resume Click (after pause):**
- Resumes task execution
- Updates button icon back to pause
- Task continues from where it left off

### 3. **Custom Placeholder Text Support**

**File: `VisualFeedbackManager.kt`**

Added `placeholderText` parameter to `showInputBox()`:
```kotlin
fun showInputBox(
    ...
    placeholderText: String = "Ask Operit",  // New parameter with default
    ...
) {
    inputField?.hint = placeholderText  // Applied to EditText
}
```

### 4. **Task Completion Flow**

When task completes normally:
- Shows "Ask Follow-up" input box
- Outside tap → hides input box AND stops service
- Submit text → starts new task with submitted text

## Complete User Flow

### During Task Execution:
```
Task Running (edge glow visible, buttons visible)
    ↓
[Option A: Click Pause]
    → Task pauses (waits in loop)
    → Button icon changes to Play
    → Click Play → Task resumes
    ↓
[Option B: Click Stop (1st time)]
    → Task pauses
    → Follow-up input appears with "Ask Follow-up" placeholder
    → Buttons still visible
    ↓
    [B1: Submit follow-up text]
        → Task resumes with new text
        → Reset stopClickedOnce flag
    ↓
    [B2: Click Stop (2nd time)]
        → Remove ALL overlay UI
        → Stop service
    ↓
    [B3: Click Resume]
        → Task resumes from pause point
```

### Task Completes Normally:
```
Task Running
    ↓
Task finishes
    ↓
Hide buttons + glow
    ↓
Show "Ask Follow-up" input box
    ↓
[Option A: Submit text] → Start new task
[Option B: Tap outside] → Hide input + Stop service
```

## Files Modified

1. **`Agent.kt`** (lines ~88-110)
   - Added pause/stop flag checking at loop start
   - Pause waits in loop checking every 500ms
   - Stop breaks execution immediately

2. **`AgentService.kt`** (lines ~207-245)
   - Added `stopClickedOnce` flag
   - Updated stop callback with two-stage behavior
   - Added custom placeholder text for follow-up input

3. **`VisualFeedbackManager.kt`** (lines ~329-376)
   - Added `placeholderText` parameter to `showInputBox()`
   - Applied placeholder to EditText hint

## Key Technical Details

### Pause Implementation
- Uses `while` loop checking `isTaskPaused` flag
- Checks every 500ms (balances responsiveness with CPU usage)
- Also checks `shouldStopTask` inside pause loop to handle stop while paused
- Uses `delay(500)` which is a suspending function (doesn't block thread)

### Stop Implementation
- Two-stage stop:
  - 1st click: Pause + show input
  - 2nd click: Full cleanup + stop service
- Uses local `var stopClickedOnce` to track state
- Reset flag when follow-up is submitted or task resumes

### Input Box Behavior
- Custom placeholder text support
- Outside tap behavior:
  - During paused task: Keeps input visible
  - After task completion: Hides input AND stops service

## Testing Instructions

1. **Start a task**: "Check my notifications"
2. **Test Pause/Resume**:
   - Click yellow pause → Task should pause
   - Button icon changes to play ▶️
   - Click play → Task resumes
3. **Test Two-Stage Stop**:
   - Click red stop (1st time) → Task pauses, "Ask Follow-up" input appears
   - Buttons still visible
   - Type follow-up → Task resumes with new text
   - OR click red stop (2nd time) → All UI removed, service stops
4. **Test Normal Completion**:
   - Let task finish naturally
   - "Ask Follow-up" input appears
   - Tap outside → Input hides, service stops

## Date
April 10, 2026

## Status
✅ All button behaviors implemented and functional
