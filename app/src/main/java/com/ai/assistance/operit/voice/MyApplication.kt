package com.ai.assistance.operit.voice

import android.content.Context
import com.ai.assistance.operit.voice.intents.IntentRegistry
import com.ai.assistance.operit.voice.intents.impl.DialIntent
import com.ai.assistance.operit.voice.intents.impl.EmailComposeIntent
import com.ai.assistance.operit.voice.intents.impl.ShareTextIntent
import com.ai.assistance.operit.voice.intents.impl.ViewUrlIntent

object MyApplication {
    private var _appContext: Context? = null
    val appContext: Context
        get() = _appContext ?: throw IllegalStateException("MyApplication not initialized. Call init() first.")
    
    private var initialized = false
    
    fun init(context: Context) {
        if (initialized) return
        initialized = true
        
        _appContext = context.applicationContext
        
        IntentRegistry.register(DialIntent())
        IntentRegistry.register(ViewUrlIntent())
        IntentRegistry.register(ShareTextIntent())
        IntentRegistry.register(EmailComposeIntent())
        IntentRegistry.init(context.applicationContext)
    }
}
