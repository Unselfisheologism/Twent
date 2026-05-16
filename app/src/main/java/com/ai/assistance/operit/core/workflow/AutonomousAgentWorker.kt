package com.ai.assistance.operit.core.workflow

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ai.assistance.operit.api.chat.autonomous.TwAutonomousAgent
import com.ai.assistance.operit.util.AppLogger

class AutonomousAgentWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val mode = inputData.getString(KEY_MODE) ?: "observe"
        AppLogger.d("AutonomousAgentWorker", "Executing mode: $mode")
        
        return try {
            val agent = TwAutonomousAgent.getInstance(applicationContext)
            when (mode) {
                "observe" -> agent.triggerObservation()
                "learn" -> agent.triggerLearning()
                "create" -> agent.triggerContentGeneration()
            }
            Result.success()
        } catch (e: Exception) {
            AppLogger.e("AutonomousAgentWorker", "Worker failed: $mode", e)
            Result.retry()
        }
    }

    companion object {
        const val KEY_MODE = "autonomous_mode"
    }
}