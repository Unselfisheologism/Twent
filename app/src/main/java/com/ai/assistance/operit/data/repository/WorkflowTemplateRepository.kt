package com.ai.assistance.operit.data.repository

import android.content.Context
import com.ai.assistance.operit.data.model.Workflow
import com.ai.assistance.operit.data.model.WorkflowTemplate
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Repository for managing workflow templates
 * Loads templates from assets/workflows folder
 */
class WorkflowTemplateRepository(private val context: Context) {

    companion object {
        private const val TAG = "WorkflowTemplateRepo"
        private const val TEMPLATES_FOLDER = "workflows"
        
        @Volatile
        private var INSTANCE: WorkflowTemplateRepository? = null

        fun getInstance(context: Context): WorkflowTemplateRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WorkflowTemplateRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Get all available workflow templates from assets
     */
    suspend fun getAllTemplates(): Result<List<WorkflowTemplate>> = withContext(Dispatchers.IO) {
        try {
            val templates = mutableListOf<WorkflowTemplate>()
            val assets = context.assets
            
            // List files in the workflows folder
            try {
                val files = assets.list(TEMPLATES_FOLDER)
                files?.forEach { fileName ->
                    if (fileName.endsWith(".json")) {
                        try {
                            val template = loadTemplateFromAssets("$TEMPLATES_FOLDER/$fileName")
                            template?.let { templates.add(it) }
                        } catch (e: Exception) {
                            AppLogger.w(TAG, "Failed to load template $fileName: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                AppLogger.w(TAG, "Failed to list templates folder: ${e.message}")
            }
            
            Result.success(templates)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to load templates", e)
            Result.failure(e)
        }
    }

    /**
     * Get templates by category
     */
    suspend fun getTemplatesByCategory(category: String): Result<List<WorkflowTemplate>> = withContext(Dispatchers.IO) {
        val allTemplates = getAllTemplates()
        allTemplates.map { templates ->
            templates.filter { it.category == category }
        }
    }

    /**
     * Get template by ID
     */
    suspend fun getTemplateById(templateId: String): Result<WorkflowTemplate?> = withContext(Dispatchers.IO) {
        val allTemplates = getAllTemplates()
        allTemplates.map { templates ->
            templates.find { it.templateId == templateId }
        }
    }

    /**
     * Load a single template from assets
     */
    private fun loadTemplateFromAssets(path: String): WorkflowTemplate? {
        return try {
            val jsonString = context.assets.open(path).bufferedReader().use { it.readText() }
            json.decodeFromString<WorkflowTemplate>(jsonString)
        } catch (e: Exception) {
            AppLogger.w(TAG, "Error loading template from $path: ${e.message}")
            null
        }
    }

    /**
     * Create a workflow from a template
     * Generates new IDs for the workflow and its nodes
     */
    fun createWorkflowFromTemplate(template: WorkflowTemplate, customName: String? = null): Workflow {
        val workflow = template.workflow
        
        // Generate new IDs for the workflow
        val newWorkflowId = UUID.randomUUID().toString()
        
        // Create a mapping of old node IDs to new node IDs
        val nodeIdMapping = mutableMapOf<String, String>()
        val newNodes = workflow.nodes.map { node ->
            val newNodeId = UUID.randomUUID().toString()
            nodeIdMapping[node.id] = newNodeId
            when (node) {
                is com.ai.assistance.operit.data.model.TriggerNode -> 
                    node.copy(id = newNodeId)
                is com.ai.assistance.operit.data.model.ExecuteNode -> 
                    node.copy(id = newNodeId)
                is com.ai.assistance.operit.data.model.ConditionNode -> 
                    node.copy(id = newNodeId)
                is com.ai.assistance.operit.data.model.LogicNode -> 
                    node.copy(id = newNodeId)
                is com.ai.assistance.operit.data.model.ExtractNode -> 
                    node.copy(id = newNodeId)
                is com.ai.assistance.operit.data.model.MCPNode -> 
                    node.copy(id = newNodeId)
                is com.ai.assistance.operit.data.model.IntegrationNode -> 
                    node.copy(id = newNodeId)
                else -> node
            }
        }
        
        // Update connections with new node IDs
        val newConnections = workflow.connections.map { connection ->
            connection.copy(
                id = UUID.randomUUID().toString(),
                sourceNodeId = nodeIdMapping[connection.sourceNodeId] ?: connection.sourceNodeId,
                targetNodeId = nodeIdMapping[connection.targetNodeId] ?: connection.targetNodeId
            )
        }
        
        // Create the new workflow with updated IDs
        return workflow.copy(
            id = newWorkflowId,
            name = customName ?: template.name,
            description = template.description,
            nodes = newNodes,
            connections = newConnections,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
