package com.ai.assistance.operit.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.ai.assistance.operit.util.ChronicallyOnlineManager
import androidx.annotation.StringRes

/**
 * 🤪 Gen Z String Localizer
 * 
 * Drop-in replacement for stringResource() that automatically translates
 * to Gen Z slang when Chronically Online mode is enabled.
 * 
 * Usage:
 * ```
 * // Instead of: Text(stringResource(R.string.settings))
 * // Use: Text(genZString(R.string.settings))
 * ```
 */
@Composable
fun genZString(@StringRes resId: Int): String {
    val context = LocalContext.current
    val chronicallyOnlineManager = remember { ChronicallyOnlineManager.getInstance(context) }
    val isChronicallyOnline by chronicallyOnlineManager.isChronicallyOnline.collectAsState(initial = false)
    
    return if (isChronicallyOnline) {
        chronicallyOnlineManager.getGenZString(context, resId)
    } else {
        context.getString(resId)
    }
}

/**
 * Get Gen Z string with format arguments
 */
@Composable
fun genZString(@StringRes resId: Int, vararg formatArgs: Any): String {
    val context = LocalContext.current
    val chronicallyOnlineManager = remember { ChronicallyOnlineManager.getInstance(context) }
    val isChronicallyOnline by chronicallyOnlineManager.isChronicallyOnline.collectAsState(initial = false)
    
    val normalString = context.getString(resId, *formatArgs)
    
    return if (isChronicallyOnline) {
        chronicallyOnlineManager.translateToGenZ(normalString)
    } else {
        normalString
    }
}

/**
 * Transform any string to Gen Z slang
 */
@Composable
fun String.toGenZ(): String {
    val context = LocalContext.current
    val chronicallyOnlineManager = remember { ChronicallyOnlineManager.getInstance(context) }
    val isChronicallyOnline by chronicallyOnlineManager.isChronicallyOnline.collectAsState(initial = false)
    
    return if (isChronicallyOnline) {
        chronicallyOnlineManager.translateToGenZ(this)
    } else {
        this
    }
}
