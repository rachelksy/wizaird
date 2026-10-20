package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

// Every field has a default so Gson can load data saved before a field existed.

/** A new concept the AI found, waiting for the user to generate or ignore it. */
data class ExtractedConcept(
    val name: String = "",
    val summary: String = ""  // one sentence, shown in the popup and saved as the concept's intro
)

/** Result of extracting concepts from an insight. Matches are linked straight away; only new ones are kept here. */
data class ConceptExtraction(
    val extractedAt: Long = System.currentTimeMillis(),
    val pending: List<ExtractedConcept> = emptyList()  // new concepts the user hasn't generated or discarded yet
)

/** What the AI returned, before it is checked against the concept list. */
data class ParsedExtraction(
    val existing: List<String>,          // names the AI says are already in the list
    val new: List<ExtractedConcept>
)

/**
 * Builds the system prompt for extracting concepts from a piece of learning content.
 * The project's concepts and their aliases are listed so the AI can reuse an existing name.
 */
fun buildExtractionSystemPrompt(project: Project?, concepts: List<Concept>): String {
    val sb = StringBuilder()
    sb.append("You identify the concepts covered in a piece of learning content, for a learner's personal knowledge base.\n")
    if (project != null) {
        sb.append("The content comes from their study of: ${project.name}. Use this only to pick the right meaning of terms.\n")
    }
    sb.append("\nA concept is a distinct idea, term, method, principle, model, phenomenon or named thing that the content explains or relies on, and that is worth its own page in a knowledge base.\n")
    if (concepts.isNotEmpty()) {
        sb.append("\nEXISTING CONCEPTS in the knowledge base (name, then other names in brackets):\n")
        concepts.sortedBy { it.name.lowercase() }.forEach { c ->
            val aliases = c.aliasList()
            sb.append("- ${c.name}")
            if (aliases.isNotEmpty()) sb.append(" [${aliases.joinToString(", ")}]")
            sb.append("\n")
        }
    }
    sb.append("\n---\n\n")
    sb.append("Your task: list every concept this content genuinely covers. Use your own judgement on how many.\n\n")
    sb.append("RULES:\n")
    sb.append("1. Include concepts the content explains or depends on. Leave out passing mentions, generic everyday words, and whole topics broader than what the content discusses.\n")
    if (concepts.isNotEmpty()) {
        sb.append("2. If a concept is already in the existing list (under its name or any other name, including singular/plural, abbreviation or spelling variants), output it as EXISTING and copy the list's name exactly.\n")
        sb.append("3. Only treat it as existing when it is exactly the same concept. A broader, narrower or related concept is not a match; output it as NEW if it deserves its own page.\n")
    } else {
        sb.append("2. The knowledge base is empty, so every concept is NEW.\n")
        sb.append("3. (No existing concepts to match.)\n")
    }
    sb.append("4. Name NEW concepts the way the field usually names them: singular, no leading article, no extra description.\n")
    sb.append("5. Give each NEW concept a one-sentence plain definition of at most 25 words.\n")
    sb.append("6. List each concept once.\n\n")
    sb.append("Respond with one line per concept and nothing else:\n")
    sb.append("EXISTING: [exact name from the list]\n")
    sb.append("NEW: [name] | [one-sentence definition]\n")
    sb.append("If the content covers no concepts, respond with: NONE")
    return sb.toString()
}

fun buildExtractionUserPrompt(text: String): String =
    "Content:\n---\n${text.trim()}\n---\n\nList the concepts."

/** Parses the EXISTING / NEW lines. Returns null when the reply is an error. */
fun parseExtraction(response: String): ParsedExtraction? {
    val trimmed = response.trim()
    if (trimmed.isEmpty() || trimmed == "No response." ||
        trimmed.startsWith("API Error") || trimmed.startsWith("Failed to parse API response")
    ) return null
    fun clean(s: String) = s.trim().trim('[', ']', '"', '*', '.', ' ').trim()
    val existing = mutableListOf<String>()
    val new = mutableListOf<ExtractedConcept>()
    trimmed.lines().forEach { raw ->
        // Tolerate list markers and bold labels the model may add
        val line = raw.trim().trimStart('-', '*', '•', ' ').replace("**", "")
        when {
            line.startsWith("EXISTING:", ignoreCase = true) ->
                clean(line.substringAfter(':')).takeIf { it.isNotEmpty() }?.let { existing += it }
            line.startsWith("NEW:", ignoreCase = true) -> {
                val body = line.substringAfter(':')
                val name = clean(body.substringBefore('|'))
                val summary = if ('|' in body) clean(body.substringAfter('|')) else ""
                if (name.isNotEmpty()) new += ExtractedConcept(name, summary)
            }
        }
    }
    return ParsedExtraction(existing, new)
}

sealed class ExtractionStatus {
    object Running : ExtractionStatus()
    object Failed : ExtractionStatus()
    data class Done(val linked: Int, val found: Int) : ExtractionStatus()
}

