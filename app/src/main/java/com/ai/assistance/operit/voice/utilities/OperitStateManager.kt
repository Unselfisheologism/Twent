package com.ai.assistance.operit.voice.utilities

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.ai.assistance.operit.voice.ConversationalAgentService
import com.ai.assistance.operit.voice.utilities.SpeechCoordinator
import com.ai.assistance.operit.voice.utilities.VisualFeedbackManager
import java.util.concurrent.CopyOnWriteArrayList

class OperitStateManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "OperitStateManager"
        
        @Volatile private var INSTANCE: OperitStateManager? = null

        fun getInstance(context: Context): OperitStateManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: OperitStateManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val speechCoordinator by lazy { SpeechCoordinator.getInstance(context) }
    private val visualFeedbackManager by lazy { VisualFeedbackManager.getInstance(context) }
    
    private var currentState: OperitState = OperitState.IDLE
    private var hasRecentError: Boolean = false
    private var errorClearRunnable: Runnable? = null
    
    private val stateChangeListeners = CopyOnWriteArrayList<(OperitState) -> Unit>()
    
    private var isMonitoring = false
    private var monitoringRunnable: Runnable? = null
    
    fun addStateChangeListener(listener: (OperitState) -> Unit) {
        stateChangeListeners.add(listener)
    }
    
    fun removeStateChangeListener(listener: (OperitState) -> Unit) {
        stateChangeListeners.remove(listener)
    }
    
    fun getCurrentState(): OperitState = currentState
    
    fun setState(newState: OperitState) {
        Log.d(TAG, "State manually set to: $newState")
        updateState(newState)
    }
    
    fun startMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Already monitoring, skipping start")
            return
        }
        
        isMonitoring = true
        Log.d(TAG, "Starting state monitoring")
        setState(OperitState.IDLE)
    }
    
    fun stopMonitoring() {
        if (!isMonitoring) {
            return
        }
        
        isMonitoring = false
        Log.d(TAG, "Stopping state monitoring")
        
        monitoringRunnable?.let { mainHandler.removeCallbacks(it) }
        errorClearRunnable?.let { mainHandler.removeCallbacks(it) }
        
        setState(OperitState.IDLE)
    }
    
    fun triggerErrorState() {
        Log.d(TAG, "Error state triggered")
        setState(OperitState.ERROR)
        
        errorClearRunnable?.let { mainHandler.removeCallbacks(it) }
        errorClearRunnable = Runnable {
            Log.d(TAG, "Error state cleared, returning to idle")
            setState(OperitState.IDLE)
        }
        mainHandler.postDelayed(errorClearRunnable!!, 3000)
    }

    private fun updateState(newState: OperitState) {
        val previousState = currentState
        currentState = newState
        
        Log.d(TAG, "State updated: $previousState -> $newState")
        
        mainHandler.post {
            stateChangeListeners.forEach { listener ->
                try {
                    listener(newState)
                } catch (e: Exception) {
                    Log.e(TAG, "Error notifying state change listener", e)
                }
            }
        }
    }
    
    fun getStatusText(): String {
        return when (currentState) {
            OperitState.IDLE -> "Ready"
            OperitState.LISTENING -> "Listening..."
            OperitState.PROCESSING -> "Processing..."
            OperitState.SPEAKING -> "Speaking..."
            OperitState.ERROR -> "Error"
        }
    }
    
    fun getStateColor(): Int {
        return DeltaStateColorMapper.getColor(context, currentState)
    }
    
    fun getDeltaVisualState(): DeltaStateColorMapper.DeltaVisualState {
        return DeltaStateColorMapper.getDeltaVisualState(context, currentState)
    }
}
