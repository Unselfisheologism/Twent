package com.ai.assistance.operit.api.chat.brain

import android.content.Context
import com.ai.assistance.operit.core.tools.AIToolHandler
import com.ai.assistance.operit.core.tools.StringResultData
import com.ai.assistance.operit.core.tools.skill.SkillManager
import com.ai.assistance.operit.data.model.AITool
import com.ai.assistance.operit.data.model.ToolResult
import com.ai.assistance.operit.data.skill.SkillRepository
import com.ai.assistance.operit.util.AppLogger
import java.io.File
import java.util.UUID

/**
 * MCVP Hermes-Level Skills Manager
 *
 * Brings hermes-agent's "Skills Are Slash Commands" philosophy to the AI Chat agent.
 *
 * Three layers of progressive disclosure:
 *   Layer 1: /slash-command detection   — user types `/android-development` → auto-loads skill
 *   Layer 2: skill_view / skills_list   — native tools so the brain can query available skills
 *   Layer 3: skill_manage               — create, update, delete skills from within the chat
 *
 * Architecture:
 *   TwSkillsManager (this file)  →  SkillManager  →  downloads/Twent/skills/<name>/SKILL.md
 *   TwConversationLoop             →  processSlashCommand() before sending to AI
 *   BrainToolCallProvider           →  injectLoadedSkills() into system prompt
 *
 * hermes-agent equivalent:
 *   /android-development           →  skill activated, instructions injected forever
 *   /skills                        →  skill catalog shown
 *   You build once. Type / forever.
 */

// ─────────────────────────────────────────────
// 1. State — what skills are currently loaded
// ─────────────────────────────────────────────

/**
 * Tracks skills loaded into the current session by TwSkillsManager.
 * Serialized to TwConversationLoopState so it survives rotation/backgrounding.
 *
 * hermes-agent injects skill content into the context once; it stays active
 * for the rest of the session (unless /skill_name again reloads it).
 */
data class TwLoadedSkill(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val displayName: String,
    val description: String,
    val category: String,
    val triggerKeywords: List<String>,
    val skillContent: String,  // Full SKILL.md text — injected into system prompt
    val loadedAt: Long = System.currentTimeMillis()
)

/**
 * Per-conversation skills state.
 * Multiple skills can be loaded simultaneously — each adds its instructions.
 */
data class TwSkillsState(
    val loadedSkills: List<TwLoadedSkill> = emptyList(),
    /** Skills that should be auto-activated when relevant keywords appear */
    val autoActivatedSkills: Set<String> = emptySet()
) {
    fun isLoaded(skillName: String) = loadedSkills.any {
        it.name.equals(skillName, ignoreCase = true) ||
        it.displayName.equals(skillName, ignoreCase = true)
    }
}

/**
 * Lightweight skill catalog entry — just metadata, no full content.
 * Used by TwAgentChatBrain.processSlashCommand() to show skill listings
 * without doing any disk IO.
 *
 * hermes-agent equivalent: skill directory scan → autocomplete → /skill-name
 */
data class TwSkillInfo(
    val name: String,         // "android-development"
    val displayName: String,   // "Android Development"
    val description: String,  // Short one-liner
    val category: String,     // "android", "devops", "creative", etc.
    val aliases: List<String> = emptyList()  // Alternative slash names
)

// ─────────────────────────────────────────────
// 2. Slash command parser
//    Detects /skill-name patterns in user input
// ─────────────────────────────────────────────

/**
 * Parses a skill slash command from raw user input.
 * Returns null if no slash command found.
 *
 * Examples:
 *   "/android-development"           → SlashCommand("android-development", null)
 *   "/android-development fix bug"   → SlashCommand("android-development", "fix bug")
 *   "/skill android-development"     → SlashCommand("android-development", null)
 *   "/sage scout ai agents x.com"   → SlashCommand("sage", "scout ai agents x.com")
 */
data class TwSlashCommand(
    val skillName: String,
    val arguments: String?
)

private val SLASH_PATTERN = Regex("""^(?:/(\w[\w-]*))(?:\s+(.+))?$""", RegexOption.DOT_MATCHES_ALL)

