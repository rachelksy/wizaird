package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates a chat title from the AI's first response.
 * Uses AI to create a concise, contextual title.
 */
suspend fun generateChatTitle(
    context: Context,
    settings: AiSettings,
    firstAiResponse: String,
    firstUserMessage: String
): String = withContext(Dispatchers.IO) {
    return@withContext try {
        val prompt = """
            Generate a short, concise title (3-6 words max) for a chat conversation.
            
            User asked: "$firstUserMessage"
            AI responded: "${firstAiResponse.take(500)}"
            
            Create a title that captures the main topic or question being discussed.
            Return ONLY the title, nothing else. No quotes, no punctuation at the end.
        """.trimIndent()
        
        val title = askAi(settings, "You are a helpful assistant that creates concise chat titles.", prompt)
        
        // Clean up the title
        val cleaned = title.trim()
            .removeSurrounding("\"")
            .removeSurrounding("'")
            .replace(Regex("[.!?]+$"), "")
            .trim()
        
        // Validate length
        if (cleaned.length > 50) {
            cleaned.take(47) + "..."
        } else if (cleaned.isBlank()) {
            // Fallback to user message
            generateFallbackTitle(firstUserMessage)
        } else {
            cleaned
        }
    } catch (e: Exception) {
        println("Failed to generate chat title: ${e.message}")
        // Fallback to user message
        generateFallbackTitle(firstUserMessage)
    }
}

/**
 * Fallback title generation from user message.
 */
private fun generateFallbackTitle(firstUserMessage: String): String {
    val cleaned = firstUserMessage.trim()
    
    if (cleaned.length <= 40) {
        return cleaned
    }
    
    // Try to extract a meaningful title
    val questionMatch = Regex("^([^.!?]{10,40}[?])").find(cleaned)
    if (questionMatch != null) {
        return questionMatch.value
    }
    
    val sentenceMatch = Regex("^([^.!?,]{10,40})[.!?,]").find(cleaned)
    if (sentenceMatch != null) {
        return sentenceMatch.groupValues[1].trim()
    }
    
    return cleaned.take(40).trim() + "..."
}

