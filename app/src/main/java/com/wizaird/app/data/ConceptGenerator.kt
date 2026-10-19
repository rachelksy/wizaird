package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class GeneratedConcept(
    val intro: String,  // 1–2 sentence summary
    val body: String    // long-form markdown article
)

/**
 * Builds the system prompt for writing a concept page: a full expert treatment,
 * as if the user had asked a professor "tell me more about [concept]".
 * Deliberately not tailored to the learner's level or progress.
 */
fun buildConceptSystemPrompt(project: Project?): String {
    val sb = StringBuilder()
    sb.append("You write the article for a concept in a learner's personal knowledge base. ")
    sb.append("They saved this concept because they want to understand it properly. Treat it as if they had asked a leading expert in the field, \"Tell me more about this.\"\n")
    if (project != null) {
        sb.append("The concept came up while they were studying: ${project.name}. ")
        sb.append("Use this only to pick the right meaning of the term. Do not narrow the article to this topic or simplify it for a beginner.\n")
    }
    sb.append("\n---\n\n")
    sb.append("Your task: write a complete, authoritative article on the concept, at the depth of a strong textbook section or an encyclopedia entry written by a subject expert.\n\n")
    sb.append("WHAT TO COVER (use what fits the concept, skip what doesn't apply, add what it needs):\n")
    sb.append("- A precise definition, then the core idea in plain words.\n")
    sb.append("- Why it exists: the problem it solves or the question it answers, and its origin if that makes it clearer.\n")
    sb.append("- How it works in full: mechanisms, components, steps, rules, formal definitions, and formulas with every term explained.\n")
    sb.append("- Variants, types or schools of thought, and how they differ.\n")
    sb.append("- At least one concrete example worked through in detail.\n")
    sb.append("- Where it is used in practice, plus its limits, trade-offs, failure modes and open debates.\n")
    sb.append("- Subtle points and common misconceptions: what experts know that newcomers miss.\n")
    sb.append("- How it relates to and differs from neighbouring concepts.\n\n")
    sb.append("RULES:\n")
    sb.append("1. Write at expert level for an intelligent reader. Do not water anything down or leave out technical detail. Use the field's real terminology, and define each term the first time it appears.\n")
    sb.append("2. Be specific: real mechanisms, real numbers, real names. Prefer substance over generalities.\n")
    sb.append("3. Let the concept's scope set the length: roughly 1,000–1,500 words for a narrow term, up to about 2,500 for a broad topic. Never pad it, and never cut it short.\n")
    sb.append("4. Use markdown with ## section headings chosen for this concept (not a fixed template), ### subheadings inside large sections, short paragraphs, and lists or tables where they help. Write formulas in plain text or code blocks, not LaTeX. Start straight with the content: no preamble, no sign-off.\n")
    sb.append("5. Be accurate before being complete. If a point is contested or uncertain, say so.\n")
    sb.append("6. The excerpts showing where the learner came across the concept are only for picking the right meaning. Do not quote them, summarise them or build the article around them.\n\n")
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
        sb.append("\nWhere the learner came across it (only for picking the right meaning):\n")
        snippets.forEach { sb.append("---\n$it\n") }
    }
    sb.append("\nWrite the article.")
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