/**
 * Attempts to parse a skill slash command from [rawInput].
 * Returns null if [rawInput] doesn't match the slash-command pattern.
 *
 * Also returns null for built-in power toggles (/yolo, /fast, /reasoning)
 * since those are handled by TwPowerToggles.
 */
fun parseSlashCommand(rawInput: String): TwSlashCommand? {
    val trimmed = rawInput.trim()
    if (!trimmed.startsWith('/')) return null

    // Skip built-in power toggles
    val lower = trimmed.lowercase()
    if (lower.startsWith("/yolo") || lower.startsWith("/fast") ||
        lower.startsWith("/reasoning") || lower.startsWith("/toolset") ||
        lower.startsWith("/snapshot") || lower.startsWith("/rollback") ||
        lower.startsWith("/branch") || lower.startsWith("/btw") ||
        lower.startsWith("/steer") || lower.startsWith("/queue") ||
        lower.startsWith("/model")) {
        return null
    }

    val match = SLASH_PATTERN.find(trimmed) ?: return null
    val skillName = match.groupValues[1].lowercase()
    val arguments = match.groupValues[2].takeIf { it.isNotBlank() }
    return TwSlashCommand(skillName, arguments)
}

// ─────────────────────────────────────────────
// 3. Frontmatter parser — extract hermes-style
//    metadata from SKILL.md YAML frontmatter
// ─────────────────────────────────────────────

/**
 * Extended frontmatter fields beyond name/description.
 * These drive auto-activation and UI display.
 */
data class TwSkillMeta(
    val name: String,
    val description: String,
    val category: String = "general",
    val keywords: List<String> = emptyList(),
    val examples: List<String> = emptyList(),
    val triggersOn: List<String> = emptyList(),
    val skillFilePath: String = ""
)

private val FRONTMATTER_START = Regex("""^---\s*$""")

private fun parseSkillMeta(skillFile: File, rawContent: String): TwSkillMeta {
    val lines = rawContent.lines()
    val name: String
    val description: String
    val category: String
    val keywords: List<String>
    val examples: List<String>
    val triggersOn: List<String>

    if (lines.isNotEmpty() && FRONTMATTER_START.matches(lines[0])) {
        val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (endIndex >= 0) {
            val frontmatter = lines.subList(1, endIndex + 1)
            var n = ""; var d = ""; var c = ""; var kw = emptyList<String>()
            var ex = emptyList<String>(); var tr = emptyList<String>()

            frontmatter.forEach { raw ->
                val line = raw.trim()
                val colon = line.indexOf(':')
                if (colon <= 0) return@forEach
                val key = line.substring(0, colon).trim().lowercase()
                val rawVal = line.substring(colon + 1).trim()

                // Parse YAML list or quoted string
                val value: String = when {
                    rawVal.startsWith('[') && rawVal.endsWith(']') -> {
                        rawVal.removeSurrounding("[", "]")
                            .split(',').map { it.trim().removeSurrounding("\"", "'") }
                            .filter { it.isNotBlank() }.joinToString(",")
                    }
                    rawVal.startsWith('"') || rawVal.startsWith('\'') -> {
                        rawVal.removeSurrounding("\"", "'")
                    }
                    else -> rawVal
                }

                when (key) {
                    "name"       -> n = value
                    "description"-> d = value
                    "category"   -> c = value
                    "keywords"   -> kw = value.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    "examples"   -> ex = value.split(",").map { it.trim().filter { c2 -> c2 != '"' && c2 != '\'' } }.filter { it.isNotBlank() }
                    "triggers"   -> tr = value.split(",").map { it.trim().removeSurrounding("\"", "'") }.filter { it.isNotBlank() }
                }
            }

            name = n.ifBlank { skillFile.parentFile?.name ?: "" }
            description = d
            category = c.ifBlank { "general" }
            keywords = kw
            examples = ex
            triggersOn = tr
        } else {
            name = skillFile.parentFile?.name ?: ""; description = ""; category = "general"
            keywords = emptyList(); examples = emptyList(); triggersOn = emptyList()
        }
    } else {
        // No frontmatter — fallback to filename and first-line description
        name = skillFile.parentFile?.name ?: ""
        description = lines.firstOrNull { it.isNotBlank() && !it.startsWith("#") } ?: ""
        category = "general"; keywords = emptyList(); examples = emptyList(); triggersOn = emptyList()
    }

    return TwSkillMeta(
        name = name,
        description = description,
        category = category,
        keywords = keywords,
        examples = examples,
        triggersOn = triggersOn,
        skillFilePath = skillFile.absolutePath
    )
}

