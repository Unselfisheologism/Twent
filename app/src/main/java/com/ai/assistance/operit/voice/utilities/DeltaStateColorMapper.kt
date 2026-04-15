package com.ai.assistance.operit.voice.utilities

import android.content.Context
import androidx.core.content.ContextCompat
import com.ai.assistance.operit.R

/**
 * Utility class for mapping OperitState values to their corresponding colors
 * and providing state-related information for the delta symbol.
 */
object DeltaStateColorMapper {

    /**
     * Data class representing the visual state of the delta symbol
     */
    data class DeltaVisualState(
        val state: OperitState,
        val color: Int,
        val statusText: String,
        val colorHex: String
    )

    /**
     * Get the color resource ID for a given OperitState
     */
    fun getColorResourceId(state: OperitState): Int {
        return when (state) {
            OperitState.IDLE -> R.color.delta_idle
            OperitState.LISTENING -> R.color.delta_listening
            OperitState.PROCESSING -> R.color.delta_processing
            OperitState.SPEAKING -> R.color.delta_speaking
            OperitState.ERROR -> R.color.delta_error
        }
    }

    /**
     * Get the resolved color value for a given OperitState
     */
    fun getColor(context: Context, state: OperitState): Int {
        val colorResId = getColorResourceId(state)
        return ContextCompat.getColor(context, colorResId)
    }

    /**
     * Get the status text for a given OperitState
     */
    fun getStatusText(state: OperitState): String {
        return when (state) {
            OperitState.IDLE -> "Ready, tap delta to wake me up!"
            OperitState.LISTENING -> "Listening..."
            OperitState.PROCESSING -> "Processing..."
            OperitState.SPEAKING -> "Speaking..."
            OperitState.ERROR -> "Error"
        }
    }

    /**
     * Get the hex color string for a given OperitState (for debugging/logging)
     */
    fun getColorHex(context: Context, state: OperitState): String {
        val color = getColor(context, state)
        return String.format("#%08X", color)
    }

    /**
     * Get complete visual state information for a given OperitState
     */
    fun getDeltaVisualState(context: Context, state: OperitState): DeltaVisualState {
        return DeltaVisualState(
            state = state,
            color = getColor(context, state),
            statusText = getStatusText(state),
            colorHex = getColorHex(context, state)
        )
    }

    /**
     * Get all available states with their visual information
     */
    fun getAllStates(context: Context): List<DeltaVisualState> {
        return OperitState.values().map { state ->
            getDeltaVisualState(context, state)
        }
    }

    /**
     * Check if a state represents an active operation (not idle or error)
     */
    fun isActiveState(state: OperitState): Boolean {
        return when (state) {
            OperitState.LISTENING, OperitState.PROCESSING, OperitState.SPEAKING -> true
            OperitState.IDLE, OperitState.ERROR -> false
        }
    }

    /**
     * Check if a state represents an error condition
     */
    fun isErrorState(state: OperitState): Boolean {
        return state == OperitState.ERROR
    }

    /**
     * Get the priority of a state for determining which state to display
     * when multiple conditions might be true. Higher numbers = higher priority.
     */
    fun getStatePriority(state: OperitState): Int {
        return when (state) {
            OperitState.ERROR -> 5      // Highest priority
            OperitState.SPEAKING -> 4   // High priority
            OperitState.LISTENING -> 3  // Medium-high priority
            OperitState.PROCESSING -> 2 // Medium priority
            OperitState.IDLE -> 1       // Lowest priority
        }
    }
}