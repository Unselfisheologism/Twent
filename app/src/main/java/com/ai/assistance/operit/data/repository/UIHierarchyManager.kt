package com.ai.assistance.operit.data.repository

import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.accessibilityservice.AccessibilityServiceInfo
import com.ai.assistance.operit.util.AppLogger
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

/**
 * UI层次结构管理器
 * 负责管理无障碍服务并获取UI层次结构。
 */
object UIHierarchyManager {
    private const val TAG = "UIHierarchyManager"

    /**
     * 检查无障碍服务是否已启用
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
        val packageName = context.packageName
        
        // Log all enabled accessibility services for debugging
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        AppLogger.d(TAG, "isAccessibilityServiceEnabled check:")
        AppLogger.d(TAG, "  - am.isEnabled: ${am.isEnabled}")
        AppLogger.d(TAG, "  - checking package: $packageName")
        AppLogger.d(TAG, "  - enabled services count: ${enabledServices.size}")
        enabledServices.forEach { svc ->
            val svcPackage = svc.resolveInfo.serviceInfo.packageName
            val svcName = svc.resolveInfo.serviceInfo.name
            AppLogger.d(TAG, "    - service: $svcName (package: $svcPackage)")
        }
        
        val isEnabled = am.isEnabled && enabledServices.any {
            it.resolveInfo.serviceInfo.packageName == packageName
        }
        AppLogger.d(TAG, "  - result: $isEnabled")
        
        return isEnabled
    }

    /**
     * 从无障碍服务获取UI层次结构
     */
    suspend fun getUIHierarchy(context: Context): String {
        val service = com.ai.assistance.operit.services.OperitAccessibilityService.instance
        if (service == null) {
            AppLogger.w(TAG, "无障碍服务未运行")
            return ""
        }
        return service.getUIHierarchyXml()
    }

    /**
     * 从UI层次结构的XML中解析出窗口信息（包名）。
     * 活动名称现在通过 getCurrentActivityName() 函数单独获取。
     * @param xmlHierarchy UI层次结构的XML字符串
     * @return 一个Pair，第一个元素是包名，第二个是null（活动名称需单独获取）。
     */
    fun extractWindowInfo(xmlHierarchy: String): Pair<String?, String?> {
        if (xmlHierarchy.isEmpty()) {
            return Pair(null, null)
        }
        try {
            val factory = XmlPullParserFactory.newInstance()
            factory.isNamespaceAware = false
            val parser = factory.newPullParser()
            parser.setInput(StringReader(xmlHierarchy))

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "node") {
                            // 只获取根节点的包名，活动名称通过单独的函数获取
                            val rootPackage = parser.getAttributeValue(null, "package")
                            return Pair(rootPackage, null)
                        }
                    }
                }
                eventType = parser.next()
            }
            
            return Pair(null, null)

        } catch (e: Exception) {
            AppLogger.e(TAG, "解析窗口信息时出错", e)
            return Pair(null, null)
        }
    }

    /**
     * 执行点击操作
     */
    suspend fun performClick(context: Context, x: Int, y: Int): Boolean {
        val service = com.ai.assistance.operit.services.OperitAccessibilityService.instance
        if (service == null) {
            AppLogger.w(TAG, "无障碍服务未运行")
            return false
        }
        return service.performClickAt(x, y)
    }

    suspend fun performLongPress(context: Context, x: Int, y: Int): Boolean {
        val service = com.ai.assistance.operit.services.OperitAccessibilityService.instance
        if (service == null) {
            AppLogger.w(TAG, "无障碍服务未运行")
            return false
        }
        return service.performLongPressAt(x, y)
    }

    /**
     * 执行滑动操作
     */
    suspend fun performSwipe(context: Context, startX: Int, startY: Int, endX: Int, endY: Int, duration: Long): Boolean {
        val service = com.ai.assistance.operit.services.OperitAccessibilityService.instance
        if (service == null) {
            AppLogger.w(TAG, "无障碍服务未运行")
            return false
        }
        return service.performSwipeGesture(startX, startY, endX, endY, duration)
    }

    /**
     * 执行全局操作 (placeholder - 需要实际实现)
     */
    suspend fun performGlobalAction(context: Context, actionId: Int): Boolean {
        // TODO: Implement with accessibility service
        AppLogger.w(TAG, "performGlobalAction needs to be implemented")
        return false
    }

    /**
     * 查找焦点节点 (placeholder - 需要实际实现)
     */
    suspend fun findFocusedNodeId(context: Context): String? {
        // TODO: Implement with accessibility service
        AppLogger.w(TAG, "findFocusedNodeId needs to be implemented")
        return null
    }

    /**
     * 设置节点文本 (placeholder - 需要实际实现)
     */
    suspend fun setTextOnNode(context: Context, nodeId: String, text: String): Boolean {
        // TODO: Implement with accessibility service
        AppLogger.w(TAG, "setTextOnNode needs to be implemented")
        return false
    }

    /**
     * 截取屏幕截图 (placeholder - 需要实际实现)
     */
    suspend fun takeScreenshot(context: Context, path: String, format: String): Boolean {
        // TODO: Implement with accessibility service
        AppLogger.w(TAG, "takeScreenshot needs to be implemented")
        return false
    }

    /**
     * 获取当前Activity名称
     */
    suspend fun getCurrentActivityName(context: Context): String? {
        val service = com.ai.assistance.operit.services.OperitAccessibilityService.instance
        if (service == null) {
            AppLogger.w(TAG, "无障碍服务未运行")
            return null
        }
        return service.getCurrentActivity()
    }
}