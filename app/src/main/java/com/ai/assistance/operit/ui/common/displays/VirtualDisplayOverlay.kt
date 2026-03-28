package com.ai.assistance.operit.ui.common.displays

import android.content.Context
import android.hardware.display.DisplayManager
import android.view.Display
import android.os.Build
import android.app.Activity
import android.util.DisplayMetrics
import com.ai.assistance.operit.util.AppLogger

object VirtualDisplayOverlay {
    private const val TAG = "VirtualDisplayOverlay"
    
    @Volatile
    private var virtualDisplay: Display? = null
    
    fun createVirtualDisplay(context: Context, width: Int = 720, height: Int = 1280, density: Int = 320): Display? {
        return try {
            val displayManager = context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val activity = context as? Activity
                val baseDisplay = activity?.display ?: displayManager.getDisplay(Display.DEFAULT_DISPLAY)
                val metrics = DisplayMetrics()
                baseDisplay?.getRealMetrics(metrics)
                
                val virtualDisplayParams = Display.LayoutParams(
                    width,
                    height,
                    density,
                    Display.LayoutParams.FLAG_NOT_TOUCHABLE or Display.LayoutParams.FLAG_NOT_FOCUSABLE
                )
                
                virtualDisplay = displayManager.createVirtualDisplay(
                    "OperitVirtualDisplay",
                    width,
                    height,
                    density,
                    null,
                    virtualDisplayParams
                )
                virtualDisplay
            } else {
                AppLogger.w(TAG, "Virtual display not supported on API ${Build.VERSION.SDK_INT}")
                null
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create virtual display", e)
            null
        }
    }
    
    fun getVirtualDisplay(): Display? = virtualDisplay
    
    fun release() {
        virtualDisplay = null
    }
}