package com.ai.assistance.operit.core.agent.perception

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.api.automation.Eyes
import com.ai.assistance.operit.services.automation.RawScreenData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

@RequiresApi(Build.VERSION_CODES.R)
class Perception(
    private val eyes: Eyes,
    private val semanticParser: SemanticParser
) {
    private var lastScreenshot: Bitmap? = null

    suspend fun analyze(previousState: Set<String>? = null, all: Boolean? = false, includeScreenshot: Boolean = true): ScreenAnalysis {
        return coroutineScope {
            val rawDataDeferred = if (all == true) {
                async { eyes.getAllRawScreenData() }
            } else {
                async { eyes.getRawScreenData() }
            }
            val keyboardStatusDeferred = async { eyes.getKeyBoardStatus() }
            val currentActivity = async { eyes.getCurrentActivityName() }
            val screenshotDeferred = if (includeScreenshot) {
                async { eyes.openEyes() }
            } else {
                null
            }

            val rawTree = rawDataDeferred.await() ?: RawScreenData(null, 0, 0, 0, 0)
            val isKeyboardOpen = keyboardStatusDeferred.await()
            val activityName = currentActivity.await()
            val rootNode = rawTree.rootNode

            val screenshot = screenshotDeferred?.await()

            if (screenshot != null) {
                lastScreenshot = screenshot
            }

            if (rootNode != null) {
                var (uiRepresentation, elementMap) = semanticParser.parseNodeTree(
                    rootNode,
                    previousState,
                    rawTree.screenWidth,
                    rawTree.screenHeight
                )

                val hasContentAbove = rawTree.pixelsAbove > 0
                val hasContentBelow = rawTree.pixelsBelow > 0

                if (uiRepresentation.isNotBlank()) {
                    if (hasContentAbove) {
                        uiRepresentation = "... ${rawTree.pixelsAbove} pixels above - scroll up to see more ...\n$uiRepresentation"
                    } else {
                        uiRepresentation = "[Start of page]\n$uiRepresentation"
                    }
                    if (hasContentBelow) {
                        uiRepresentation = "$uiRepresentation\n... ${rawTree.pixelsBelow} pixels below - scroll down to see more ..."
                    } else {
                        uiRepresentation = "$uiRepresentation\n[End of page]"
                    }
                } else {
                    uiRepresentation = "The screen is empty or contains no interactive elements."
                }

                ScreenAnalysis(
                    uiRepresentation = uiRepresentation,
                    isKeyboardOpen = isKeyboardOpen,
                    activityName = activityName,
                    elementMap = elementMap,
                    scrollUp = rawTree.pixelsAbove,
                    scrollDown = rawTree.pixelsBelow,
                    screenshot = screenshot
                )
            } else {
                ScreenAnalysis(
                    uiRepresentation = "Unable to get screen state",
                    isKeyboardOpen = isKeyboardOpen,
                    activityName = activityName,
                    elementMap = mutableMapOf(),
                    scrollUp = rawTree.pixelsAbove,
                    scrollDown = rawTree.pixelsBelow,
                    screenshot = screenshot
                )
            }
        }
    }

    fun getLastScreenshot(): Bitmap? = lastScreenshot
}