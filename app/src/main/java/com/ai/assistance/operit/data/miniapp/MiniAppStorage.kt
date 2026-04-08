package com.ai.assistance.operit.data.miniapp

import android.content.Context
import com.ai.assistance.operit.data.model.MiniApp
import com.ai.assistance.operit.data.model.MiniAppType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Handles the persistence layer for mini-apps.
 * Manages both the mini-app metadata (JSON) and the app files (HTML/CSS/JS).
 */
class MiniAppStorage(private val context: Context) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    companion object {
        private const val MINI_APPS_DIR = "mini_apps"
        private const val METADATA_FILE = "manifest.json"
        private const val PERSISTENT_SUBDIR = "persistent"
        private const val EPHEMERAL_SUBDIR = "ephemeral"
    }

    /**
     * Root directory for all mini-apps.
     */
    private fun getMiniAppsRoot(): File {
        return File(context.filesDir, MINI_APPS_DIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Directory for persistent mini-apps.
     */
    private fun getPersistentDir(): File {
        return File(getMiniAppsRoot(), PERSISTENT_SUBDIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Directory for ephemeral mini-apps.
     */
    private fun getEphemeralDir(): File {
        return File(getMiniAppsRoot(), EPHEMERAL_SUBDIR).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Directory for a specific mini-app.
     */
    fun getMiniAppDirectory(miniApp: MiniApp): File {
        val baseDir = when (miniApp.type) {
            MiniAppType.PERSISTENT -> getPersistentDir()
            MiniAppType.EPHEMERAL -> getEphemeralDir()
        }
        return File(baseDir, miniApp.id).also {
            if (!it.exists()) it.mkdirs()
        }
    }

    /**
     * Directory for a specific mini-app by ID only (for lookup).
     */
    fun getMiniAppDirectoryById(id: String, type: MiniAppType): File {
        val baseDir = when (type) {
            MiniAppType.PERSISTENT -> getPersistentDir()
            MiniAppType.EPHEMERAL -> getEphemeralDir()
        }
        return File(baseDir, id)
    }

    /**
     * Save a mini-app's manifest (metadata) to disk.
     */
    suspend fun saveManifest(miniApp: MiniApp): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = getMiniAppDirectory(miniApp)
            val manifestFile = File(dir, METADATA_FILE)
            val jsonString = json.encodeToString(miniApp)
            manifestFile.writeText(jsonString)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Load a mini-app's manifest from disk.
     */
    suspend fun loadManifest(id: String, type: MiniAppType): Result<MiniApp?> = withContext(Dispatchers.IO) {
        try {
            val dir = getMiniAppDirectoryById(id, type)
            val manifestFile = File(dir, METADATA_FILE)
            if (manifestFile.exists()) {
                val miniApp = json.decodeFromString<MiniApp>(manifestFile.readText())
                Result.success(miniApp)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List all mini-apps, optionally filtered by type.
     */
    suspend fun listAllMiniApps(type: MiniAppType? = null): Result<List<MiniApp>> = withContext(Dispatchers.IO) {
        try {
            val miniApps = mutableListOf<MiniApp>()
            val dirsToScan = when (type) {
                MiniAppType.PERSISTENT -> listOf(getPersistentDir())
                MiniAppType.EPHEMERAL -> listOf(getEphemeralDir())
                null -> listOf(getPersistentDir(), getEphemeralDir())
            }

            for (dir in dirsToScan) {
                if (!dir.exists()) continue
                val subDirs = dir.listFiles()?.filter { it.isDirectory } ?: continue
                for (subDir in subDirs) {
                    val manifestFile = File(subDir, METADATA_FILE)
                    if (manifestFile.exists()) {
                        try {
                            val miniApp = json.decodeFromString<MiniApp>(manifestFile.readText())
                            miniApps.add(miniApp)
                        } catch (e: Exception) {
                            // Skip corrupted manifests
                        }
                    }
                }
            }

            // Sort by updatedAt descending (most recently updated first)
            miniApps.sortByDescending { it.updatedAt }
            Result.success(miniApps)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Delete a mini-app's entire directory.
     */
    suspend fun deleteMiniApp(id: String, type: MiniAppType): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = getMiniAppDirectoryById(id, type)
            if (dir.exists()) {
                dir.deleteRecursively()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save a file within a mini-app's directory.
     */
    suspend fun saveFile(miniApp: MiniApp, fileName: String, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = getMiniAppDirectory(miniApp)
            // Support subdirectories in the file name (e.g. "css/style.css")
            val file = File(dir, fileName).also {
                it.parentFile?.mkdirs()
            }
            file.writeText(content)
            // Update the manifest's updatedAt
            val updatedMiniApp = miniApp.copy(updatedAt = System.currentTimeMillis())
            saveManifest(updatedMiniApp)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Save binary file within a mini-app's directory.
     */
    suspend fun saveFileBinary(miniApp: MiniApp, fileName: String, content: ByteArray): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val dir = getMiniAppDirectory(miniApp)
            val file = File(dir, fileName).also {
                it.parentFile?.mkdirs()
            }
            file.writeBytes(content)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Read a file from a mini-app's directory.
     */
    suspend fun readFile(miniApp: MiniApp, fileName: String): Result<String?> = withContext(Dispatchers.IO) {
        try {
            val dir = getMiniAppDirectory(miniApp)
            val file = File(dir, fileName)
            if (file.exists() && file.isFile) {
                Result.success(file.readText())
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * List files in a mini-app's directory.
     */
    suspend fun listFiles(miniApp: MiniApp, subPath: String = ""): Result<List<String>> = withContext(Dispatchers.IO) {
        try {
            val dir = getMiniAppDirectory(miniApp)
            val targetDir = if (subPath.isEmpty()) dir else File(dir, subPath)
            if (targetDir.exists() && targetDir.isDirectory) {
                val files = targetDir.listFiles()?.map {
                    if (it.isDirectory) "${it.name}/" else it.name
                } ?: emptyList()
                Result.success(files)
            } else {
                Result.success(emptyList())
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