// ─────────────────────────────────────────────
// 4. TwSkillsManager singleton
// ─────────────────────────────────────────────

/**
 * Orchestrates hermes-agent-style skills for the AI Chat agent.
 *
 * Entry points:
 *   - TwConversationLoop.processSlashCommand()     → parseSlashCommand + loadSkill()
 *   - TwPromptBuilder.injectLoadedSkills()         → getSkillsInjection()
 *   - BrainToolCallProvider                        → registerNativeSkillTools()
 *
 * hermes-agent equivalent behaviours:
 *   - `/sage`         → skill loaded, instructions injected forever
 *   - `/skills`       → catalog of all available skills
 *   - skill re-load   → refreshes instructions, same effect as /skill_name
 */
object TwSkillsManager {

    private const val TAG = "TwSkillsManager"

    private var skillManager: SkillManager? = null
    private var skillRepository: SkillRepository? = null

    /**
     * Must be called once during app startup / AI Chat page init.
     * Provides the Context needed to access the skills directory.
     */
    fun initialize(context: Context) {
        skillManager = SkillManager.getInstance(context)
        skillRepository = SkillRepository.getInstance(context)
        AppLogger.d(TAG, "TwSkillsManager initialized. Skills dir: ${skillManager?.getSkillsDirectoryPath()}")
    }

    // ─── Skill Catalog (fast, no disk IO after init) ────────────────────────────

    /**
     * Returns a lightweight catalog of all available skills — just metadata.
     * hermes-agent equivalent: skill directory scan → autocomplete.
     *
     * This reads only the frontmatter (first 15 lines) of each SKILL.md file.
     * No full content read = fast. Cached in-memory.
     */
    fun getSkillsCatalog(): List<TwSkillInfo> {
        val sm = skillManager
        if (sm == null) {
            AppLogger.w(TAG, "getSkillsCatalog called before initialize()")
            return emptyList()
        }
        val availableMap = sm.getAvailableSkills()
        return availableMap.entries.map { (skillName, pkg) ->
            val category = try {
                val lines = pkg.skillFile.bufferedReader().use { it.readLines() }
                lines.take(20).find { it.trim().startsWith("category:") }
                    ?.substringAfter(":")?.trim()?.removeSurrounding("\"", "'")
                    ?: "general"
            } catch (_: Exception) {
                "general"
            }
            TwSkillInfo(
                name = skillName,
                displayName = skillName.replace("-", " ").replaceFirstChar { it.uppercase() },
                description = pkg.description,
                category = category,
                triggerKeywords = emptyList()
            )
        }
    }

    // ─── Layer 1: Slash Command Processing ─────────────────────────────────────

    /**
     * Detects and processes a slash command from [rawInput].
     * If [rawInput] starts with `/skill-name`, loads that skill and returns a
     * description of what was loaded.
     *
     * Returns null if no slash command was found (or if it's a power toggle).
     * Returns a human-readable result string for display.
     *
     * Call this BEFORE sending input to the AI.
     */
    fun processSlashCommand(rawInput: String): String? {
        val cmd = parseSlashCommand(rawInput) ?: return null

        val result = loadSkill(cmd.skillName)
        return if (result != null) {
            val args = cmd.arguments?.let { " with args: `$it`" } ?: ""
            "✅ Skill **${result.displayName}** loaded.$args\n" +
            "Skill instructions are now active for this session.\n" +
            result.description
        } else {
            null
        }
    }

