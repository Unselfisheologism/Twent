package com.ai.assistance.operit.voice.v2.actions

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.voice.api.Finger
import com.ai.assistance.operit.voice.v2.ActionResult
import com.ai.assistance.operit.voice.v2.perception.ScreenAnalysis
import com.ai.assistance.operit.voice.v2.fs.FileSystem

class ActionExecutor(private val finger: Finger) {

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun execute(
        action: Action,
        screenAnalysis: ScreenAnalysis,
        context: Context,
        fileSystem: FileSystem
    ): ActionResult {
        Log.d("ActionExecutor", "Action execution stub - action: $action")
        return ActionResult(isDone = true, success = true)
    }
}