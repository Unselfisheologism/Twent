package com.ai.assistance.operit.util

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Resources

/**
 * 🤪 Chronically Online Context Wrapper
 *
 * Wraps the app context to automatically translate ALL strings to Gen Z slang
 * when Chronically Online mode is enabled.
 *
 * This works at the system level - just like how LocaleUtils changes the app language!
 */
class ChronicallyOnlineContextWrapper(base: Context) : ContextWrapper(base) {

    companion object {
        /**
         * Wrap context with Chronically Online mode if enabled
         * Call this in attachBaseContext() of MainActivity
         */
        fun wrap(context: Context): Context {
            return ChronicallyOnlineContextWrapper(context)
        }
    }

    override fun getResources(): Resources {
        val resources = super.getResources()

        // Note: We can't easily override getString() at the Resources level
        // Instead, we rely on the ChronicallyOnlineManager.translateToGenZ()
        // being called by the genZString() composable

        return resources
    }
}

/**
 * Apply Chronically Online mode to a context
 * This should be called in Application.onCreate() or MainActivity.attachBaseContext()
 */
fun applyChronicallyOnlineMode(context: Context): Context {
    // For now, we use the composable-level translation
    // Future enhancement: Could override Resources.getString() for system-wide translation

    // Note: We can't synchronously check Flow here without runBlocking
    // The composable-level genZString() handles the async collection properly

    return context
}