    /**
     * Loads a skill by [skillName] (case-insensitive).
     * Parses frontmatter and stores the full SKILL.md content.
     *
     * hermes-agent behaviour: loading a skill replaces its content in context.
     *
     * @return TwLoadedSkill if found and loaded, null if not found
     */
    fun loadSkill(skillName: String): TwLoadedSkill? {
        val sm = skillManager ?: run {
            AppLogger.e(TAG, "TwSkillsManager not initialized — call initialize(context) first")
            return null
        }

        val allSkills = sm.getAvailableSkills()

        // Match by exact name, then fuzzy (lowercase, hyphens/underscores interchangeable)
        val matched = allSkills.entries.find { (name, _) ->
            name.equals(skillName, ignoreCase = true) ||
            name.equals(skillName.replace('-', '_'), ignoreCase = true) ||
            name.equals(skillName.replace('_', '-'), ignoreCase = true)
        }

        if (matched == null) {
            AppLogger.d(TAG, "Skill not found: $skillName. Available: ${allSkills.keys}")
            return null
        }

        val (_, pkg) = matched
        val rawContent = sm.readSkillContent(pkg.name) ?: return null
        val meta = parseSkillMeta(pkg.skillFile, rawContent)

        val loaded = TwLoadedSkill(
            name = pkg.name,
            displayName = meta.name,
            description = meta.description,
            category = meta.category,
            triggerKeywords = meta.keywords + meta.triggersOn,
            skillContent = rawContent
        )

        AppLogger.d(TAG, "Loaded skill: ${loaded.displayName} (${meta.category})")
        return loaded
    }

    // ─── Layer 2: Context Injection ────────────────────────────────────────────

    /**
     * Injects loaded skills into the system prompt.
     * Called by TwPromptBuilder.buildSystemPrompt() — append this to the base prompt.
     *
     * hermes-agent behaviour: skill instructions are injected once and stay active.
     * Multiple skills accumulate — agent sees ALL loaded skill instructions.
     *
     * Format:
     *   ## Active Skills
     *   ### android-development
     *   Category: android | Keywords: gradle, compose, xml
     *   > Your instructions for this skill...
     */
    fun getSkillsInjection(loadedSkills: List<TwLoadedSkill>): String {
        if (loadedSkills.isEmpty()) return ""

        return buildString {
            appendLine()
            appendLine("## Active Skills")
            appendLine("(loaded via /slash-command — remain active for this session)")
            appendLine()

            loadedSkills.forEach { skill ->
                val keywords = if (skill.triggerKeywords.isNotEmpty()) {
                    " | Keywords: ${skill.triggerKeywords.joinToString(", ")}"
                } else ""

                appendLine("### ${skill.displayName}")
                appendLine("Category: ${skill.category}$keywords")
                appendLine()
                // Strip YAML frontmatter from displayed content — the agent already knows the metadata
                appendLine(stripFrontmatter(skill.skillContent))
                appendLine()
            }
        }
    }

    /**
     * Auto-activation check: given [userMessage], return skills that should be
     * loaded based on trigger keywords in their frontmatter.
     *
     * This is the "proactive" hermes-agent behaviour:
     * skill triggers when relevant keywords appear in conversation.
     */
    fun checkAutoActivation(
        userMessage: String,
        loadedSkills: List<TwLoadedSkill>,
        allSkills: Map<String, Any>
    ): List<String> {
        val alreadyLoaded = loadedSkills.map { it.name.lowercase() }.toSet()
        val lowerMsg = userMessage.lowercase()

        return loadedSkills
            .filter { skill ->
                skill.triggerKeywords.any { kw ->
                    kw.isNotBlank() && lowerMsg.contains(kw.lowercase())
                }
            }
            .map { it.name }
            .filter { it.lowercase() !in alreadyLoaded }
    }

    private fun stripFrontmatter(content: String): String {
        val lines = content.lines()
        if (lines.isEmpty() || !FRONTMATTER_START.matches(lines[0])) return content
        val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
        return if (endIndex >= 0) {
            lines.drop(endIndex + 2).joinToString("\n").trim()
        } else content
    }

    // ─── Layer 3: Skill Catalog ────────────────────────────────────────────────

