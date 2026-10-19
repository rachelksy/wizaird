package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class GeneratedConcept(
    val intro: String,  // 1–2 sentence summary
    val body: String    // long-form markdown article
)

/** Builds the system prompt for writing a concept page. */
fun buildConceptSystemPrompt(project: Project?): String {
    val sb = StringBuilder()
    sb.append("You write concept pages for a learner's personal knowledge base inside a microlearning app.\n")
    if (project != null) {
        sb.append("The learner is studying: ${project.name}.\n")
        if (project.instructions.isNotBlank()) sb.append("Their goal: ${project.instructions}\n")
        if (project.background.isNotBlank()) {
            sb.append("What they already know: ${project.background}\n")
        } else {
            sb.append("Assume no prior knowledge.\n")
        }
        if (project.learningProgress.isNotBlank()) {
            sb.append("What they have covered so far:\n${project.learningProgress}\n")
        }
    }
    sb.append("\n---\n\n")
    sb.append("Your task: write a reference page about one concept, so the learner can come back to it later and fully understand it.\n\n")
    sb.append("RULES:\n")
    sb.append("1. Explain the concept in the sense it is used in the learner's topic. If the term means different things elsewhere, focus on this topic's meaning.\n")
    sb.append("2. Cover what it is, how it works, where and why it is used, a concrete example, and how it relates to or differs from neighbouring concepts. Add common misconceptions only if they genuinely help.\n")
    sb.append("3. Use short markdown sections with ## headings, short paragraphs and lists where they help. Typically 300–600 words.\n")
    sb.append("4. Pitch it at the learner's level. Tone: clear, direct, substantive. No filler, no preamble.\n")
    sb.append("5. The \"where the learner met it\" excerpts are context only. Do not quote or summarise them; write about the concept itself.\n\n")
    sb.append("Respond in this exact format:\n")
    sb.append("SUMMARY: [one or two plain sentences defining the concept]\n")
    sb.append("ARTICLE:\n[the markdown article]")
    return sb.toString()
}

/** Builds the user prompt: the concept name plus any excerpts showing where it was met. */
fun buildConceptUserPrompt(name: String, contextSnippets: List<String>): String {
    val sb = StringBuilder()
    sb.append("Concept: \"$name\"\n")
    val snippets = contextSnippets.filter { it.isNotBlank() }.take(5)
    if (snippets.isNotEmpty()) {
        sb.append("\nWhere the learner met it:\n")
        snippets.forEach { sb.append("---\n$it\n") }
    }
    sb.append("\nWrite the concept page.")
    return sb.toString()
}

/**
 * Generates the summary and article for a concept.
 * Returns null on failure so callers can keep the existing text.
 */
suspend fun generateConcept(
    context: Context,
    projectId: String,
    name: String,
    contextSnippets: List<String>
): GeneratedConcept? = withContext(Dispatchers.IO) {
    try {
        val settings = settingsFlow(context).first()
        if (settings.apiKey.isBlank()) return@withContext null
        val project = projectsFlow(context).first().firstOrNull { it.id == projectId }
        val response = askAi(
            settings,
            buildConceptSystemPrompt(project),
            buildConceptUserPrompt(name, contextSnippets)
        )
        parseGeneratedConcept(response)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

fun parseGeneratedConcept(response: String): GeneratedConcept? {
    // askAi reports failures as plain text instead of throwing
    val trimmed = response.trim()
    if (trimmed.isEmpty() || trimmed == "No response." ||
        trimmed.startsWith("API Error") || trimmed.startsWith("Failed to parse API response")
    ) return null
    val summary = Regex("SUMMARY:\\s*(.+?)(?=ARTICLE:|$)", RegexOption.DOT_MATCHES_ALL)
        .find(response)?.groupValues?.get(1)?.trim().orEmpty()
    val article = Regex("ARTICLE:\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
        .find(response)?.groupValues?.get(1)?.trim().orEmpty()
    return when {
        summary.isNotEmpty() || article.isNotEmpty() -> GeneratedConcept(summary, article)
        // Model ignored the format: use the whole reply as the article
        else -> GeneratedConcept(intro = "", body = response.trim())
    }
}
