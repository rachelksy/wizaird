package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

data class GeneratedConcept(
    val intro: String,                    // 1–2 sentence summary
    val body: String,                     // long-form markdown article
    val aliases: List<String> = emptyList() // other names for exactly this concept
)

/**
 * Builds the system prompt for writing a concept page: a full, advanced-level treatment,
 * as if the user had asked a professor "tell me more about [concept]".
 * Deliberately not tailored to the learner's level or progress.
 */
fun buildConceptSystemPrompt(project: Project?): String {
    val sb = StringBuilder()
    sb.append("You write the article for a concept in a learner's personal knowledge base. ")
    sb.append("They saved this concept because they want to understand it properly. Treat it as if they had asked a knowledgeable professor in the field, \"Tell me more about this.\"\n")
    if (project != null) {
        sb.append("The concept came up while they were studying: ${project.name}. ")
        sb.append("Use this only to pick the right meaning of the term. Do not narrow the article to this topic or simplify it for a beginner.\n")
    }
    sb.append("\n---\n\n")
    sb.append("Your task: write a complete, authoritative article on the concept, at the depth of a good textbook section on that one concept.\n\n")
    sb.append("WHAT TO COVER (use what fits the concept, skip what doesn't apply, add what it needs):\n")
    sb.append("- A precise definition, then the core idea in plain words.\n")
    sb.append("- Why it exists: the problem it solves or the question it answers, and its origin if that makes it clearer.\n")
    sb.append("- How it works: mechanisms, components, steps or rules, with formulas only where the field relies on them (every term explained).\n")
    sb.append("- Variants, types or schools of thought, and how they differ.\n")
    sb.append("- At least one concrete example worked through in detail.\n")
    sb.append("- Where it is used in practice, plus its limits and trade-offs.\n")
    sb.append("- Subtle points and common misconceptions.\n")
    sb.append("- How it relates to and differs from neighbouring concepts.\n\n")
    sb.append("RULES:\n")
    sb.append("1. Write at an advanced level for an intelligent reader. Don't water it down, but go only as technical as understanding the concept needs. Use the field's terminology and define each term the first time it appears.\n")
    sb.append("2. This is one concept, not a whole topic. The article (not counting the summary) should be about 500–1,500 words; let the concept's scope decide where in that range it falls.\n")
    sb.append("3. Use markdown with ## section headings chosen for this concept (not a fixed template), ### subheadings inside large sections, short paragraphs, and lists or tables where they help. Write formulas in plain text or code blocks, not LaTeX. Start straight with the content: no preamble, no sign-off.\n")
    sb.append("4. Only include what you are confident is accurate. If you are not sure about a point, leave it out. Include specific figures, dates, names or studies only when you are confident they are correct; otherwise describe the idea in general terms. Where experts in the field genuinely disagree, say so and briefly set out the main positions.\n")
    sb.append("5. The excerpts showing where the learner came across the concept only tell you which meaning is meant. Don't refer to them in the article.\n")
    sb.append("6. Aliases are other names for exactly this concept: synonyms, abbreviations, acronyms and alternative spellings in common use. Never list related, broader or narrower concepts. If there are none, write \"none\".\n\n")
    sb.append("Respond in this exact format:\n")
    sb.append("ALIASES: [comma-separated aliases, or none]\n")
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
    val aliases = Regex("ALIASES:[ \\t]*(.*)")
        .find(response)?.groupValues?.get(1)
        ?.split(",")?.map { it.trim().trim('.', '"', '[', ']') }
        ?.filter { it.isNotEmpty() && !it.equals("none", ignoreCase = true) }
        .orEmpty()
    val summary = Regex("SUMMARY:\\s*(.+?)(?=ARTICLE:|$)", RegexOption.DOT_MATCHES_ALL)
        .find(response)?.groupValues?.get(1)?.trim().orEmpty()
    val article = Regex("ARTICLE:\\s*(.+)$", RegexOption.DOT_MATCHES_ALL)
        .find(response)?.groupValues?.get(1)?.trim().orEmpty()
    return when {
        summary.isNotEmpty() || article.isNotEmpty() -> GeneratedConcept(summary, article, aliases)
        // Model ignored the format: use the whole reply as the article
        else -> GeneratedConcept(intro = "", body = response.trim())
    }
}

/**
 * Adds generated aliases to the ones already saved, keeping the user's own.
 * Skips the concept's name and anything already listed (case-insensitive).
 */
fun mergeAliases(existing: String, added: List<String>, name: String): String {
    val result = existing.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableList()
    added.forEach { alias ->
        val taken = alias.equals(name.trim(), ignoreCase = true) ||
            result.any { it.equals(alias, ignoreCase = true) }
        if (!taken) result += alias
    }
    return result.joinToString(", ")
}

/**
 * Generates concept pages outside any screen's lifetime, so leaving the page
 * doesn't cancel a generation that's already running.
 */
object ConceptGeneration {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val generating = MutableStateFlow<Set<String>>(emptySet())
    val failed = MutableStateFlow<Set<String>>(emptySet())

    fun start(context: Context, conceptId: String) {
        if (conceptId in generating.value) return
        val appContext = context.applicationContext
        generating.update { it + conceptId }
        failed.update { it - conceptId }
        scope.launch {
            try {
                val concept = getConceptById(appContext, conceptId) ?: return@launch
                val result = generateConcept(
                    appContext, concept.projectId, concept.name, concept.sources.map { it.snippet }
                )
                if (result == null) {
                    failed.update { it + conceptId }
                } else {
                    // Re-read so edits made while generating aren't lost
                    val latest = getConceptById(appContext, conceptId) ?: return@launch
                    val now = System.currentTimeMillis()
                    upsertConcept(
                        appContext,
                        latest.copy(
                            intro = result.intro.ifBlank { latest.intro },
                            body = result.body,
                            aliases = mergeAliases(latest.aliases, result.aliases, latest.name),
                            updatedAt = now,
                            lastTouchedAt = now
                        )
                    )
                }
            } finally {
                generating.update { it - conceptId }
            }
        }
    }

    fun clearFailure(conceptId: String) = failed.update { it - conceptId }
}
