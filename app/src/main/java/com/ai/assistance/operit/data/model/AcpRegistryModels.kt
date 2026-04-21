package com.ai.assistance.operit.data.model

/**
 * Represents the root ACP registry response from the CDN.
 * Fetch from: https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json
 */
data class AcpRegistryResponse(
    val agents: List<AcpAgentEntry> = emptyList()
)

/**
 * Represents a single agent entry from the ACP registry.
 */
data class AcpAgentEntry(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val repository: String?,          // GitHub repo URL
    val icon: String?,                // Icon URL or SVG data
    val homepage: String?,            // Project homepage
    val installCommand: String?,      // npm install, pip install, etc.
    val binary: String?,              // The binary name for `which` check
    val authMethods: List<String>?,   // Supported auth methods
    val tags: List<String>?           // Tags/categories
)
