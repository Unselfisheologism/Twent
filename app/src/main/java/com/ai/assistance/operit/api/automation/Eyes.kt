package com.ai.assistance.operit.api.automation

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import com.ai.assistance.operit.services.automation.OperitAutomationService
import com.ai.assistance.operit.services.automation.RawScreenData

class Eyes(private val context: Context) {

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun openEyes(): Bitmap? {
        val service = OperitAutomationService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return null
        }
        return service.captureScreenshot()
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun openPureXMLEyes(): String {
        val service = OperitAutomationService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return "<hierarchy/>"
        }
        Log.d("Eyes", "Requesting UI layout dump...")
        return service.dumpWindowHierarchy(true)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    suspend fun openXMLEyes(): String {
        val service = OperitAutomationService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return "<hierarchy/>"
        }
        Log.d("Eyes", "Requesting UI layout dump...")
        return service.dumpWindowHierarchy()
    }

    fun getKeyBoardStatus(): Boolean {
        val service = OperitAutomationService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return false
        }
        return service.isTypingAvailable()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getRawScreenData(): RawScreenData? {
        val service = OperitAutomationService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return RawScreenData(null, null, 0, 0, 0, 0)
        }
        return service.getScreenAnalysisData()
    }

    @RequiresApi(Build.VERSION_CODES.R)
    suspend fun getAllRawScreenData(): RawScreenData? {
        val service = OperitAutomationService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return RawScreenData(null, null, 0, 0, 0, 0)
        }
        return service.getAllScreenAnalysisData()
    }

    fun getCurrentActivityName(): String {
        val service = OperitAutomationService.instance
        if (service == null) {
            Log.e("Eyes", "Accessibility Service is not running!")
            return "Unknown"
        }
        return service.getCurrentActivityName()
    }
}