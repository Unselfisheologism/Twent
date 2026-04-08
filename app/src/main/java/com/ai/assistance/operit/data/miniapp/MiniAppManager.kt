package com.ai.assistance.operit.data.miniapp

import android.content.Context
import com.ai.assistance.operit.data.model.MiniApp
import com.ai.assistance.operit.data.model.MiniAppType
import com.ai.assistance.operit.util.AppLogger
import kotlinx.serialization.Serializable

/**
 * Central manager for mini-app lifecycle operations.
 * 
 * This is the main entry point for creating, listing, reading, updating, and deleting mini-apps.
 * It coordinates between the storage layer and any other services (like the local web server).
 * 
 * Usage from AI tools:
 *   MiniAppManager.getInstance(context).createMiniApp(scaffold)
 * 
 * Usage from UI:
 *   MiniAppManager.getInstance(context).listMiniApps()
 */
class MiniAppManager private constructor(private val context: Context) {

    private val storage = MiniAppStorage(context)

    companion object {
        private const val TAG = "MiniAppManager"

        @Volatile
        private var INSTANCE: MiniAppManager? = null

        /**
         * Get the singleton instance of MiniAppManager.
         */
        fun getInstance(context: Context): MiniAppManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: MiniAppManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    // ============================================================================
    // CRUD Operations
    // ============================================================================

    /**
     * Create a new mini-app from a scaffold.
     * 
     * The scaffold determines how the mini-app's content is generated:
     * - [MiniAppScaffold.FromPrompt]: AI will generate HTML/CSS/JS from a natural language prompt
     * - [MiniAppScaffold.FromTemplate]: Use a pre-built template
     * - [MiniAppScaffold.FromFiles]: Use raw file contents directly
     * 
     * @return The created MiniApp, or null if creation failed
     */
    suspend fun createMiniApp(scaffold: MiniAppScaffold): Result<MiniApp> {
        return when (scaffold) {
            is MiniAppScaffold.FromFiles -> createFromFiles(scaffold)
            is MiniAppScaffold.FromTemplate -> createFromTemplate(scaffold)
            is MiniAppScaffold.FromPrompt -> {
                // FromPrompt is handled by the AI tool layer — the AI produces
                // a FromFiles or FromTemplate scaffold after generating content.
                // This branch should not be reached directly from here.
                Result.failure(IllegalStateException(
                    "FromPrompt scaffolds must be resolved by the AI agent into FromFiles or FromTemplate first"
                ))
            }
        }
    }

    /**
     * Create a mini-app from raw file contents.
     * This is the most direct creation method.
     */
    private suspend fun createFromFiles(scaffold: MiniAppScaffold.FromFiles): Result<MiniApp> {
        if (scaffold.files.isEmpty()) {
            return Result.failure(IllegalArgumentException("File map cannot be empty"))
        }

        if (!scaffold.files.containsKey(scaffold.entryFile)) {
            return Result.failure(IllegalArgumentException(
                "Entry file '${scaffold.entryFile}' not found in file map"
            ))
        }

        val miniApp = MiniApp(
            name = scaffold.name,
            description = scaffold.description,
            icon = scaffold.icon,
            type = scaffold.type,
            entryFile = scaffold.entryFile,
            requiredPermissions = scaffold.requiredPermissions,
            webPermissions = scaffold.webPermissions,
            metadata = scaffold.metadata
        )

        // Save manifest
        storage.saveManifest(miniApp).onFailure { e ->
            AppLogger.e(TAG, "Failed to save manifest for ${miniApp.name}", e)
            return Result.failure(e)
        }

        // Save all files
        for ((fileName, content) in scaffold.files) {
            storage.saveFile(miniApp, fileName, content).onFailure { e ->
                AppLogger.e(TAG, "Failed to save file $fileName for ${miniApp.name}", e)
                // Clean up on failure
                storage.deleteMiniApp(miniApp.id, miniApp.type)
                return Result.failure(e)
            }
        }

        AppLogger.d(TAG, "Created mini-app: ${miniApp.name} (${miniApp.id})")
        return Result.success(miniApp)
    }

    /**
     * Create a mini-app from a pre-defined template.
     */
    private suspend fun createFromTemplate(scaffold: MiniAppScaffold.FromTemplate): Result<MiniApp> {
        val template = scaffold.template
        val name = scaffold.name ?: template.name

        val files = mutableMapOf<String, String>()

        // Build the HTML file (merge template HTML, CSS, and JS)
        val htmlContent = buildHtmlFromTemplate(template)
        files["index.html"] = htmlContent

        // Save CSS separately if it exists
        if (template.css.isNotBlank()) {
            files["style.css"] = template.css
        }

        // Save JS separately if it exists
        if (template.javascript.isNotBlank()) {
            files["app.js"] = template.javascript
        }

        val permissions = scaffold.customPermissions ?: template.suggestedPermissions
        val webPermissions = template.suggestedWebPermissions

        val fileScaffold = MiniAppScaffold.FromFiles(
            files = files,
            name = name,
            type = scaffold.type,
            description = scaffold.description ?: template.description,
            icon = template.icon,
            entryFile = "index.html",
            requiredPermissions = permissions,
            webPermissions = webPermissions,
            metadata = mapOf(
                "source_template" to template.id,
                "generated_by" to "template"
            )
        )

        return createFromFiles(fileScaffold)
    }

    /**
     * Merge template HTML, CSS, and JS into a single HTML file.
     * If the HTML already contains <style> or <script> blocks, it uses those.
     * Otherwise, it injects CSS and JS as separate <style> and <script> tags.
     */
    private fun buildHtmlFromTemplate(template: com.ai.assistance.operit.data.miniapp.MiniAppTemplate): String {
        var html = template.html

        // If CSS is provided and HTML doesn't have inline styles, inject it
        if (template.css.isNotBlank() && !html.contains("<style", ignoreCase = true)) {
            html = html.replace(
                "</head>",
                "<style>\n${template.css}\n</style>\n</head>",
                ignoreCase = true
            )
        }

        // If JS is provided and HTML doesn't have inline scripts, inject it
        if (template.javascript.isNotBlank() && !html.contains("<script", ignoreCase = true)) {
            html = html.replace(
                "</body>",
                "<script>\n${template.javascript}\n</script>\n</body>",
                ignoreCase = true
            )
        }

        return html
    }

    /**
     * List all mini-apps, optionally filtered by type.
     */
    suspend fun listMiniApps(type: MiniAppType? = null): Result<List<MiniApp>> {
        return storage.listAllMiniApps(type).onFailure { e ->
            AppLogger.e(TAG, "Failed to list mini-apps", e)
        }
    }

    /**
     * Get a specific mini-app by ID.
     * Note: The caller must know the type (persistent vs ephemeral) to locate it.
     * This method searches both directories.
     */
    suspend fun getMiniApp(id: String): Result<MiniApp?> {
        // Try persistent first, then ephemeral
        val persistentResult = storage.loadManifest(id, MiniAppType.PERSISTENT)
        persistentResult.onSuccess { app -> if (app != null) return Result.success(app) }

        val ephemeralResult = storage.loadManifest(id, MiniAppType.EPHEMERAL)
        return ephemeralResult
    }

    /**
     * Delete a mini-app permanently.
     */
    suspend fun deleteMiniApp(id: String, type: MiniAppType): Result<Unit> {
        return storage.deleteMiniApp(id, type).onFailure { e ->
            AppLogger.e(TAG, "Failed to delete mini-app $id", e)
        }.onSuccess {
            AppLogger.d(TAG, "Deleted mini-app $id")
        }
    }

    // ============================================================================
    // File Operations
    // ============================================================================

    /**
     * Read a file from a mini-app's directory.
     */
    suspend fun readFile(miniApp: MiniApp, fileName: String): Result<String?> {
        return storage.readFile(miniApp, fileName)
    }

    /**
     * Save a file to a mini-app's directory.
     */
    suspend fun saveFile(miniApp: MiniApp, fileName: String, content: String): Result<Unit> {
        return storage.saveFile(miniApp, fileName, content)
    }

    /**
     * List files in a mini-app's directory.
     */
    suspend fun listFiles(miniApp: MiniApp, subPath: String = ""): Result<List<String>> {
        return storage.listFiles(miniApp, subPath)
    }

    // ============================================================================
    // Storage Sync (localStorage ↔ Disk)
    // ============================================================================

    /**
     * Backup localStorage data for a mini-app to disk.
     * This should be called periodically or when the WebView reports localStorage changes.
     *
     * The JS inside the mini-app WebView should periodically call a bridge method
     * that passes the localStorage contents here.
     */
    suspend fun backupLocalStorage(miniApp: MiniApp, localStorageData: Map<String, String>): Result<Unit> {
        return try {
            val wrapper = LocalStorageWrapper(localStorageData)
            val json = kotlinx.serialization.json.Json.encodeToString(wrapper)
            storage.saveFile(miniApp, "data/localStorage.json", json)
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to backup localStorage for ${miniApp.name}", e)
            Result.failure(e)
        }
    }

    /**
     * Load previously backed-up localStorage data for a mini-app.
     * Returns the map of key-value pairs, or null if no backup exists.
     */
    suspend fun restoreLocalStorage(miniApp: MiniApp): Result<Map<String, String>?> {
        return try {
            val result = storage.readFile(miniApp, "data/localStorage.json")
            result.map { content ->
                if (content != null) {
                    val wrapper = kotlinx.serialization.json.Json.decodeFromString<LocalStorageWrapper>(content)
                    wrapper.data
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to restore localStorage for ${miniApp.name}", e)
            Result.failure(e)
        }
    }

    // ============================================================================
    // Utility
    // ============================================================================

    /**
     * Get the local URL that the LocalWebServer should serve this mini-app from.
     * The actual serving is done by LocalWebServer on a specific port.
     * 
     * Example: "http://localhost:8095/mini_app/{id}/index.html"
     * 
     * NOTE: The port (8095) should match what LocalWebServer uses for mini-apps.
     * This will be configured when Phase 2 (LocalWebServer extension) is implemented.
     */
    fun getMiniAppUrl(miniApp: MiniApp, port: Int = 8095): String {
        return "http://localhost:$port/mini_app/${miniApp.id}/${miniApp.entryFile}"
    }

    /**
     * Check if a mini-app with the given name already exists.
     */
    suspend fun existsByName(name: String, type: MiniAppType? = null): Boolean {
        return listMiniApps(type).getOrNull()?.any { it.name.equals(name, ignoreCase = true) } == true
    }

    /**
     * Generate a unique name if a mini-app with the given name already exists.
     */
    suspend fun ensureUniqueName(baseName: String, type: MiniAppType? = null): String {
        if (!existsByName(baseName, type)) return baseName
        var counter = 2
        while (existsByName("$baseName $counter", type)) {
            counter++
        }
        return "$baseName $counter"
    }
}

/**
 * Serializable wrapper for localStorage key-value pairs.
 * Needed because kotlinx.serialization cannot auto-infer serializers for raw Map types.
 */
@Serializable
private data class LocalStorageWrapper(val data: Map<String, String>)
