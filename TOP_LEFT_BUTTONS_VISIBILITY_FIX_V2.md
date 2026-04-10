# Top-Left Buttons Visibility Fix v2

## Problem
Stop (red) and pause-play (yellow) buttons not visible during task execution, even though 4-sided neon glow IS visible.

## Root Cause
WindowManager z-ordering: overlays added later appear on top of earlier ones.

## Solution v2
1. `showTaskActiveGlow()` now accepts button callbacks and calls both `showEdgeGlow()` + `showTopLeftTaskControls()`
2. `hideTaskActiveGlow()` now also hides buttons
3. Added comprehensive logging to diagnose execution flow

## Key Changes
- `VisualFeedbackManager.kt`: Modified `showTaskActiveGlow()` and `hideTaskActiveGlow()` signatures
- `AgentService.kt`: Updated to use new `showTaskActiveGlow(callbacks)` API
- Added extensive logging to track when/where buttons are shown

## Testing
Run: `adb logcat | grep VisualFeedbackManager`
Look for:
- `=== showTaskActiveGlow: Called both showEdgeGlow + showTopLeftTaskControls ===`
- `=== showTopLeftTaskControls CALLED ===`
- `=== showTopLeftTaskControls EXECUTING on main thread ===`
- `Top-left task controls added at edge glow layer.`

If these logs appear but buttons are still invisible, the issue is likely the TOP status overlay (y=150) covering the buttons (y=40dp).

## Files Modified
1. `VisualFeedbackManager.kt` - Enhanced show/hide task glow methods, added logging
2. `AgentService.kt` - Updated to use new API
3. `OverlayManager.kt` - Removed bring-to-front logic (reverted)

Date: April 10, 2026
Status: Ready for testing with logcat verification
