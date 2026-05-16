package com.ai.assistance.operit.data.model

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * MCP工具信息
 * 用于在MCP节点中存储可用的工具信息
 */
@Serializable
data class MCPToolInfo(
    val name: String,
    val description: String = "",
    val inputSchema: Map<String, Map<String, String>> = emptyMap()
)

/**
 * 工作流模型
 * 代表一个完整的自动化工作流程
 */
@Serializable
data class Workflow(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var description: String = "",
    var nodes: List<WorkflowNode> = emptyList(),
    var connections: List<WorkflowNodeConnection> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    var updatedAt: Long = System.currentTimeMillis(),
    var enabled: Boolean = true,
    // 执行统计信息
    var lastExecutionTime: Long? = null,  // 上次执行时间
    var lastExecutionStatus: ExecutionStatus? = null,  // 上次执行状态
    var totalExecutions: Int = 0,  // 总执行次数
    var successfulExecutions: Int = 0,  // 成功执行次数
    var failedExecutions: Int = 0  // 失败执行次数
)

/**
 * 执行状态枚举
 */
@Serializable
enum class ExecutionStatus {
    SUCCESS,  // 成功
    FAILED,   // 失败
    RUNNING   // 运行中
}

/**
 * 工作流节点基类
 */
@Serializable
sealed class WorkflowNode {
    abstract val id: String
    abstract val type: String
    abstract var name: String
    abstract var description: String
    abstract var position: NodePosition
}

/**
 * 触发节点
 * 定义工作流的触发条件（暂时为占位符）
 */
@Serializable
data class TriggerNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "trigger",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var triggerType: String = "manual", // manual, schedule, event
    var triggerConfig: Map<String, String> = emptyMap() // 触发器配置参数
) : WorkflowNode()

/**
 * 执行节点
 * 定义工作流的执行动作
 */
@Serializable
data class ExecuteNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "execute",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var actionType: String = "", // 工具名称，如 "http_request", "list_files", "click_element"
    var actionConfig: Map<String, ParameterValue> = emptyMap(), // 工具参数：支持静态值或节点引用
    var jsCode: String? = null // 可选：直接执行 JavaScript 代码
) : WorkflowNode()

@Serializable
enum class ConditionOperator {
    EQ,
    NE,
    GT,
    GTE,
    LT,
    LTE,
    CONTAINS,
    NOT_CONTAINS,
    IN,
    NOT_IN
}

@Serializable
data class ConditionNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "condition",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var left: ParameterValue = ParameterValue.StaticValue(""),
    var operator: ConditionOperator = ConditionOperator.EQ,
    var right: ParameterValue = ParameterValue.StaticValue("")
) : WorkflowNode()

@Serializable
enum class LogicOperator {
    AND,
    OR
}

@Serializable
data class LogicNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "logic",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var operator: LogicOperator = LogicOperator.AND
) : WorkflowNode()

@Serializable
enum class ExtractMode {
    REGEX,
    JSON,
    SUB,
    CONCAT,
    RANDOM_INT,
    RANDOM_STRING
}

@Serializable
data class ExtractNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "extract",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var source: ParameterValue = ParameterValue.StaticValue(""),
    var mode: ExtractMode = ExtractMode.REGEX,
    var expression: String = "",
    var group: Int = 0,
    var defaultValue: String = "",
    var others: List<ParameterValue> = emptyList(),
    var startIndex: Int = 0,
    var length: Int = -1,
    var randomMin: Int = 0,
    var randomMax: Int = 100,
    var randomStringLength: Int = 8,
    var randomStringCharset: String = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
    var useFixed: Boolean = false,
    var fixedValue: String = ""
) : WorkflowNode()

/**
 * MCP服务器节点
 * 允许在工作流中调用已配置的MCP服务器的工具
 */
@Serializable
data class MCPNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "mcp",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    var serverName: String = "", // MCP服务器名称
    var toolName: String = "", // 要调用的工具名称
    var toolDescription: String = "", // 工具描述（可选，用于显示）
    var parameters: Map<String, ParameterValue> = emptyMap() // 工具参数：支持静态值或节点引用
) : WorkflowNode()

/**
 * 集成节点类型常量
 */
object IntegrationNodeConstants {
    const val TYPE_INTEGRATION = "integration"
    
    // Integration node type values (matching IntegrationNodeType)
    const val TYPE_TOOL = "tool"
    const val TYPE_WEBHOOK = "webhook"
    const val TYPE_MCP = "mcp"
    const val TYPE_OAUTH = "oauth"
}

/**
 * 集成节点
 * 允许在工作流中调用外部集成（Composio工具、Webhooks、MCP服务器、OAuth）
 * 
 * 支持的集成类型：
 * - TOOL: Composio工具执行
 * - WEBHOOK: 自定义Webhook HTTP调用
 * - MCP: MCP服务器工具执行
 * - OAUTH: OAuth连接管理
 */