    /**
     * Returns a markdown catalog of all available skills.
     * Used by the /skills slash command and skills_list tool.
     */
    fun getSkillCatalog(
        loadedSkills: List<TwLoadedSkill> = emptyList(),
        maxResults: Int = 50
    ): String {
        val sm = skillManager ?: return "⚠️ Skills manager not initialized."
        val allPackages = sm.getAvailableSkills()

        if (allPackages.isEmpty()) {
            return "📭 No skills installed.\n\n" +
                   "Skills are `.md` files in `Downloads/Twent/skills/`.\n" +
                   "Import from GitHub: paste a repo URL into the chat."
        }

        val loadedSet = loadedSkills.map { it.name.lowercase() }.toSet()

        // Group by category
        val byCategory = allPackages.entries
            .groupBy { (_, pkg) ->
                // Parse category from frontmatter without re-reading file
                parseSkillMetaQuick(pkg.skillFile)
            }
            .toSortedMap()

        return buildString {
            appendLine("## Available Skills (${allPackages.size})")
            appendLine()
            appendLine("Use `/skill-name` to load a skill — its instructions inject into context permanently.")
            appendLine()

            byCategory.forEach { (category, skills) ->
                appendLine("### $category")
                skills.forEach { (_, pkg) ->
                    val isActive = pkg.name.lowercase() in loadedSet
                    val marker = if (isActive) " ✅" else "  "
                    val desc = pkg.description.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                    appendLine("- $marker **`${pkg.name}`**$desc")
                }
                appendLine()
            }

            appendLine("---")
            appendLine("Tip: Create your own skill — `/skill-create <name>`")
            appendLine("Or import from GitHub: `/import-skill https://github.com/user/repo`")
        }
    }

    /**
     * Quick category parse without full content reading (uses cached metadata).
     */
    private fun parseSkillMetaQuick(skillFile: File): String {
        return try {
            // First 15 lines is enough for frontmatter
            val lines = skillFile.bufferedReader().use { it.readLines() }.take(15)
            if (lines.isNotEmpty() && FRONTMATTER_START.matches(lines[0])) {
                val endIndex = lines.drop(1).indexOfFirst { it.trim() == "---" }
                if (endIndex >= 0) {
                    lines.subList(1, endIndex + 1).forEach { raw ->
                        val line = raw.trim()
                        val colon = line.indexOf(':')
                        if (colon > 0 && line.substring(0, colon).trim().lowercase() == "category") {
                            return line.substring(colon + 1).trim().removeSurrounding("\"", "'")
                        }
                    }
                }
            }
            "general"
        } catch (_: Exception) {
            "general"
        }
    }

    // ─── Layer 4: Native Tool Registration ─────────────────────────────────────
    // Registers skill tools into AIToolHandler so the brain can use them
    // without relying on the MCP server. These wrap the existing SkillManager.
    // Uses the lambda-based registerTool() overload: executor: (AITool) -> ToolResult
    // ──────────────────────────────────────────────────────────────────────────

    /**
     * Registers all native skill tools into [handler].
     * Call this from AIToolHandler.registerDefaultTools() alongside existing tool registrations.
     *
     * Tools registered:
     *   skills_list    — list all available skills (cached, no re-read)
     *   skill_view     — view a specific skill's full content
     *   skill_delete   — delete a skill by name
     *   skill_create   — create a new skill from name + content
     *   skill_search   — search skills by keyword or category
     *
     * @param ctx Application context — used to access skills directory via SkillManager
     */
    @JvmStatic
    fun registerNativeSkillTools(ctx: Context, handler: AIToolHandler) {
        initialize(ctx)
        registerSkillsListTool(ctx, handler)
        registerSkillViewTool(ctx, handler)
        registerSkillDeleteTool(ctx, handler)
        registerSkillCreateTool(ctx, handler)
        registerSkillSearchTool(ctx, handler)
    }

