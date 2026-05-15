package com.ai.assistance.operit.core.tools.defaultTool.standard

import android.content.Context
import com.ai.assistance.operit.util.AppLogger
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.WorkflowDetailResultData
import com.ai.assistance.operit.core.tools.WorkflowListResultData
import com.ai.assistance.operit.core.tools.WorkflowResultData
import com.ai.assistance.operit.data.model.*
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 工作流管理工具
 * 提供工作流的创建、查询、更新和删除功能
 */
class StandardWorkflowTools(private val context: Context) {

    companion object {
        private const val TAG = "StandardWorkflowTools"
    }

    private val workflowRepository = WorkflowRepository(context)
    private val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "__type"
    }

    /**
     * 获取所有工作流
     */
    suspend fun getAllWorkflows(tool: AITool): ToolResult {
        return try {
            val result = workflowRepository.getAllWorkflows()
            
            if (result.isSuccess) {
                val workflows = result.getOrNull() ?: emptyList()
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = WorkflowListResultData(
                        workflows = workflows.map { workflow ->
                            WorkflowResultData(
                                id = workflow.id,
                                name = workflow.name,
                                description = workflow.description,
                                nodeCount = workflow.nodes.size,
                                connectionCount = workflow.connections.size,
                                enabled = workflow.enabled,
                                createdAt = workflow.createdAt,
                                updatedAt = workflow.updatedAt,
                                lastExecutionTime = workflow.lastExecutionTime,
                                lastExecutionStatus = workflow.lastExecutionStatus?.name,
                                totalExecutions = workflow.totalExecutions,
                                successfulExecutions = workflow.successfulExecutions,
                                failedExecutions = workflow.failedExecutions
                            )
                        },
                        totalCount = workflows.size
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowListResultData.empty(),
                    error = "Failed to get workflow list: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get all workflows", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowListResultData.empty(),
                error = "Failed to get workflow list: ${e.message}"
            )
        }
    }

    /**
     * 创建工作流
     */
    suspend fun createWorkflow(tool: AITool): ToolResult {
        return try {
            val name = tool.parameters.find { it.name == "name" }?.value
            if (name.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Workflow name cannot be empty"
                )
            }

            val description = tool.parameters.find { it.name == "description" }?.value ?: ""
            val nodesJson = tool.parameters.find { it.name == "nodes" }?.value
            val connectionsJson = tool.parameters.find { it.name == "connections" }?.value
            val enabled = tool.parameters.find { it.name == "enabled" }?.value?.toBoolean() ?: true

            // 解析节点
            val nodes = if (!nodesJson.isNullOrBlank()) {
                parseNodes(nodesJson)
            } else {
                emptyList()
            }

            // 解析连接
            val connections = if (!connectionsJson.isNullOrBlank()) {
                parseConnections(connectionsJson, nodes)
            } else {
                emptyList()
            }

            val workflow = Workflow(
                name = name,
                description = description,
                nodes = nodes,
                connections = connections,
                enabled = enabled
            )

            val result = workflowRepository.createWorkflow(workflow)
            
            if (result.isSuccess) {
                val createdWorkflow = result.getOrNull()!!
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = WorkflowDetailResultData(
                        id = createdWorkflow.id,
                        name = createdWorkflow.name,
                        description = createdWorkflow.description,
                        nodes = createdWorkflow.nodes,
                        connections = createdWorkflow.connections,
                        enabled = createdWorkflow.enabled,
                        createdAt = createdWorkflow.createdAt,
                        updatedAt = createdWorkflow.updatedAt,
                        lastExecutionTime = createdWorkflow.lastExecutionTime,
                        lastExecutionStatus = createdWorkflow.lastExecutionStatus?.name,
                        totalExecutions = createdWorkflow.totalExecutions,
                        successfulExecutions = createdWorkflow.successfulExecutions,
                        failedExecutions = createdWorkflow.failedExecutions
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Failed to create workflow: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to create workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowDetailResultData.empty(),
                error = "Failed to create workflow: ${e.message}"
            )
        }
    }

    /**
     * 获取工作流详情
     */
    suspend fun getWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Workflow ID cannot be empty"
                )
            }

            val result = workflowRepository.getWorkflowById(workflowId)
            
            if (result.isSuccess) {
                val workflow = result.getOrNull()
                if (workflow == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = WorkflowDetailResultData.empty(),
                        error = "Workflow not found: $workflowId"
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = WorkflowDetailResultData(
                            id = workflow.id,
                            name = workflow.name,
                            description = workflow.description,
                            nodes = workflow.nodes,
                            connections = workflow.connections,
                            enabled = workflow.enabled,
                            createdAt = workflow.createdAt,
                            updatedAt = workflow.updatedAt,
                            lastExecutionTime = workflow.lastExecutionTime,
                            lastExecutionStatus = workflow.lastExecutionStatus?.name,
                            totalExecutions = workflow.totalExecutions,
                            successfulExecutions = workflow.successfulExecutions,
                            failedExecutions = workflow.failedExecutions
                        )
                    )
                }
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Failed to get workflow: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowDetailResultData.empty(),
                error = "Failed to get workflow: ${e.message}"
            )
        }
    }

    /**
     * 更新工作流
     */
    suspend fun updateWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Workflow ID cannot be empty"
                )
            }

            // 获取现有工作流
            val existingResult = workflowRepository.getWorkflowById(workflowId)
            if (existingResult.isFailure || existingResult.getOrNull() == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Workflow not found: $workflowId"
                )
            }

            val existingWorkflow = existingResult.getOrNull()!!

            // 更新字段（如果提供了新值）
            val name = tool.parameters.find { it.name == "name" }?.value ?: existingWorkflow.name
            val description = tool.parameters.find { it.name == "description" }?.value ?: existingWorkflow.description
            val nodesJson = tool.parameters.find { it.name == "nodes" }?.value
            val connectionsJson = tool.parameters.find { it.name == "connections" }?.value
            val enabledParam = tool.parameters.find { it.name == "enabled" }?.value
            val enabled = if (enabledParam != null) enabledParam.toBoolean() else existingWorkflow.enabled

            // 解析节点（如果提供了）
            val nodes = if (!nodesJson.isNullOrBlank()) {
                parseNodes(nodesJson)
            } else {
                existingWorkflow.nodes
            }

            // 解析连接（如果提供了）
            val connections = if (!connectionsJson.isNullOrBlank()) {
                parseConnections(connectionsJson, nodes)
            } else {
                existingWorkflow.connections
            }

            val updatedWorkflow = existingWorkflow.copy(
                name = name,
                description = description,
                nodes = nodes,
                connections = connections,
                enabled = enabled
            )

            val result = workflowRepository.updateWorkflow(updatedWorkflow)
            
            if (result.isSuccess) {
                val savedWorkflow = result.getOrNull()!!
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = WorkflowDetailResultData(
                        id = savedWorkflow.id,
                        name = savedWorkflow.name,
                        description = savedWorkflow.description,
                        nodes = savedWorkflow.nodes,
                        connections = savedWorkflow.connections,
                        enabled = savedWorkflow.enabled,
                        createdAt = savedWorkflow.createdAt,
                        updatedAt = savedWorkflow.updatedAt,
                        lastExecutionTime = savedWorkflow.lastExecutionTime,
                        lastExecutionStatus = savedWorkflow.lastExecutionStatus?.name,
                        totalExecutions = savedWorkflow.totalExecutions,
                        successfulExecutions = savedWorkflow.successfulExecutions,
                        failedExecutions = savedWorkflow.failedExecutions
                    )
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Failed to update workflow: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowDetailResultData.empty(),
                error = "Failed to update workflow: ${e.message}"
            )
        }
    }

    /**
     * 差异更新工作流（增量 patch）
     */
    suspend fun patchWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Workflow ID cannot be empty"
                )
            }

            // 获取现有工作流
            val existingResult = workflowRepository.getWorkflowById(workflowId)
            if (existingResult.isFailure || existingResult.getOrNull() == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Workflow not found: $workflowId"
                )
            }

            val existingWorkflow = existingResult.getOrNull()!!

            val nameParam = tool.parameters.find { it.name == "name" }?.value
            val descriptionParam = tool.parameters.find { it.name == "description" }?.value
            val enabledParam = tool.parameters.find { it.name == "enabled" }?.value
            val enabled = if (enabledParam != null) enabledParam.toBoolean() else existingWorkflow.enabled

            val nodePatchesJson = tool.parameters.find { it.name == "node_patches" }?.value
            val connectionPatchesJson = tool.parameters.find { it.name == "connection_patches" }?.value

            val nodes = existingWorkflow.nodes.toMutableList()
            val connections = existingWorkflow.connections.toMutableList()

            fun buildNodeDetailResult(savedWorkflow: Workflow): ToolResult {
                return ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = WorkflowDetailResultData(
                        id = savedWorkflow.id,
                        name = savedWorkflow.name,
                        description = savedWorkflow.description,
                        nodes = savedWorkflow.nodes,
                        connections = savedWorkflow.connections,
                        enabled = savedWorkflow.enabled,
                        createdAt = savedWorkflow.createdAt,
                        updatedAt = savedWorkflow.updatedAt,
                        lastExecutionTime = savedWorkflow.lastExecutionTime,
                        lastExecutionStatus = savedWorkflow.lastExecutionStatus?.name,
                        totalExecutions = savedWorkflow.totalExecutions,
                        successfulExecutions = savedWorkflow.successfulExecutions,
                        failedExecutions = savedWorkflow.failedExecutions
                    )
                )
            }

            fun mergePosition(existing: NodePosition, patchObj: JSONObject?): NodePosition {
                if (patchObj == null) return existing

                val x = if (patchObj.has("x")) patchObj.optDouble("x", existing.x.toDouble()).toFloat() else existing.x
                val y = if (patchObj.has("y")) patchObj.optDouble("y", existing.y.toDouble()).toFloat() else existing.y
                return NodePosition(x = x, y = y)
            }

            fun mergeStringMap(existing: Map<String, String>, patchObj: JSONObject?): Map<String, String> {
                if (patchObj == null) return existing
                val merged = existing.toMutableMap()
                val keys = patchObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    merged[k] = patchObj.optString(k, "")
                }
                return merged
            }

            fun mergeParameterValueMap(
                existing: Map<String, ParameterValue>,
                patchObj: JSONObject?
            ): Map<String, ParameterValue> {
                if (patchObj == null) return existing
                val merged = existing.toMutableMap()
                val keys = patchObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    merged[k] = parseParameterValue(patchObj.opt(k))
                }
                return merged
            }

            fun ensureSameType(existingNode: WorkflowNode, patchObj: JSONObject) {
                val patchType = patchObj.optString("type", "").trim()
                if (patchType.isNotBlank() && patchType != existingNode.type) {
                    throw IllegalArgumentException("Node type cannot be changed in patch: ${existingNode.type} -> $patchType")
                }
            }

            fun mergeNode(existingNode: WorkflowNode, patchObj: JSONObject): WorkflowNode {
                ensureSameType(existingNode, patchObj)

                val name = if (patchObj.has("name")) patchObj.optString("name", existingNode.name) else existingNode.name
                val description = if (patchObj.has("description")) patchObj.optString("description", existingNode.description) else existingNode.description
                val position = mergePosition(existingNode.position, patchObj.optJSONObject("position"))

                return when (existingNode) {
                    is TriggerNode -> {
                        val triggerType = if (patchObj.has("triggerType")) patchObj.optString("triggerType", existingNode.triggerType) else existingNode.triggerType
                        val triggerConfig = mergeStringMap(existingNode.triggerConfig, patchObj.optJSONObject("triggerConfig"))
                        existingNode.copy(
                            name = name,
                            description = description,
                            position = position,
                            triggerType = triggerType,
                            triggerConfig = triggerConfig
                        )
                    }
                    is ExecuteNode -> {
                        val actionType = if (patchObj.has("actionType")) patchObj.optString("actionType", existingNode.actionType) else existingNode.actionType
                        val actionConfig = mergeParameterValueMap(existingNode.actionConfig, patchObj.optJSONObject("actionConfig"))
                        val jsCode = if (patchObj.has("jsCode")) {
                            when (val raw = patchObj.opt("jsCode")) {
                                null, JSONObject.NULL -> null
                                else -> raw.toString()
                            }
                        } else {
                            existingNode.jsCode
                        }

                        existingNode.copy(
                            name = name,
                            description = description,
                            position = position,
                            actionType = actionType,
                            actionConfig = actionConfig,
                            jsCode = jsCode
                        )
                    }
                    is ConditionNode -> {
                        val left = if (patchObj.has("left")) parseParameterValue(patchObj.opt("left")) else existingNode.left
                        val right = if (patchObj.has("right")) parseParameterValue(patchObj.opt("right")) else existingNode.right
                        val operator = if (patchObj.has("operator")) {
                            val operatorRaw = patchObj.optString("operator", existingNode.operator.name)
                            try {
                                ConditionOperator.valueOf(operatorRaw.trim().uppercase())
                            } catch (_: Exception) {
                                existingNode.operator
                            }
                        } else {
                            existingNode.operator
                        }

                        existingNode.copy(
                            name = name,
                            description = description,
                            position = position,
                            left = left,
                            operator = operator,
                            right = right
                        )
                    }
                    is LogicNode -> {
                        val operator = if (patchObj.has("operator")) {
                            val operatorRaw = patchObj.optString("operator", existingNode.operator.name)
                            try {
                                LogicOperator.valueOf(operatorRaw.trim().uppercase())
                            } catch (_: Exception) {
                                existingNode.operator
                            }
                        } else {
                            existingNode.operator
                        }

                        existingNode.copy(
                            name = name,
                            description = description,
                            position = position,
                            operator = operator
                        )
                    }
                    is ExtractNode -> {
                        val source = if (patchObj.has("source")) parseParameterValue(patchObj.opt("source")) else existingNode.source
                        val mode = if (patchObj.has("mode")) {
                            val modeRaw = patchObj.optString("mode", existingNode.mode.name)
                            try {
                                ExtractMode.valueOf(modeRaw.trim().uppercase())
                            } catch (_: Exception) {
                                existingNode.mode
                            }
                        } else {
                            existingNode.mode
                        }
                        val expression = if (patchObj.has("expression")) patchObj.optString("expression", existingNode.expression) else existingNode.expression
                        val group = if (patchObj.has("group")) patchObj.optInt("group", existingNode.group) else existingNode.group
                        val defaultValue = if (patchObj.has("defaultValue")) patchObj.optString("defaultValue", existingNode.defaultValue) else existingNode.defaultValue

                        val others = if (patchObj.has("others")) parseParameterValueList(patchObj.opt("others")) else existingNode.others
                        val startIndex = if (patchObj.has("startIndex")) patchObj.optInt("startIndex", existingNode.startIndex) else existingNode.startIndex
                        val length = if (patchObj.has("length")) patchObj.optInt("length", existingNode.length) else existingNode.length
                        val randomMin = if (patchObj.has("randomMin")) patchObj.optInt("randomMin", existingNode.randomMin) else existingNode.randomMin
                        val randomMax = if (patchObj.has("randomMax")) patchObj.optInt("randomMax", existingNode.randomMax) else existingNode.randomMax
                        val randomStringLength =
                            if (patchObj.has("randomStringLength")) patchObj.optInt("randomStringLength", existingNode.randomStringLength) else existingNode.randomStringLength
                        val randomStringCharset =
                            if (patchObj.has("randomStringCharset")) patchObj.optString("randomStringCharset", existingNode.randomStringCharset) else existingNode.randomStringCharset

                        val useFixed = if (patchObj.has("useFixed")) patchObj.optBoolean("useFixed", existingNode.useFixed) else existingNode.useFixed
                        val fixedValue = if (patchObj.has("fixedValue")) patchObj.optString("fixedValue", existingNode.fixedValue) else existingNode.fixedValue

                        existingNode.copy(
                            name = name,
                            description = description,
                            position = position,
                            source = source,
                            mode = mode,
                            expression = expression,
                            group = group,
                            defaultValue = defaultValue,
                            others = others,
                            startIndex = startIndex,
                            length = length,
                            randomMin = randomMin,
                            randomMax = randomMax,
                            randomStringLength = randomStringLength,
                            randomStringCharset = randomStringCharset,
                            useFixed = useFixed,
                            fixedValue = fixedValue
                        )
                    }
                    is MCPNode -> {
                        val serverName = if (patchObj.has("serverName")) patchObj.optString("serverName", existingNode.serverName) else existingNode.serverName
                        val toolName = if (patchObj.has("toolName")) patchObj.optString("toolName", existingNode.toolName) else existingNode.toolName
                        val toolDescription = if (patchObj.has("toolDescription")) patchObj.optString("toolDescription", existingNode.toolDescription) else existingNode.toolDescription
                        val parameters = mergeParameterValueMap(existingNode.parameters, patchObj.optJSONObject("parameters"))

                        existingNode.copy(
                            name = name,
                            description = description,
                            position = position,
                            serverName = serverName,
                            toolName = toolName,
                            toolDescription = toolDescription,
                            parameters = parameters
                        )
                    }
                    is IntegrationNode -> {
                        // Get existing values from IntegrationNode
                        val currentWebhookConfig = existingNode.webhookConfig
                        val currentMcpConfig = existingNode.mcpServerConfig

                        val integrationType = if (patchObj.has("integrationType")) patchObj.optString("integrationType", existingNode.integrationType) else existingNode.integrationType
                        val toolkitName = if (patchObj.has("toolkitName")) patchObj.optString("toolkitName", existingNode.toolkit) else existingNode.toolkit
                        val action = if (patchObj.has("action")) patchObj.optString("action", existingNode.actionId) else existingNode.actionId
                        val parameters = mergeParameterValueMap(existingNode.parameters, patchObj.optJSONObject("parameters"))
                        val webhookUrl = if (patchObj.has("webhookUrl")) patchObj.optString("webhookUrl", currentWebhookConfig?.url ?: "") else currentWebhookConfig?.url ?: ""
                        val webhookMethod = if (patchObj.has("webhookMethod")) patchObj.optString("webhookMethod", currentWebhookConfig?.method ?: "GET") else currentWebhookConfig?.method ?: "GET"
                        val webhookHeaders = if (patchObj.has("webhookHeaders")) patchObj.optString("webhookHeaders", "") else ""
                        val webhookAuthType = if (patchObj.has("webhookAuthType")) patchObj.optString("webhookAuthType", "") else ""
                        val mcpServerName = if (patchObj.has("mcpServerName")) patchObj.optString("mcpServerName", currentMcpConfig?.serverName ?: "") else currentMcpConfig?.serverName ?: ""
                        val oauthAccountId = if (patchObj.has("oauthAccountId")) patchObj.optString("oauthAccountId", existingNode.accountId ?: "") else existingNode.accountId ?: ""
                        val timeout = if (patchObj.has("timeout")) patchObj.optInt("timeout", (existingNode.timeout / 1000).toInt()) else (existingNode.timeout / 1000).toInt()

                        // Map JSON field names to IntegrationNode field names
                        val webhookConfig = if (webhookUrl.isNotBlank()) {
                            com.ai.assistance.operit.data.model.IntegrationWebhookConfig(
                                url = webhookUrl,
                                method = webhookMethod,
                                headers = currentWebhookConfig?.headers ?: emptyMap(),
                                apiKeyRequired = webhookAuthType == "api_key"
                            )
                        } else null

                        val mcpServerConfig = if (mcpServerName.isNotBlank()) {
                            com.ai.assistance.operit.data.model.IntegrationMcpServerConfig(
                                serverName = mcpServerName,
                                serverId = currentMcpConfig?.serverId,
                                toolName = currentMcpConfig?.toolName ?: "",
                                parameters = currentMcpConfig?.parameters ?: emptyMap()
                            )
                        } else null

                        existingNode.copy(
                            name = name,
                            description = description,
                            position = position,
                            integrationType = integrationType,
                            toolkit = toolkitName,
                            actionId = action,
                            parameters = parameters,
                            webhookConfig = webhookConfig,
                            mcpServerConfig = mcpServerConfig,
                            accountId = if (oauthAccountId.isNotBlank()) oauthAccountId else null,
                            timeout = timeout.toLong() * 1000
                        )
                    }
                }
            }

            // Apply node patches
            if (!nodePatchesJson.isNullOrBlank()) {
                val patchArray = JSONArray(nodePatchesJson)
                for (i in 0 until patchArray.length()) {
                    val patchObj = patchArray.getJSONObject(i)
                    val op = patchObj.optString("op", "").trim().lowercase()
                    val patchId = patchObj.optString("id", "").trim()
                    val nodeObj = patchObj.optJSONObject("node")

                    when (op) {
                        "add" -> {
                            if (nodeObj == null) throw IllegalArgumentException("node_patches[$i] missing node")
                            if (patchId.isNotBlank()) nodeObj.put("id", patchId)
                            val parsed = parseNode(nodeObj) ?: throw IllegalArgumentException("node_patches[$i] failed to parse node")
                            if (nodes.any { it.id == parsed.id }) {
                                throw IllegalArgumentException("Node already exists: ${parsed.id}")
                            }
                            nodes.add(parsed)
                        }
                        "update" -> {
                            val id = patchId.ifBlank { nodeObj?.optString("id", "")?.trim().orEmpty() }
                            if (id.isBlank()) throw IllegalArgumentException("node_patches[$i] update missing id")
                            val existingIndex = nodes.indexOfFirst { it.id == id }
                            if (existingIndex < 0) throw IllegalArgumentException("Node not found: $id")
                            if (nodeObj == null) throw IllegalArgumentException("node_patches[$i] update missing node")
                            val updated = mergeNode(nodes[existingIndex], nodeObj)
                            nodes[existingIndex] = updated
                        }
                        "remove" -> {
                            val id = patchId
                            if (id.isBlank()) throw IllegalArgumentException("node_patches[$i] remove missing id")
                            val removed = nodes.removeAll { it.id == id }
                            if (!removed) throw IllegalArgumentException("Node not found: $id")
                            connections.removeAll { it.sourceNodeId == id || it.targetNodeId == id }
                        }
                        else -> throw IllegalArgumentException("node_patches[$i] op only supports add/update/remove")
                    }
                }
            }

            // Apply connection patches
            if (!connectionPatchesJson.isNullOrBlank()) {
                val nodeIdList = nodes.map { it.id }
                val nodeIdSet = nodeIdList.toSet()
                val nodeNameToIds = nodes.groupBy { it.name.trim() }.mapValues { (_, v) -> v.map { it.id } }

                val patchArray = JSONArray(connectionPatchesJson)
                for (i in 0 until patchArray.length()) {
                    val patchObj = patchArray.getJSONObject(i)
                    val op = patchObj.optString("op", "").trim().lowercase()
                    val patchId = patchObj.optString("id", "").trim()
                    val connObj = patchObj.optJSONObject("connection")

                    when (op) {
                        "add" -> {
                            if (connObj == null) throw IllegalArgumentException("connection_patches[$i] missing connection")
                            if (patchId.isNotBlank()) connObj.put("id", patchId)
                            val parsed = parseConnection(connObj, nodeIdList, nodeIdSet, nodeNameToIds)
                                ?: throw IllegalArgumentException("connection_patches[$i] failed to parse connection")
                            if (connections.any { it.id == parsed.id }) {
                                throw IllegalArgumentException("Connection already exists: ${parsed.id}")
                            }
                            connections.add(parsed)
                        }
                        "update" -> {
                            val id = patchId.ifBlank { connObj?.optString("id", "")?.trim().orEmpty() }
                            if (id.isBlank()) throw IllegalArgumentException("connection_patches[$i] update missing id")
                            val existingIndex = connections.indexOfFirst { it.id == id }
                            if (existingIndex < 0) throw IllegalArgumentException("Connection not found: $id")

                            val existingConn = connections[existingIndex]
                            val merged = JSONObject().apply {
                                put("id", existingConn.id)
                                put("sourceNodeId", existingConn.sourceNodeId)
                                put("targetNodeId", existingConn.targetNodeId)
                                if (existingConn.condition != null) {
                                    put("condition", existingConn.condition)
                                }
                            }

                            if (connObj == null) throw IllegalArgumentException("connection_patches[$i] update missing connection")
                            val keys = connObj.keys()
                            while (keys.hasNext()) {
                                val k = keys.next()
                                merged.put(k, connObj.get(k))
                            }

                            val parsed = parseConnection(merged, nodeIdList, nodeIdSet, nodeNameToIds)
                                ?: throw IllegalArgumentException("connection_patches[$i] failed to parse connection")
                            connections[existingIndex] = parsed
                        }
                        "remove" -> {
                            val id = patchId
                            if (id.isBlank()) throw IllegalArgumentException("connection_patches[$i] remove missing id")
                            val removed = connections.removeAll { it.id == id }
                            if (!removed) throw IllegalArgumentException("Connection not found: $id")
                        }
                        else -> throw IllegalArgumentException("connection_patches[$i] op only supports add/update/remove")
                    }
                }
            }

            // 清理非法连接（例如节点被删掉后）
            val nodeIdSet = nodes.map { it.id }.toSet()
            connections.removeAll { it.sourceNodeId !in nodeIdSet || it.targetNodeId !in nodeIdSet || it.sourceNodeId == it.targetNodeId }

            val updatedWorkflow = existingWorkflow.copy(
                name = nameParam ?: existingWorkflow.name,
                description = descriptionParam ?: existingWorkflow.description,
                nodes = nodes,
                connections = connections,
                enabled = enabled
            )

            val result = workflowRepository.updateWorkflow(updatedWorkflow)
            if (result.isSuccess) {
                buildNodeDetailResult(result.getOrNull()!!)
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = WorkflowDetailResultData.empty(),
                    error = "Failed to update workflow: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to patch workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = WorkflowDetailResultData.empty(),
                error = "Failed to update workflow: ${e.message}"
            )
        }
    }

    /**
     * 删除工作流
     */
    suspend fun deleteWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow ID cannot be empty"
                )
            }

            val result = workflowRepository.deleteWorkflow(workflowId)
            
            if (result.isSuccess) {
                val deleted = result.getOrNull() ?: false
                if (deleted) {
                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData("Workflow deleted: $workflowId")
                    )
                } else {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Workflow not found or deletion failed: $workflowId"
                    )
                }
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to delete workflow: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete workflow: ${e.message}"
            )
        }
    }

    /**
     * 触发工作流执行
     */
    suspend fun triggerWorkflow(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow ID cannot be empty"
                )
            }

            val result = workflowRepository.triggerWorkflow(workflowId)
            
            if (result.isSuccess) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData(result.getOrNull() ?: "Workflow executed successfully")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to trigger workflow: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to trigger workflow", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to trigger workflow: ${e.message}"
            )
        }
    }

    /**
     * 解析节点JSON字符串
     */
    private fun parseNodes(nodesJson: String): List<WorkflowNode> {
        return try {
            val jsonArray = JSONArray(nodesJson)
            val nodes = mutableListOf<WorkflowNode>()

            for (i in 0 until jsonArray.length()) {
                val nodeObj = jsonArray.getJSONObject(i)
                val node = parseNode(nodeObj)
                if (node != null) {
                    nodes.add(node)
                }
            }

            if (jsonArray.length() > 0 && nodes.isEmpty()) {
                throw IllegalArgumentException("Failed to parse nodes: Please provide type=trigger/execute/condition/logic/extract for each node (or provide __type=...TriggerNode/...ExecuteNode for inference)")
            }

            nodes
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse nodes JSON", e)
            throw IllegalArgumentException("Invalid node JSON format: ${e.message}")
        }
    }

    /**
     * 解析单个节点
     */
    private fun parseNode(nodeObj: JSONObject): WorkflowNode? {
        return try {
            val type = nodeObj.optString("type", "").trim().ifBlank {
                inferNodeType(nodeObj)
            }
            val id = nodeObj.optString("id", UUID.randomUUID().toString())
            val name = nodeObj.optString("name", "")
            val description = nodeObj.optString("description", "")
            
            // 解析位置
            val positionObj = nodeObj.optJSONObject("position")
            val position = if (positionObj != null) {
                NodePosition(
                    x = positionObj.optDouble("x", 0.0).toFloat(),
                    y = positionObj.optDouble("y", 0.0).toFloat()
                )
            } else {
                NodePosition(0f, 0f)
            }

            when (type) {
                "trigger" -> {
                    val triggerType = nodeObj.optString("triggerType", "manual")
                    val triggerConfigObj = nodeObj.optJSONObject("triggerConfig")
                    val triggerConfig = if (triggerConfigObj != null) {
                        jsonObjectToStringMap(triggerConfigObj)
                    } else {
                        emptyMap()
                    }

                    TriggerNode(
                        id = id,
                        name = name.ifBlank { "Trigger" },
                        description = description,
                        position = position,
                        triggerType = triggerType,
                        triggerConfig = triggerConfig
                    )
                }
                "execute" -> {
                    val actionType = nodeObj.optString("actionType", "")
                    val actionConfigObj = nodeObj.optJSONObject("actionConfig")
                    val actionConfig = if (actionConfigObj != null) {
                        jsonObjectToParameterValueMap(actionConfigObj)
                    } else {
                        emptyMap()
                    }
                    val jsCode = nodeObj.optString("jsCode", null)

                    ExecuteNode(
                        id = id,
                        name = name.ifBlank { "Action" },
                        description = description,
                        position = position,
                        actionType = actionType,
                        actionConfig = actionConfig,
                        jsCode = jsCode
                    )
                }
                "condition" -> {
                    val operatorRaw = nodeObj.optString("operator", "EQ")
                    val operator = try {
                        ConditionOperator.valueOf(operatorRaw.trim().uppercase())
                    } catch (_: Exception) {
                        ConditionOperator.EQ
                    }

                    val left = parseParameterValue(nodeObj.opt("left"))
                    val right = parseParameterValue(nodeObj.opt("right"))

                    ConditionNode(
                        id = id,
                        name = name.ifBlank { "Condition" },
                        description = description,
                        position = position,
                        left = left,
                        operator = operator,
                        right = right
                    )
                }
                "logic" -> {
                    val operatorRaw = nodeObj.optString("operator", nodeObj.optString("operatorLogic", "AND"))
                    val operator = try {
                        LogicOperator.valueOf(operatorRaw.trim().uppercase())
                    } catch (_: Exception) {
                        LogicOperator.AND
                    }

                    LogicNode(
                        id = id,
                        name = name.ifBlank { "Logic" },
                        description = description,
                        position = position,
                        operator = operator
                    )
                }
                "extract" -> {
                    val modeRaw = nodeObj.optString("mode", "REGEX")
                    val mode = try {
                        ExtractMode.valueOf(modeRaw.trim().uppercase())
                    } catch (_: Exception) {
                        ExtractMode.REGEX
                    }

                    val expression = nodeObj.optString("expression", nodeObj.optString("pattern", nodeObj.optString("path", "")))
                    val group = nodeObj.optInt("group", 0)
                    val defaultValue = nodeObj.optString("defaultValue", "")
                    val source = parseParameterValue(nodeObj.opt("source"))

                    val others = parseParameterValueList(nodeObj.opt("others"))
                    val startIndex = nodeObj.optInt("startIndex", 0)
                    val length = nodeObj.optInt("length", -1)
                    val randomMin = nodeObj.optInt("randomMin", 0)
                    val randomMax = nodeObj.optInt("randomMax", 100)
                    val randomStringLength = nodeObj.optInt("randomStringLength", 8)
                    val randomStringCharset = nodeObj.optString(
                        "randomStringCharset",
                        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
                    )

                    val useFixed = nodeObj.optBoolean("useFixed", false)
                    val fixedValue = nodeObj.optString("fixedValue", "")

                    ExtractNode(
                        id = id,
                        name = name.ifBlank { "Extract" },
                        description = description,
                        position = position,
                        source = source,
                        mode = mode,
                        expression = expression,
                        group = group,
                        defaultValue = defaultValue,
                        others = others,
                        startIndex = startIndex,
                        length = length,
                        randomMin = randomMin,
                        randomMax = randomMax,
                        randomStringLength = randomStringLength,
                        randomStringCharset = randomStringCharset,
                        useFixed = useFixed,
                        fixedValue = fixedValue
                    )
                }
                else -> {
                    AppLogger.w(TAG, "Unknown node type: $type")
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse node", e)
            null
        }
    }

    private fun inferNodeType(nodeObj: JSONObject): String {
        val discriminator = nodeObj.optString("__type", "").trim()
        if (discriminator.isNotBlank()) {
            val simple = discriminator.substringAfterLast('.').lowercase()
            when {
                simple.endsWith("triggernode") -> return "trigger"
                simple.endsWith("executenode") -> return "execute"
                simple.endsWith("conditionnode") -> return "condition"
                simple.endsWith("logicnode") -> return "logic"
                simple.endsWith("extractnode") -> return "extract"
            }
        }

        if (nodeObj.has("triggerType") || nodeObj.has("triggerConfig")) return "trigger"
        if (nodeObj.has("actionType") || nodeObj.has("actionConfig") || nodeObj.has("jsCode")) return "execute"
        if (nodeObj.has("left") || nodeObj.has("right")) return "condition"
        if (nodeObj.has("source") || nodeObj.has("mode") || nodeObj.has("expression") || nodeObj.has("pattern") || nodeObj.has("path")) return "extract"
        if (nodeObj.has("operator")) return "logic"

        return ""
    }

    /**
     * 解析连接JSON字符串
     */
    private fun parseConnections(connectionsJson: String, nodes: List<WorkflowNode>): List<WorkflowNodeConnection> {
        return try {
            val jsonArray = JSONArray(connectionsJson)
            val connections = mutableListOf<WorkflowNodeConnection>()
            val nodeIdList = nodes.map { it.id }
            val nodeIdSet = nodeIdList.toSet()
            val nodeNameToIds = nodes.groupBy { it.name.trim() }.mapValues { (_, v) -> v.map { it.id } }

            for (i in 0 until jsonArray.length()) {
                val connObj = jsonArray.getJSONObject(i)
                val connection = parseConnection(connObj, nodeIdList, nodeIdSet, nodeNameToIds)
                if (connection != null && connection.sourceNodeId != connection.targetNodeId) {
                    connections.add(connection)
                }
            }

            if (jsonArray.length() > 0 && connections.isEmpty()) {
                throw IllegalArgumentException("Failed to parse connections: Please check source/target fields (sourceNodeId/targetNodeId recommended) and node IDs existence")
            }

            connections
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse connections JSON", e)
            throw IllegalArgumentException("Invalid connection JSON format: ${e.message}")
        }
    }

    /**
     * 解析单个连接
     */
    private fun parseConnection(
        connObj: JSONObject,
        nodeIdList: List<String>,
        nodeIdSet: Set<String>,
        nodeNameToIds: Map<String, List<String>>
    ): WorkflowNodeConnection? {
        return try {
            val id = connObj.optString("id", UUID.randomUUID().toString())
            val sourceNodeId = resolveNodeId(connObj, true, nodeIdList, nodeIdSet, nodeNameToIds)
            val targetNodeId = resolveNodeId(connObj, false, nodeIdList, nodeIdSet, nodeNameToIds)
            val condition = connObj.optString("condition", null)

            if (sourceNodeId.isBlank() || targetNodeId.isBlank()) {
                AppLogger.w(TAG, "Connection missing source or target node ID")
                return null
            }

            if (!nodeIdSet.contains(sourceNodeId) || !nodeIdSet.contains(targetNodeId)) {
                AppLogger.w(TAG, "Connection references unknown node: $sourceNodeId -> $targetNodeId")
                return null
            }

            WorkflowNodeConnection(
                id = id,
                sourceNodeId = sourceNodeId,
                targetNodeId = targetNodeId,
                condition = condition
            )
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to parse connection", e)
            null
        }
    }

    private fun resolveNodeId(
        connObj: JSONObject,
        isSource: Boolean,
        nodeIdList: List<String>,
        nodeIdSet: Set<String>,
        nodeNameToIds: Map<String, List<String>>
    ): String {
        val idKeys = if (isSource) {
            listOf("sourceNodeId", "sourceId", "source", "from")
        } else {
            listOf("targetNodeId", "targetId", "target", "to")
        }
        for (k in idKeys) {
            val v = connObj.optString(k, "").trim()
            if (v.isNotBlank() && nodeIdSet.contains(v)) return v
            // Some LLMs put indices in id fields, e.g. "sourceNodeId": 0 or "0"
            val idxFromIdField = v.toIntOrNull()
            if (idxFromIdField != null) {
                val idByIndex = nodeIdList.getOrNull(idxFromIdField)
                if (idByIndex != null) return idByIndex
            }
        }

        val indexKeys = if (isSource) {
            listOf("sourceIndex", "sourceNodeIndex", "fromIndex", "from_node_index")
        } else {
            listOf("targetIndex", "targetNodeIndex", "toIndex", "to_node_index")
        }
        for (k in indexKeys) {
            if (!connObj.has(k)) continue
            val idx = when (val raw = connObj.get(k)) {
                is Number -> raw.toInt()
                is String -> raw.trim().toIntOrNull()
                else -> null
            }
            if (idx != null) {
                val idByIndex = nodeIdList.getOrNull(idx)
                if (idByIndex != null) return idByIndex
            }
        }

        val nameKeys = if (isSource) {
            listOf("sourceNodeName", "sourceName", "fromName", "from_node_name")
        } else {
            listOf("targetNodeName", "targetName", "toName", "to_node_name")
        }
        for (k in nameKeys) {
            val name = connObj.optString(k, "").trim()
            if (name.isBlank()) continue
            val ids = nodeNameToIds[name]
            if (ids != null && ids.size == 1) return ids.first()
            if (ids != null && ids.isNotEmpty()) return ids.first()
        }

        for (k in idKeys) {
            val nameOrId = connObj.optString(k, "").trim()
            if (nameOrId.isBlank()) continue
            val ids = nodeNameToIds[nameOrId]
            if (ids != null && ids.isNotEmpty()) return ids.first()
        }

        return ""
    }

    /**
     * 将JSONObject转换为Map<String, String>
     */
    private fun jsonObjectToStringMap(jsonObject: JSONObject): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = jsonObject.optString(key, "")
        }
        return map
    }

    private fun parseParameterValue(raw: Any?): ParameterValue {
        return when (raw) {
            null, JSONObject.NULL -> ParameterValue.StaticValue("")
            is JSONObject -> {
                val nodeId = raw.optString("nodeId", raw.optString("ref", raw.optString("refNodeId", ""))).trim()
                if (nodeId.isNotBlank()) {
                    ParameterValue.NodeReference(nodeId)
                } else {
                    val value = raw.optString("value", raw.toString())
                    ParameterValue.StaticValue(value)
                }
            }
            is String -> {
                val s = raw
                ParameterValue.StaticValue(s)
            }
            is Number -> ParameterValue.StaticValue(raw.toString())
            is Boolean -> ParameterValue.StaticValue(raw.toString())
            else -> ParameterValue.StaticValue(raw.toString())
        }
    }

    private fun parseParameterValueList(raw: Any?): List<ParameterValue> {
        return when (raw) {
            null, JSONObject.NULL -> emptyList()
            is JSONArray -> {
                (0 until raw.length()).map { idx ->
                    parseParameterValue(raw.opt(idx))
                }
            }
            is List<*> -> raw.map { item -> parseParameterValue(item) }
            else -> listOf(parseParameterValue(raw))
        }
    }

    /**
     * 将JSONObject转换为Map<String, ParameterValue>
     */
    private fun jsonObjectToParameterValueMap(jsonObject: JSONObject): Map<String, ParameterValue> {
        val map = mutableMapOf<String, ParameterValue>()
        val keys = jsonObject.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            map[key] = parseParameterValue(jsonObject.opt(key))
        }
        return map
    }

    // ==================== Node CRUD Tools ====================

    /**
     * 添加节点到工作流
     * workflow_id: 工作流ID
     * node_type: 节点类型 (trigger, ai, execute_shell, skill, integration, condition, logic, extract, mcp)
     * name: 节点名称
     */
    suspend fun addNode(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow ID cannot be empty"
                )
            }

            val nodeType = tool.parameters.find { it.name == "node_type" }?.value?.lowercase()
            if (nodeType.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Node type cannot be empty"
                )
            }

            val name = tool.parameters.find { it.name == "name" }?.value ?: "New $nodeType node"

            // Get existing workflow
            val existingResult = workflowRepository.getWorkflowById(workflowId)
            if (existingResult.isFailure || existingResult.getOrNull() == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow not found: $workflowId"
                )
            }

            val existingWorkflow = existingResult.getOrNull()!!
            val newNode = createNodeByType(nodeType, name)
            if (newNode == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Unknown or unsupported node type: $nodeType"
                )
            }

            val updatedNodes = existingWorkflow.nodes + newNode
            val updatedWorkflow = existingWorkflow.copy(nodes = updatedNodes)

            val result = workflowRepository.updateWorkflow(updatedWorkflow)
            if (result.isSuccess) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("{\"node_id\": \"${newNode.id}\", \"node_type\": \"$nodeType\", \"node_name\": \"${newNode.name}\"}")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to add node: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to add node", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to add node: ${e.message}"
            )
        }
    }

    /**
     * 配置节点属性
     * workflow_id: 工作流ID
     * node_id: 节点ID
     * field: 要修改的字段名
     * value: 新的值
     */
    suspend fun configureNode(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow ID cannot be empty"
                )
            }

            val nodeId = tool.parameters.find { it.name == "node_id" }?.value
            if (nodeId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Node ID cannot be empty"
                )
            }

            val field = tool.parameters.find { it.name == "field" }?.value
            if (field.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Field name cannot be empty"
                )
            }

            val value = tool.parameters.find { it.name == "value" }?.value ?: ""

            // Get existing workflow
            val existingResult = workflowRepository.getWorkflowById(workflowId)
            if (existingResult.isFailure || existingResult.getOrNull() == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow not found: $workflowId"
                )
            }

            val existingWorkflow = existingResult.getOrNull()!!
            val nodeIndex = existingWorkflow.nodes.indexOfFirst { it.id == nodeId }
            if (nodeIndex < 0) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Node not found: $nodeId"
                )
            }

            val updatedNode = updateNodeField(existingWorkflow.nodes[nodeIndex], field, value)
            if (updatedNode == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to update field '$field': invalid field name or value"
                )
            }

            val updatedNodes = existingWorkflow.nodes.toMutableList()
            updatedNodes[nodeIndex] = updatedNode
            val updatedWorkflow = existingWorkflow.copy(nodes = updatedNodes)

            val result = workflowRepository.updateWorkflow(updatedWorkflow)
            if (result.isSuccess) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("{\"node_id\": \"$nodeId\", \"field\": \"$field\", \"value\": \"$value\", \"updated\": true}")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to configure node: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to configure node", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to configure node: ${e.message}"
            )
        }
    }

    /**
     * 连接两个节点
     * workflow_id: 工作流ID
     * source_node_id: 源节点ID
     * target_node_id: 目标节点ID
     * condition: 连接条件（可选）
     */
    suspend fun connectNodes(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow ID cannot be empty"
                )
            }

            val sourceNodeId = tool.parameters.find { it.name == "source_node_id" }?.value
            if (sourceNodeId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source node ID cannot be empty"
                )
            }

            val targetNodeId = tool.parameters.find { it.name == "target_node_id" }?.value
            if (targetNodeId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Target node ID cannot be empty"
                )
            }

            val condition = tool.parameters.find { it.name == "condition" }?.value

            // Get existing workflow
            val existingResult = workflowRepository.getWorkflowById(workflowId)
            if (existingResult.isFailure || existingResult.getOrNull() == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow not found: $workflowId"
                )
            }

            val existingWorkflow = existingResult.getOrNull()!!

            val sourceNode = existingWorkflow.nodes.find { it.id == sourceNodeId }
            if (sourceNode == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Source node not found: $sourceNodeId"
                )
            }

            val targetNode = existingWorkflow.nodes.find { it.id == targetNodeId }
            if (targetNode == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Target node not found: $targetNodeId"
                )
            }

            val connection = WorkflowNodeConnection(
                id = UUID.randomUUID().toString(),
                sourceNodeId = sourceNodeId,
                targetNodeId = targetNodeId,
                condition = condition
            )

            val updatedConnections = existingWorkflow.connections + connection
            val updatedWorkflow = existingWorkflow.copy(connections = updatedConnections)

            val result = workflowRepository.updateWorkflow(updatedWorkflow)
            if (result.isSuccess) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("{\"connection_id\": \"${connection.id}\", \"source\": \"$sourceNodeId\", \"target\": \"$targetNodeId\"}")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to connect nodes: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to connect nodes", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to connect nodes: ${e.message}"
            )
        }
    }

    /**
     * 删除节点
     * workflow_id: 工作流ID
     * node_id: 节点ID
     */
    suspend fun deleteNode(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow ID cannot be empty"
                )
            }

            val nodeId = tool.parameters.find { it.name == "node_id" }?.value
            if (nodeId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Node ID cannot be empty"
                )
            }

            // Get existing workflow
            val existingResult = workflowRepository.getWorkflowById(workflowId)
            if (existingResult.isFailure || existingResult.getOrNull() == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow not found: $workflowId"
                )
            }

            val existingWorkflow = existingResult.getOrNull()!!

            val nodeToDelete = existingWorkflow.nodes.find { it.id == nodeId }
            if (nodeToDelete == null) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Node not found: $nodeId"
                )
            }

            // Remove node and all connections referencing this node
            val updatedNodes = existingWorkflow.nodes.filter { it.id != nodeId }
            val updatedConnections = existingWorkflow.connections.filter {
                it.sourceNodeId != nodeId && it.targetNodeId != nodeId
            }

            val updatedWorkflow = existingWorkflow.copy(
                nodes = updatedNodes,
                connections = updatedConnections
            )

            val result = workflowRepository.updateWorkflow(updatedWorkflow)
            if (result.isSuccess) {
                ToolResult(
                    toolName = tool.name,
                    success = true,
                    result = StringResultData("{\"node_id\": \"$nodeId\", \"deleted\": true, \"connections_removed\": ${existingWorkflow.connections.size - updatedConnections.size}}")
                )
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to delete node: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to delete node", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to delete node: ${e.message}"
            )
        }
    }

    /**
     * 获取工作流详细信息（JSON格式）
     * workflow_id: 工作流ID
     */
    suspend fun getWorkflowDetail(tool: AITool): ToolResult {
        return try {
            val workflowId = tool.parameters.find { it.name == "workflow_id" }?.value
            if (workflowId.isNullOrBlank()) {
                return ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Workflow ID cannot be empty"
                )
            }

            val result = workflowRepository.getWorkflowById(workflowId)
            if (result.isSuccess) {
                val workflow = result.getOrNull()
                if (workflow == null) {
                    ToolResult(
                        toolName = tool.name,
                        success = false,
                        result = StringResultData(""),
                        error = "Workflow not found: $workflowId"
                    )
                } else {
                    // Build detailed JSON response
                    val nodesJson = workflow.nodes.joinToString(",\n") { node ->
                        buildNodeJson(node)
                    }
                    val connectionsJson = workflow.connections.joinToString(",\n") { conn ->
                        """{"id": "${conn.id}", "source": "${conn.sourceNodeId}", "target": "${conn.targetNodeId}", "condition": ${conn.condition?.let { "\"$it\"" } ?: "null"}}"""
                    }
                    val fullJson = """
                    {
                        "id": "${workflow.id}",
                        "name": "${escapeJson(workflow.name)}",
                        "description": "${escapeJson(workflow.description)}",
                        "enabled": ${workflow.enabled},
                        "node_count": ${workflow.nodes.size},
                        "connection_count": ${workflow.connections.size},
                        "nodes": [
                            $nodesJson
                        ],
                        "connections": [
                            $connectionsJson
                        ],
                        "stats": {
                            "total_executions": ${workflow.totalExecutions},
                            "successful_executions": ${workflow.successfulExecutions},
                            "failed_executions": ${workflow.failedExecutions},
                            "last_execution_time": ${workflow.lastExecutionTime ?: "null"},
                            "last_execution_status": ${workflow.lastExecutionStatus?.name?.let { "\"$it\"" } ?: "null"}
                        }
                    }
                    """.trimIndent()

                    ToolResult(
                        toolName = tool.name,
                        success = true,
                        result = StringResultData(fullJson)
                    )
                }
            } else {
                ToolResult(
                    toolName = tool.name,
                    success = false,
                    result = StringResultData(""),
                    error = "Failed to get workflow: ${result.exceptionOrNull()?.message}"
                )
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to get workflow detail", e)
            ToolResult(
                toolName = tool.name,
                success = false,
                result = StringResultData(""),
                error = "Failed to get workflow detail: ${e.message}"
            )
        }
    }

    // ==================== Helper Methods ====================

    /**
     * Create a new node by type
     */
    private fun createNodeByType(nodeType: String, name: String): WorkflowNode? {
        val id = UUID.randomUUID().toString()
        val position = NodePosition(0f, 0f)

        return when (nodeType) {
            "trigger" -> TriggerNode(
                id = id,
                name = name,
                position = position,
                triggerType = "manual",
                triggerConfig = emptyMap()
            )
            "ai" -> AINode(
                id = id,
                name = name,
                position = position,
                taskType = "generate_text",
                prompt = ""
            )
            "execute_shell" -> ExecuteShellNode(
                id = id,
                name = name,
                position = position,
                command = ""
            )
            "skill" -> SkillNode(
                id = id,
                name = name,
                position = position,
                skillNames = emptyList()
            )
            "integration" -> IntegrationNode(
                id = id,
                name = name,
                position = position,
                integrationType = "tool",
                toolkit = ""
            )
            "condition" -> ConditionNode(
                id = id,
                name = name,
                position = position,
                left = ParameterValue.StaticValue(""),
                operator = ConditionOperator.EQ,
                right = ParameterValue.StaticValue("")
            )
            "logic" -> LogicNode(
                id = id,
                name = name,
                position = position,
                operator = LogicOperator.AND
            )
            "extract" -> ExtractNode(
                id = id,
                name = name,
                position = position,
                source = ParameterValue.StaticValue(""),
                mode = ExtractMode.REGEX,
                expression = ""
            )
            "mcp" -> MCPNode(
                id = id,
                name = name,
                position = position,
                serverName = "",
                toolName = ""
            )
            else -> null
        }
    }

    /**
     * Update a field on a node (using reflection for dynamic field access)
     */
    private fun updateNodeField(node: WorkflowNode, field: String, value: String): WorkflowNode? {
        return try {
            when (node) {
                is TriggerNode -> updateTriggerNode(node, field, value)
                is AINode -> updateAINode(node, field, value)
                is ExecuteShellNode -> updateExecuteShellNode(node, field, value)
                is SkillNode -> updateSkillNode(node, field, value)
                is IntegrationNode -> updateIntegrationNode(node, field, value)
                is ConditionNode -> updateConditionNode(node, field, value)
                is LogicNode -> updateLogicNode(node, field, value)
                is ExtractNode -> updateExtractNode(node, field, value)
                is MCPNode -> updateMCPNode(node, field, value)
                is ExecuteNode -> updateExecuteNode(node, field, value)
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to update node field", e)
            null
        }
    }

    private fun updateTriggerNode(node: TriggerNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "triggertype", "trigger_type" -> node.copy(triggerType = value)
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateAINode(node: AINode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "tasktype", "task_type" -> node.copy(taskType = value)
            "modelid", "model_id" -> node.copy(modelId = value)
            "systemprompt", "system_prompt" -> node.copy(systemPrompt = value)
            "prompt" -> node.copy(prompt = value)
            "enabletools", "enable_tools" -> node.copy(enableTools = value.toBoolean())
            "enabledtools", "enabled_tools" -> node.copy(enabledTools = value.split(",").map { it.trim() })
            "maxtokens", "max_tokens" -> node.copy(maxTokens = value.toIntOrNull() ?: node.maxTokens)
            "temperature" -> node.copy(temperature = value.toFloatOrNull() ?: node.temperature)
            "timeout", "timeoutms", "timeout_ms" -> node.copy(timeoutMs = value.toLongOrNull() ?: node.timeoutMs)
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateExecuteShellNode(node: ExecuteShellNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "command" -> node.copy(command = value)
            "sessionid", "session_id" -> node.copy(sessionId = value)
            "timeout", "timeoutms", "timeout_ms" -> node.copy(timeoutMs = value.toLongOrNull() ?: node.timeoutMs)
            "capturestderr", "capture_stderr" -> node.copy(captureStderr = value.toBoolean())
            "workingdir", "working_dir" -> node.copy(workingDir = value)
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateSkillNode(node: SkillNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "skillnames", "skill_names" -> node.copy(skillNames = value.split(",").map { it.trim() })
            "extrainstructions", "extra_instructions" -> node.copy(extraInstructions = value)
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateIntegrationNode(node: IntegrationNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "integrationtype", "integration_type" -> node.copy(integrationType = value)
            "toolkit" -> node.copy(toolkit = value)
            "actionid", "action_id" -> node.copy(actionId = value)
            "accountid", "account_id" -> node.copy(accountId = value.ifBlank { null })
            "timeout" -> node.copy(timeout = value.toLongOrNull()?.times(1000) ?: node.timeout)
            "enabled" -> node.copy(enabled = value.toBoolean())
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateConditionNode(node: ConditionNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "left" -> node.copy(left = ParameterValue.StaticValue(value))
            "operator" -> node.copy(operator = try { ConditionOperator.valueOf(value.uppercase()) } catch (e: Exception) { node.operator })
            "right" -> node.copy(right = ParameterValue.StaticValue(value))
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateLogicNode(node: LogicNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "operator" -> node.copy(operator = try { LogicOperator.valueOf(value.uppercase()) } catch (e: Exception) { node.operator })
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateExtractNode(node: ExtractNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "source" -> node.copy(source = ParameterValue.StaticValue(value))
            "mode" -> node.copy(mode = try { ExtractMode.valueOf(value.uppercase()) } catch (e: Exception) { node.mode })
            "expression" -> node.copy(expression = value)
            "group" -> node.copy(group = value.toIntOrNull() ?: node.group)
            "defaultvalue", "default_value" -> node.copy(defaultValue = value)
            "startindex", "start_index" -> node.copy(startIndex = value.toIntOrNull() ?: node.startIndex)
            "length" -> node.copy(length = value.toIntOrNull() ?: node.length)
            "usefixed", "use_fixed" -> node.copy(useFixed = value.toBoolean())
            "fixedvalue", "fixed_value" -> node.copy(fixedValue = value)
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateMCPNode(node: MCPNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "servername", "server_name" -> node.copy(serverName = value)
            "toolname", "tool_name" -> node.copy(toolName = value)
            "tooldescription", "tool_description" -> node.copy(toolDescription = value)
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    private fun updateExecuteNode(node: ExecuteNode, field: String, value: String): WorkflowNode {
        return when (field.lowercase()) {
            "name" -> node.copy(name = value)
            "description" -> node.copy(description = value)
            "actiontype", "action_type" -> node.copy(actionType = value)
            "jscode", "js_code" -> node.copy(jsCode = value.ifBlank { null })
            else -> throw IllegalArgumentException("Unknown field: $field")
        }
    }

    /**
     * Build JSON representation of a node
     */
    private fun buildNodeJson(node: WorkflowNode): String {
        val baseFields = "\"id\": \"${node.id}\", \"type\": \"${node.type}\", \"name\": \"${escapeJson(node.name)}\", \"description\": \"${escapeJson(node.description)}\""
        return when (node) {
            is TriggerNode -> "{$baseFields, \"triggerType\": \"${node.triggerType}\", \"triggerConfig\": ${mapToJson(node.triggerConfig)}}"
            is AINode -> "{$baseFields, \"taskType\": \"${node.taskType}\", \"modelId\": \"${escapeJson(node.modelId)}\", \"prompt\": \"${escapeJson(node.prompt)}\", \"enableTools\": ${node.enableTools}}"
            is ExecuteShellNode -> "{$baseFields, \"command\": \"${escapeJson(node.command)}\", \"timeoutMs\": ${node.timeoutMs}}"
            is SkillNode -> "{$baseFields, \"skillNames\": ${node.skillNames.map { "\"$it\"" }.joinToString(",", "[", "]")}}"
            is IntegrationNode -> "{$baseFields, \"integrationType\": \"${node.integrationType}\", \"toolkit\": \"${escapeJson(node.toolkit)}\", \"actionId\": \"${escapeJson(node.actionId)}\"}"
            is ConditionNode -> "{$baseFields, \"operator\": \"${node.operator.name}\", \"left\": \"${node.left}\", \"right\": \"${node.right}\"}"
            is LogicNode -> "{$baseFields, \"operator\": \"${node.operator.name}\"}"
            is ExtractNode -> "{$baseFields, \"mode\": \"${node.mode.name}\", \"expression\": \"${escapeJson(node.expression)}\"}"
            is MCPNode -> "{$baseFields, \"serverName\": \"${escapeJson(node.serverName)}\", \"toolName\": \"${escapeJson(node.toolName)}\"}"
            is ExecuteNode -> "{$baseFields, \"actionType\": \"${node.actionType}\"}"
        }
    }

    private fun mapToJson(map: Map<String, String>): String {
        if (map.isEmpty()) return "{}"
        val entries = map.entries.joinToString(",") { "\"${it.key}\": \"${escapeJson(it.value)}\"" }
        return "{$entries}"
    }

    private fun escapeJson(s: String): String {
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}

