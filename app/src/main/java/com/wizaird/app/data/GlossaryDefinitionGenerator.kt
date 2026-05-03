package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class GlossaryDefinition(
    val term: String,
    val definition: String,
    val aliases: String // Comma-separated
)

/**
 * Generates a glossary definition using AI based on a highlighted term and its context.
 */
suspend fun generateGlossaryDefinition(
    context: Context,
    highlightedTerm: String,
    contextText: String
): GlossaryDefinition = withContext(Dispatchers.IO) {
    val settings = settingsFlow(context).first()
    
    val systemPrompt = """You are a helpful assistant that creates glossary definitions.
Given a term and its context, provide:
1. A clear, concise definition (2-3 sentences max)
2. Any common aliases or acronyms (comma-separated, or leave empty if none)

Respond in this exact format:
DEFINITION: [your definition here]
ALIASES: [comma-separated aliases, or leave empty]"""

    val userPrompt = """Term: "$highlightedTerm"

Context:
$contextText

Please provide a definition and any aliases for this term."""

    try {
        val response = askAi(settings, systemPrompt, userPrompt)
        
        // Parse the AI response
        val definitionMatch = Regex("DEFINITION:\\s*(.+?)(?=ALIASES:|$)", RegexOption.DOT_MATCHES_ALL)
            .find(response)
        val aliasesMatch = Regex("ALIASES:\\s*(.+?)$", RegexOption.DOT_MATCHES_ALL)
            .find(response)
        
        val definition = definitionMatch?.groupValues?.get(1)?.trim() ?: response.trim()
        val aliases = aliasesMatch?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() } ?: ""
        
        GlossaryDefinition(
            term = highlightedTerm,
            definition = definition,
            aliases = aliases
        )
    } catch (e: Exception) {
        // Fallback if AI fails
        GlossaryDefinition(
            term = highlightedTerm,
            definition = "Definition could not be generated. Please add manually.",
            aliases = ""
        )
    }
}