@Serializable
data class IntegrationNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = IntegrationNodeConstants.TYPE_INTEGRATION,
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    // Integration type (tool, webhook, mcp, oauth)
    var integrationType: String = IntegrationNodeConstants.TYPE_TOOL,
    // Toolkit name (for tool-based integrations like Composio)
    var toolkit: String = "",
    // Action/tool to execute
    var actionId: String = "",
    // Parameters for the action (supports static values or node references)
    var parameters: Map<String, ParameterValue> = emptyMap(),
    // Connected account ID to use (if required)
    var accountId: String? = null,
    // Custom webhook configuration (if integrationType is WEBHOOK)
    var webhookConfig: IntegrationWebhookConfig? = null,
    // MCP server configuration (if integrationType is MCP)
    var mcpServerConfig: IntegrationMcpServerConfig? = null,
    // Error handling settings
    var errorHandling: IntegrationErrorHandlingConfig = IntegrationErrorHandlingConfig(),
    // Retry settings
    var retryConfig: IntegrationRetryConfig = IntegrationRetryConfig(),
    // Execution timeout in milliseconds
    var timeout: Long = 30000L,
    // Whether the node is enabled
    var enabled: Boolean = true
) : WorkflowNode()

/**
 * Integration webhook configuration
 */
@Serializable
data class IntegrationWebhookConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val url: String = "",
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val apiKeyRequired: Boolean = false
)

/**
 * Integration MCP server configuration
 */
@Serializable
data class IntegrationMcpServerConfig(
    val serverName: String = "",
    val serverId: String? = null,
    val toolName: String = "",
    val parameters: Map<String, String> = emptyMap()
)

/**
 * Integration error handling configuration
 */
@Serializable
data class IntegrationErrorHandlingConfig(
    val onError: String = "stop", // stop, continue, retry, fallback
    val errorMessage: String = "",
    val fallbackValue: String? = null,
    val continueOnError: Boolean = false
)

/**
 * Integration retry configuration
 */
@Serializable
data class IntegrationRetryConfig(
    val enabled: Boolean = false,
    val maxRetries: Int = 3,
    val retryDelay: Long = 1000L,
    val exponentialBackoff: Boolean = true,
    val retryableErrors: List<String> = emptyList()
)

/**
 * 参数值类型
 * 支持静态值或引用其他节点的输出
 */
@Serializable
sealed class ParameterValue {
    @Serializable
    data class StaticValue(val value: String) : ParameterValue()

    @Serializable
    data class NodeReference(val nodeId: String) : ParameterValue()

    @Serializable
    data class TriggerExtra(val key: String, val defaultValue: String? = null) : ParameterValue()
}

/**
 * 节点位置信息（用于将来的可视化编辑器）
 */
@Serializable
data class NodePosition(
    var x: Float = 0f,
    var y: Float = 0f
)

/**
 * AI节点
 * 在工作流中调用AI服务（复用AI Chat的EnhancedAIService配置）
 * 支持：文本生成、图像分析、分类、嵌入等
 */
@Serializable
data class AINode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "ai",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    // AI任务类型
    var taskType: String = "generate_text",  // generate_text, analyze_image, classify, embed, reasoning
    // 模型选择（空=使用默认模型）
    var modelId: String = "",
    // 系统提示词模板
    var systemPrompt: String = "",
    // 用户提示词（支持节点引用）
    var prompt: String = "",
    // 启用工具调用（让AI在AI节点内调用工具）
    var enableTools: Boolean = false,
    // 工具列表（当enableTools=true时）
    var enabledTools: List<String> = emptyList(),
    // 最大token
    var maxTokens: Int = 4096,
    // 温度
    var temperature: Float = 0.7f,
    // 输出是否流式（工作流中建议false）
    var stream: Boolean = false,
    // 超时时间（毫秒）
    var timeoutMs: Long = 60000L,
    // 输入文件列表（节点引用或文件路径，如 "{node_id.output}" 或 "/storage/..."）
    var inputFiles: List<String> = emptyList()
) : WorkflowNode()

/**
 * 执行Shell节点
 * 在工作流中执行Ubuntu shell命令（复用Terminal页面的终端会话）
 */
@Serializable
data class ExecuteShellNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "execute_shell",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    // 要执行的命令（支持节点引用）
    var command: String = "",
    // 指定会话ID（空=自动创建临时会话）
    var sessionId: String = "",
    // 超时时间（毫秒）
    var timeoutMs: Long = 30000L,
    // 是否捕获stderr
    var captureStderr: Boolean = true,
    // 工作目录（可选）
    var workingDir: String = ""
) : WorkflowNode()

/**
 * Skill节点
 * 将已安装的SKILL.md内容注入到后续AI节点的上下文中
 * 不执行任何逻辑，只存储skill引用
 */
@Serializable
data class SkillNode(
    override val id: String = UUID.randomUUID().toString(),
    override val type: String = "skill",
    override var name: String = "",
    override var description: String = "",
    override var position: NodePosition = NodePosition(0f, 0f),
    // 要加载的skill名称（与SKILL.md文件名对应，如 "android-development"）
    var skillNames: List<String> = emptyList(),
    // 额外的指令（追加到skill内容后）
    var extraInstructions: String = ""
) : WorkflowNode()

/**
 * 工作流节点连接
 * 定义节点之间的连接关系
 */
@Serializable
data class WorkflowNodeConnection(
    val id: String = UUID.randomUUID().toString(),
    val sourceNodeId: String,
    val targetNodeId: String,
    var condition: String? = null // 连接条件（可选）
)

