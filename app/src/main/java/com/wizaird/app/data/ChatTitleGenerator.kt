package com.wizaird.app.data

/**
 * Generates a chat title from the user's first message.
 */
fun generateChatTitle(firstUserMessage: String): String {
    val cleaned = firstUserMessage.trim()

    if (cleaned.length <= 40) {
        return cleaned
    }

    // Prefer a question if one appears near the start
    val questionMatch = Regex("^([^.!?]{10,40}[?])").find(cleaned)
    if (questionMatch != null) {
        return questionMatch.value
    }

    // Otherwise take up to the first sentence boundary
    val sentenceMatch = Regex("^([^.!?,]{10,40})[.!?,]").find(cleaned)
    if (sentenceMatch != null) {
        return sentenceMatch.groupValues[1].trim()
    }

    return cleaned.take(40).trim() + "..."
}
