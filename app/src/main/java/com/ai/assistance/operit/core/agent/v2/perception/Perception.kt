package com.ai.assistance.operit.core.agent.v2.perception

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.api.automation.Eyes
import com.ai.assistance.operit.services.automation.RawScreenData
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async

/**
 * The Perception module is responsible for observing the device screen and
 * creating a structured analysis of the current state.
 *
 * @param eyes An instance of the Eyes class to see the screen (XML, screenshot).
 * @param semanticParser An instance of the SemanticParser to make sense of the XML.
 */
@RequiresApi(Build.VERSION_CODES.R)
class Perception(
    private val eyes: Eyes,
    private val semanticParser: SemanticParser
) {

    /**
     * Analyzes the current screen to produce a comprehensive ScreenAnalysis object.
     * This is the main entry point for this module.
     *
     * It performs multiple observation actions concurrently for efficiency.
     *
     * @param previousState An optional set of node identifiers from the previous state,
     * used to detect new UI elements.
     * @return A ScreenAnalysis object containing the complete state of the screen.
     */
    suspend fun analyze(previousState: Set<String>? = null, all: Boolean? =  false): ScreenAnalysis {
        Log.d("Perception", "Starting screen analysis...")
        return coroutineScope {
            val rawDataDeferred = if (all == true) {
                async { eyes.getAllRawScreenData() }
            } else {
                // Use getAllRawScreenData() for more reliable window detection
                async { eyes.getAllRawScreenData() }
            }
        val keyboardStatusDeferred = async { eyes.getKeyBoardStatus() }
        val currentActivity = async { eyes.getCurrentActivityName() }
        val rawTree = rawDataDeferred.await() ?: RawScreenData(
            rootNode = null,
            activityName = null,
            pixelsAbove = 0,
            pixelsBelow = 0,
            screenWidth = 0,
            screenHeight = 0
        )
        Log.d("Perception", "Raw screen data - rootNode: ${rawTree.rootNode != null}, activity: ${rawTree.activityName}")
        val isKeyboardOpen = keyboardStatusDeferred.await()
        val activityName = currentActivity.await()
        Log.d("Perception", "Activity: $activityName, keyboard: $isKeyboardOpen")
        val rootNode = rawTree.rootNode

        // Parse the XML from the raw data
            if(rootNode != null) {
                Log.d("Perception", "Parsing node tree...")
                var (uiRepresentation, elementMap) =
                    semanticParser.parseNodeTree(
                        rootNode,
                        previousState,
                        rawTree.screenWidth,
                        rawTree.screenHeight
                    )
                
                Log.d("Perception", "Parsed ${elementMap.size} interactive elements")
                Log.d("Perception", "First 10 element IDs: ${elementMap.keys.take(10).toList()}")

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
                    uiRepresentation = "⚠️ The screen is empty or contains no interactive elements."
                }

                val normalizedRep = uiRepresentation.lowercase()
                val isLikelyEmpty = normalizedRep.contains("empty") || 
                               normalizedRep.contains("no items") ||
                               normalizedRep.contains("no files") ||
                               normalizedRep.contains("no folders") ||
                               normalizedRep.contains("no results") ||
                               normalizedRep.contains("nothing here")

                if (elementMap.isEmpty() || isLikelyEmpty) {
                    uiRepresentation = "📭 $uiRepresentation\n➡️ If target items aren't visible, try navigating back or to a different screen."
                }

                ScreenAnalysis(
                    uiRepresentation = uiRepresentation,
                    isKeyboardOpen = isKeyboardOpen,
                    activityName = activityName,
                    elementMap = elementMap,
                    scrollUp = rawTree.pixelsAbove,
                    scrollDown = rawTree.pixelsBelow
                )
            } else{
                ScreenAnalysis(
                    uiRepresentation = "⚠️ Screen analysis returned no data. The app may have crashed or be loading.",
                    isKeyboardOpen = isKeyboardOpen,
                    activityName = activityName,
                    elementMap = mutableMapOf(),
                    scrollUp = rawTree.pixelsAbove,
                    scrollDown = rawTree.pixelsBelow
                )
            }
    }
    }
}