    private fun registerSkillsListTool(ctx: Context, handler: AIToolHandler) {
        handler.registerTool(
            name = "skills_list",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val category = tool.parameters.find { it.name == "category" }?.value
                if (category.isNullOrBlank()) {
                    "List all available skills with name, category, and description. Returns a markdown catalog."
                } else {
                    "List skills in category `$category`. Returns a markdown catalog."
                }
            },
            executor = { _ ->
                try {
                    val sm = SkillManager.getInstance(ctx)
                    val allSkills = sm.getAvailableSkills()

                    val sb = StringBuilder()
                    sb.appendLine("Available Skills (${allSkills.size}):")
                    sb.appendLine()

                    allSkills.entries
                        .sortedBy { it.value.description.isBlank() }
                        .forEach { (name, pkg) ->
                            val cat = parseSkillMetaQuick(pkg.skillFile)
                            val desc = pkg.description.takeIf { it.isNotBlank() } ?: "(no description)"
                            sb.appendLine("- **$name** [$cat] — $desc")
                        }

                    ToolResult(
                        toolName = "skills_list",
                        success = true,
                        result = StringResultData(sb.toString())
                    )
                } catch (e: Exception) {
                    ToolResult(toolName = "skills_list", success = false,
                        result = StringResultData(""), error = "Error listing skills: ${e.message}")
                }
            }
        )
    }

    private fun registerSkillViewTool(ctx: Context, handler: AIToolHandler) {
        handler.registerTool(
            name = "skill_view",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                if (name.isBlank()) "View a skill's full SKILL.md content by name"
                else "View full content of skill **`$name`** — includes YAML frontmatter and all instructions"
            },
            executor = { tool ->
                val skillName = tool.parameters.find { it.name == "name" }?.value?.trim()
                if (skillName.isNullOrBlank()) {
                    return@registerTool ToolResult(toolName = "skill_view", success = false,
                        result = StringResultData(""), error = "Missing required parameter: name")
                }

                try {
                    val sm = SkillManager.getInstance(ctx)
                    val content = sm.readSkillContent(skillName)
                    if (content != null) {
                        ToolResult(toolName = "skill_view", success = true,
                            result = StringResultData(content))
                    } else {
                        ToolResult(toolName = "skill_view", success = false,
                            result = StringResultData(""), error = "Skill not found: $skillName")
                    }
                } catch (e: Exception) {
                    ToolResult(toolName = "skill_view", success = false,
                        result = StringResultData(""), error = "Error loading skill: ${e.message}")
                }
            }
        )
    }

    private fun registerSkillDeleteTool(ctx: Context, handler: AIToolHandler) {
        handler.registerTool(
            name = "skill_delete",
            dangerCheck = { true },
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                if (name.isBlank()) "Delete a skill directory by name (irreversible)"
                else "⚠️ Delete skill **`$name`** and all its files (irreversible)"
            },
            executor = { tool ->
                val skillName = tool.parameters.find { it.name == "name" }?.value?.trim()
                if (skillName.isNullOrBlank()) {
                    return@registerTool ToolResult(toolName = "skill_delete", success = false,
                        result = StringResultData(""), error = "Missing required parameter: name")
                }

                try {
                    val sm = SkillManager.getInstance(ctx)
                    val deleted = sm.deleteSkill(skillName)
                    if (deleted) {
                        ToolResult(toolName = "skill_delete", success = true,
                            result = StringResultData("✅ Skill `$skillName` deleted."))
                    } else {
                        ToolResult(toolName = "skill_delete", success = false,
                            result = StringResultData(""), error = "Skill not found or could not be deleted: $skillName")
                    }
                } catch (e: Exception) {
                    ToolResult(toolName = "skill_delete", success = false,
                        result = StringResultData(""), error = "Error deleting skill: ${e.message}")
                }
            }
        )
    }

    private fun registerSkillCreateTool(ctx: Context, handler: AIToolHandler) {
        handler.registerTool(
            name = "skill_create",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value ?: ""
                val desc = tool.parameters.find { it.name == "description" }?.value ?: ""
                if (name.isBlank()) "Create a new skill with name, description, category, and content"
                else "Create skill **`$name`** — ${desc.take(60)}"
            },
            executor = { tool ->
                val name = tool.parameters.find { it.name == "name" }?.value?.trim()
                val description = tool.parameters.find { it.name == "description" }?.value ?: ""
                val category = tool.parameters.find { it.name == "category" }?.value ?: "general"
                val content = tool.parameters.find { it.name == "content" }?.value ?: ""

                if (name.isNullOrBlank()) {
                    return@registerTool ToolResult(toolName = "skill_create", success = false,
                        result = StringResultData(""), error = "Missing required parameter: name")
                }

                try {
                    val sm = SkillManager.getInstance(ctx)
                    val skillsDir = File(sm.getSkillsDirectoryPath())
                    val skillDir = File(skillsDir, name.trim())

                    if (skillDir.exists()) {
                        return@registerTool ToolResult(toolName = "skill_create", success = false,
                            result = StringResultData(""),
                            error = "Skill `$name` already exists. Use `/skill-view $name` to see it, or choose a different name.")
                    }

                    skillDir.mkdirs()
                    val skillFile = File(skillDir, "SKILL.md")

                    val skillMdContent = buildString {
                        appendLine("---")
                        appendLine("name: $name")
                        appendLine("description: $description")
                        appendLine("category: $category")
                        appendLine("keywords: []")
                        appendLine("examples: []")
                        appendLine("triggers: []")
                        appendLine("---")
                        appendLine()
                        if (content.isNotBlank()) {
                            appendLine(content)
                        } else {
                            appendLine("# $name")
                            appendLine()
                            appendLine("Write your skill instructions here. Be specific about:")
                            appendLine("- What this skill does")
                            appendLine("- When to activate it")
                            appendLine("- Key steps or patterns to follow")
                        }
                    }

                    skillFile.writeText(skillMdContent)
                    sm.refreshAvailableSkills()

                    ToolResult(toolName = "skill_create", success = true,
                        result = StringResultData(
                            "✅ Skill **`$name`** created at:\n`${skillFile.absolutePath}`\n\n" +
                            "Use `/skill-view $name` to see it, or type `/$name` to activate it."
                        )
                    )
                } catch (e: Exception) {
                    ToolResult(toolName = "skill_create", success = false,
                        result = StringResultData(""), error = "Error creating skill: ${e.message}")
                }
            }
        )
    }

    private fun registerSkillSearchTool(ctx: Context, handler: AIToolHandler) {
        handler.registerTool(
            name = "skill_search",
            dangerCheck = { false },
            descriptionGenerator = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value ?: ""
                "Search installed skills by keyword, category, or description.\n" +
                if (query.isBlank()) "Returns all skills ranked by relevance to your query."
                else "Query: `$query`"
            },
            executor = { tool ->
                val query = tool.parameters.find { it.name == "query" }?.value?.trim() ?: ""
                try {
                    val sm = SkillManager.getInstance(ctx)
                    val allSkills = sm.getAvailableSkills()
                    val lowerQuery = query.lowercase()

                    val results = allSkills.entries
                        .filter { (name, pkg) ->
                            name.lowercase().contains(lowerQuery) ||
                            pkg.description.lowercase().contains(lowerQuery) ||
                            parseSkillMetaQuick(pkg.skillFile).lowercase().contains(lowerQuery)
                        }
                        .sortedByDescending { (name, _) -> name.lowercase().startsWith(lowerQuery) }
                        .take(20)

                    if (results.isEmpty()) {
                        ToolResult(toolName = "skill_search", success = true,
                            result = StringResultData(
                                if (query.isBlank()) "No skills found."
                                else "No skills match: `$query`"
                            ))
                    } else {
                        val sb = StringBuilder()
                        sb.appendLine("Found ${results.size} skill(s):")
                        results.forEach { (name, pkg) ->
                            val cat = parseSkillMetaQuick(pkg.skillFile)
                            val desc = pkg.description.takeIf { it.isNotBlank() } ?: "(no description)"
                            sb.appendLine("- **$name** [$cat] — $desc")
                        }
                        ToolResult(toolName = "skill_search", success = true,
                            result = StringResultData(sb.toString()))
                    }
                } catch (e: Exception) {
                    ToolResult(toolName = "skill_search", success = false,
                        result = StringResultData(""), error = "Error searching skills: ${e.message}")
                }
            }
        )
    }
}