/**
 * Extracts concepts from insights outside any screen's lifetime.
 * Concepts that already exist are linked straight away (an empty or ghost concept also gets
 * its page written); new ones are saved on the insight for the user to generate or ignore.
 */
object ConceptExtractor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Latest status per insight ID. */
    val status = MutableStateFlow<Map<String, ExtractionStatus>>(emptyMap())

    fun start(context: Context, insightId: String) {
        if (status.value[insightId] == ExtractionStatus.Running) return
        val appContext = context.applicationContext
        status.update { it + (insightId to ExtractionStatus.Running) }
        scope.launch {
            var result: ExtractionStatus = ExtractionStatus.Failed
            try {
                result = extract(appContext, insightId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                status.update { it + (insightId to result) }
            }
        }
    }

    private suspend fun extract(context: Context, insightId: String): ExtractionStatus {
        val insight = allStoredInsightsFlow(context).first().firstOrNull { it.id == insightId }
            ?: return ExtractionStatus.Failed
        val settings = settingsFlow(context).first()
        if (settings.apiKey.isBlank()) return ExtractionStatus.Failed
        val project = projectsFlow(context).first().firstOrNull { it.id == insight.projectId }
        val concepts = conceptsFlow(context, insight.projectId).first()

        val response = askAi(settings, buildExtractionSystemPrompt(project, concepts), buildExtractionUserPrompt(insight.text))
        val parsed = parseExtraction(response) ?: return ExtractionStatus.Failed

        // Code decides what matches, whatever the AI labelled it
        val linkIds = linkedSetOf<String>()
        val newOnes = mutableListOf<ExtractedConcept>()
        parsed.existing.forEach { name ->
            val match = concepts.firstOrNull { it.matchesName(name) }
            if (match != null) linkIds += match.id
            else newOnes += ExtractedConcept(name)  // AI claimed a match that isn't there
        }
        parsed.new.forEach { candidate ->
            val match = concepts.firstOrNull { it.matchesName(candidate.name) }
            if (match != null) linkIds += match.id
            else if (newOnes.none { it.name.equals(candidate.name, ignoreCase = true) }) newOnes += candidate
        }

        concepts.filter { it.id in linkIds }.forEach { linkToInsight(context, it.id, insight, it.name) }
        setInsightConceptExtraction(context, insight.id, ConceptExtraction(pending = newOnes))
        return ExtractionStatus.Done(linked = linkIds.size, found = newOnes.size)
    }

    /**
     * Creates the chosen pending concepts, links them to the insight and writes their pages.
     * Pending concepts that weren't chosen are dropped.
     */
    fun generate(context: Context, insightId: String, chosen: List<ExtractedConcept>) {
        val appContext = context.applicationContext
        scope.launch {
            val insight = allStoredInsightsFlow(appContext).first().firstOrNull { it.id == insightId } ?: return@launch
            setInsightConceptExtraction(appContext, insightId, insight.conceptExtraction?.copy(pending = emptyList()))
            chosen.forEach { item ->
                // It may have been created since the extraction ran
                val existing = findDuplicateConcept(appContext, insight.projectId, item.name)
                if (existing != null) {
                    linkToInsight(appContext, existing.id, insight, item.name)
                } else {
                    val now = System.currentTimeMillis()
                    val concept = Concept(
                        id = UUID.randomUUID().toString(),
                        projectId = insight.projectId,
                        name = item.name,
                        intro = item.summary,
                        createdAt = now,
                        updatedAt = now,
                        firstMetAt = now,
                        lastTouchedAt = now,
                        sources = listOf(insightSource(insight, item.name))
                    )
                    upsertConcept(appContext, concept)
                    ConceptGeneration.start(appContext, concept.id)
                }
            }
        }
    }

    /** Drops the pending new concepts without creating any. */
    fun discard(context: Context, insightId: String) {
        val appContext = context.applicationContext
        scope.launch {
            val insight = allStoredInsightsFlow(appContext).first().firstOrNull { it.id == insightId } ?: return@launch
            setInsightConceptExtraction(appContext, insightId, insight.conceptExtraction?.copy(pending = emptyList()))
        }
    }

    /** Links a concept to the insight; a ghost or empty concept also gets its page written. */
    private suspend fun linkToInsight(context: Context, conceptId: String, insight: StoredInsight, term: String) {
        val before = getConceptById(context, conceptId) ?: return
        addSourceToConcept(context, conceptId, insightSource(insight, term))
        if (before.isGhost || before.isEmpty) ConceptGeneration.start(context, conceptId)
    }

    private fun insightSource(insight: StoredInsight, term: String) = ConceptSource(
        type = ConceptSourceType.INSIGHT,
        insightId = insight.id,
        snippet = buildSnippet(insight.text, term)
    )
}
