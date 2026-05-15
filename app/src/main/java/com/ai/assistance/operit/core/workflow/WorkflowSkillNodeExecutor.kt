package com.ai.assistance.operit.core.workflow

import android.content.Context
import com.ai.assistance.operit.data.model.SkillNode
import com.ai.assistance.operit.core.workflow.NodeExecutionState
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.AppLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Executor for SkillNode in workflow system.
 * Loads SKILL.md content from SkillRepository and accumulates skills for AINode use.
 */
class WorkflowSkillNodeExecutor private constructor(private val context: Context) {

    companion object {
        private const val TAG = "WorkflowSkillNodeExecutor"
        
        @Volatile
        private var instance: WorkflowSkillNodeExecutor? = null

        // Class-level cache for accumulated skills across workflow execution
        // Maps workflowId -> (skillName -> content)
        private val skillsCache = mutableMapOf<String, MutableMap<String, String>>()

        fun getInstance(context: Context): WorkflowSkillNodeExecutor {
            return instance ?: synchronized(this) {
                instance ?: WorkflowSkillNodeExecutor(context.applicationContext).also {
                    instance = it
                }
            }
        }

        /**
         * Gets accumulated skills for a workflow.
         * Called by AINode executor to inject skill context.
         *
         * @param workflowId The workflow ID
         * @return Map of skillName to skill content
         */
        fun getAccumulatedSkills(workflowId: String): Map<String, String> {
            return skillsCache[workflowId]?.toMap() ?: emptyMap()
        }

        /**
         * Clears accumulated skills for a workflow.
         * Called after workflow execution completes.
         *
         * @param workflowId The workflow ID
         */
        fun clearAccumulatedSkills(workflowId: String) {
            synchronized(skillsCache) {
                skillsCache.remove(workflowId)
                AppLogger.d(TAG, "Cleared accumulated skills for workflow: $workflowId")
            }
        }

        /**
         * Clears all accumulated skills (for testing).
         */
        fun clearAllSkills() {
            synchronized(skillsCache) {
                skillsCache.clear()
            }
        }
    }

    /**
     * Executes a skill node in a workflow.
     * Loads SKILL.md content for the specified skills and caches them.
     *
     * @param node The skill node to execute
     * @param nodeResults Map of previous node execution results (unused for skill nodes)
     * @param triggerExtras Map of trigger extras (unused for skill nodes)
     * @param workflowId The workflow ID for caching skills
     * @return NodeExecutionState with JSON containing skills and extra instructions
     */
    suspend fun execute(
        node: SkillNode,
        nodeResults: Map<String, NodeExecutionState>,
        triggerExtras: Map<String, String>,
        workflowId: String
    ): NodeExecutionState = withContext(Dispatchers.IO) {
        AppLogger.d(TAG, "Executing skill node: ${node.id}, workflow: $workflowId, skills: ${node.skillNames}")

        try {
            if (node.skillNames.isEmpty()) {
                AppLogger.w(TAG, "Skill node ${node.id} has no skill names defined")
                return@withContext NodeExecutionState.Skipped("No skill names defined")
            }

            val skillRepository = SkillRepository.getInstance(context)
            val loadedSkills = mutableMapOf<String, String>()
            var skippedCount = 0

            // Load each skill's content
            for (skillName in node.skillNames) {
                val content = skillRepository.readSkillContent(skillName)
                
                if (content != null && content.isNotBlank()) {
                    loadedSkills[skillName] = content
                    AppLogger.d(TAG, "Loaded skill: $skillName (${content.length} chars)")
                } else {
                    AppLogger.w(TAG, "Skill not found or empty: $skillName")
                    skippedCount++
                }
            }

            if (loadedSkills.isEmpty()) {
                AppLogger.e(TAG, "No skills could be loaded for node ${node.id}")
                return@withContext NodeExecutionState.Failed("No skills found: ${node.skillNames.joinToString()}")
            }

            // Cache the loaded skills for this workflow
            synchronized(skillsCache) {
                val workflowSkills = skillsCache.getOrPut(workflowId) { mutableMapOf() }
                workflowSkills.putAll(loadedSkills)
                AppLogger.d(TAG, "Cached ${loadedSkills.size} skills for workflow $workflowId (total: ${workflowSkills.size})")
            }

            // Build output JSON
            val outputJson = buildSkillOutputJson(loadedSkills, node.extraInstructions)

            AppLogger.d(TAG, "Skill node ${node.id} executed successfully, loaded ${loadedSkills.size} skills (skipped: $skippedCount)")
            NodeExecutionState.Success(outputJson)

        } catch (e: Exception) {
            AppLogger.e(TAG, "Error executing skill node ${node.id}: ${e.message}", e)
            NodeExecutionState.Failed("Error: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Builds the JSON output string for skill execution result.
     */
    private fun buildSkillOutputJson(skills: Map<String, String>, extraInstructions: String): String {
        val json = JSONObject()
        
        // Add skills map
        val skillsJson = JSONObject()
        for ((name, content) in skills) {
            skillsJson.put(name, content)
        }
        json.put("skills", skillsJson)
        
        // Add extra instructions
        json.put("extra_instructions", extraInstructions)
        
        return json.toString()
    }

    /**
     * Clears the singleton instance (useful for testing).
     */
    fun clearInstance() {
        synchronized(this) {
            instance = null
        }
    }
}