package com.ai.assistance.operit.services

import android.graphics.Bitmap
import com.ai.assistance.operit.services.automation.OperitAutomationService

class OperitAccessibilityService {
    companion object {
        var instance: OperitAccessibilityService? = null
            private set

        fun setInstance(service: OperitAutomationService?) {
            instance = service
        }
    }
    }

    fun getUIHierarchyXml(): String {
        return instance?.getWindowHierarchySignature() ?: ""
    }

    fun performClickAt(x: Int, y: Int): Boolean {
        instance?.clickOnPoint(x.toFloat(), y.toFloat())
        return true
    }

    fun performLongPressAt(x: Int, y: Int): Boolean {
        instance?.longClickOnPoint(x.toFloat(), y.toFloat())
        return true
    }

    fun performSwipeGesture(startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        instance?.swipe(startX.toFloat(), startY.toFloat(), endX.toFloat(), endY.toFloat(), duration)
        return true
    }

    fun getCurrentActivity(): String {
        return instance?.getCurrentActivityName() ?: "Unknown"
    }
}