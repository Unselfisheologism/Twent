package com.ai.assistance.operit.util

/**
 * Regex patterns for extracting OpenUI Lang code from AI responses.
 * AI wraps OpenUI code in <openui>...</openui> tags.
 */
object OpenUiRegex {

    /**
     * Matches <openui>...</openui> tags, case-insensitive, multiline.
     * Extracts the full tag block including the tags themselves.
     */
    val openUiTagPattern = Regex(
        "<openui[\\s\\S]*?</openui>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /**
     * Extracts the content INSIDE <openui>...</openui> tags (without the tags).
     */
    val openUiContentPattern = Regex(
        "<openui[\\s\\S]*?>([\\s\\S]*?)</openui>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.MULTILINE)
    )

    /**
     * Extract all OpenUI code blocks from a response string.
     * Returns the content inside each <openui> tag.
     */
    fun extractOpenUiCode(response: String): List<String> {
        return openUiContentPattern.findAll(response).map { it.groupValues[1].trim() }.toList()
    }

    /**
     * Check if a response contains any OpenUI code.
     */
    fun hasOpenUiCode(response: String): Boolean {
        return openUiTagPattern.containsMatchIn(response)
    }

    /**
     * Extract the first OpenUI code block from a response.
     * Returns null if no OpenUI code is found.
     */
    fun extractFirstOpenUiCode(response: String): String? {
        return openUiContentPattern.find(response)?.groupValues?.getOrNull(1)?.trim()
    }

    /**
     * Remove all <openui>...</openui> blocks from a response string.
     * Used when displaying the clean text response (without the code block visible in markdown).
     */
    fun stripOpenUiTags(response: String): String {
        return openUiTagPattern.replace(response, "")
    }
}
