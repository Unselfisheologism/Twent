package com.ai.assistance.operit.data.model

/**
 * Represents the root ACP registry response from the CDN.
 * Fetch from: https://cdn.agentclientprotocol.com/registry/v1/latest/registry.json
 */
data class AcpRegistryResponse(
    val agents: List<AcpAgentEntry> = emptyList()
)

/**
 * Distribution information for an agent.
 */
data class AcpDistribution(
    val npx: AcpNpxDistribution? = null,
    val binary: Map<String, AcpBinaryDistribution>? = null,
    val uvx: AcpUvxDistribution? = null
)

/**
 * NPX distribution (npm package).
 */
data class AcpNpxDistribution(
    val package: String,
    val args: List<String>? = null,
    val env: Map<String, String>? = null
)

/**
 * Binary distribution for a specific platform.
 */
data class AcpBinaryDistribution(
    val archive: String,
    val cmd: String,
    val args: List<String>? = null
)

/**
 * UVX distribution (Python package).
 */
data class AcpUvxDistribution(
    val package: String,
    val args: List<String>? = null
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
    val distribution: AcpDistribution?, // Distribution information
    val license: String?,             // License type
    val authors: List<String>?        // Authors
)
