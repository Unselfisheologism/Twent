package com.ai.assistance.operit.core.agent.perception

import android.view.accessibility.AccessibilityNodeInfo

class SemanticParser {

    private val interactiveNodeMap = mutableMapOf<Int, AccessibilityNodeInfo>()
    private var interactiveElementCounter = 0
    private var screenWidth: Int = 0
    private var screenHeight: Int = 0

    fun parseNodeTree(
        rootNode: AccessibilityNodeInfo,
        previousNodes: Set<String>?,
        screenWidth: Int,
        screenHeight: Int
    ): Pair<String, Map<Int, AccessibilityNodeInfo>> {
        this.screenWidth = screenWidth
        this.screenHeight = screenHeight

        interactiveNodeMap.clear()
        interactiveElementCounter = 0
        val stringBuilder = StringBuilder()

        buildStringFromNodeRecursive(rootNode, 0, stringBuilder, previousNodes ?: emptySet())

        return Pair(stringBuilder.toString(), interactiveNodeMap)
    }

    private fun getExtraInfo(node: AccessibilityNodeInfo): String {
        val infoParts = mutableListOf<String>()
        if (node.isCheckable) infoParts.add("checkable")
        if (node.isChecked) infoParts.add("checked")
        if (node.isClickable) infoParts.add("clickable")
        if (node.isEnabled) infoParts.add("enabled")
        if (node.isFocusable) infoParts.add("focusable")
        if (node.isFocused) infoParts.add("focused")
        if (node.isScrollable) infoParts.add("scrollable")
        if (node.isLongClickable) infoParts.add("long clickable")
        if (node.isSelected) infoParts.add("selected")

        return if (infoParts.isNotEmpty()) {
            "This element is ${infoParts.joinToString(", ")}."
        } else {
            ""
        }
    }

    private fun buildStringFromNodeRecursive(
        node: AccessibilityNodeInfo,
        indentLevel: Int,
        builder: StringBuilder,
        previousNodes: Set<String>
    ) {
        if (!node.isVisibleToUser) {
            return
        }

        val text = node.text?.toString() ?: ""
        val contentDesc = node.contentDescription?.toString() ?: ""
        val visibleText = if (text.isNotBlank()) text else contentDesc
        val resourceId = node.viewIdResourceName ?: ""
        val className = node.className?.toString() ?: ""

        val isSemanticallyImportant = resourceId.isNotBlank() || visibleText.isNotBlank()
        val isInteractive = (node.isClickable ||
                node.isLongClickable ||
                node.isCheckable ||
                node.isScrollable ||
                node.isEditable ||
                node.isFocusable) && node.isEnabled

        val shouldPrintNode = isInteractive || isSemanticallyImportant

        if (shouldPrintNode) {
            val nodeKey = "$visibleText|$resourceId|$className"
            val isNew = !previousNodes.contains(nodeKey) && isSemanticallyImportant
            val indent = "\t".repeat(indentLevel)

            if (isInteractive) {
                interactiveElementCounter++
                interactiveNodeMap[interactiveElementCounter] = node

                val newMarker = if (isNew) "* " else ""
                val extraInfo = getExtraInfo(node)
                val simpleClassName = className.removePrefix("android.widget.")

                builder.append("$indent$newMarker[$interactiveElementCounter] ")
                    .append("text:\"${visibleText.replace("\n", " ")}\" ")
                    .append("<$resourceId> ")
                    .append("<$extraInfo> ")
                    .append("<$simpleClassName>\n")

            } else {
                val newMarker = if (isNew) "* " else ""
                builder.append("$indent$newMarker${visibleText.replace("\n", " ")}\n")
            }
        }

        val nextIndent = if (shouldPrintNode) indentLevel + 1 else indentLevel
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                buildStringFromNodeRecursive(child, nextIndent, builder, previousNodes)
            }
        }
    }
}