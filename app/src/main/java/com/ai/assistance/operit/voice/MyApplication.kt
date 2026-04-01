package com.ai.assistance.operit.voice

import android.content.Context
import com.ai.assistance.operit.core.application.OperitApplication

object MyApplication {
    val appContext: Context
        get() = OperitApplication.instance.applicationContext
}