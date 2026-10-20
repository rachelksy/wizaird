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

/** One line the AI returned, before it is checked against the concept list. */
data class ParsedConcept(
    val name: String,
    val summary: String,
    val claimedExisting: Boolean  // the AI says it is already in the list
)

/**
 * Builds the system prompt for extracting concepts from a piece of learning content.
 * [isChat]: the content is a chat transcript rather than an insight.
 * The project's concepts and their aliases are listed so the AI can reuse an existing name.
 */
fun buildExtractionSystemPrompt(project: Project?, concepts: List<Concept>, isChat: Boolean): String {
    val sb = StringBuilder()
    sb.append("You identify the concepts covered in a piece of learning content, for a learner's personal knowledge base.\n")
    if (isChat) {
        sb.append("The content is a conversation between the learner (User) and an AI tutor (Assistant). ")
        sb.append("Concepts come from the whole conversation: later answers often clarify, correct or go deeper than earlier ones, so give them as much attention as the opening. ")
        sb.append("The learner's questions show what they were trying to understand.\n")
    } else {
        sb.append("The content is a short explanation (an insight) written for the learner.\n")
    }
    if (project != null) {
        sb.append("The content comes from their study of: ${project.name}. Use this only to pick the right meaning of terms.\n")
    }
    sb.append("\nA concept is a distinct idea, term, method, principle, model, pattern, framework, phenomenon or named thing that the content explains or relies on, and that is worth its own page in a knowledge base. ")
    sb.append("This includes ideas introduced as one side of a contrast, and named parts of a process, system or interface, when the content explains them.\n")
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
    sb.append("Your task: go through the content ${if (isChat) "message by message" else "section by section"} and list every concept it covers. ")
    sb.append("Aim to be complete: the learner should find a page for each idea the content teaches.\n\n")
    sb.append("RULES:\n")
    sb.append("1. Include every concept the content explains or relies on, wherever it appears. Leave out only passing mentions the content never explains, everyday words used in their everyday sense, and whole fields broader than what the content discusses.\n")
    if (concepts.isNotEmpty()) {
        sb.append("2. If a concept is already in the existing list (under its name or any other name, including singular/plural, abbreviation or spelling variants), output it as EXISTING and copy the list's name exactly.\n")
        sb.append("3. Only treat it as existing when it is exactly the same concept. A broader, narrower or related concept is not a match; output it as NEW if it deserves its own page.\n")
    } else {
        sb.append("2. The knowledge base is empty, so every concept is NEW.\n")
        sb.append("3. (No existing concepts to match.)\n")
    }
    sb.append("4. Name NEW concepts the way the field usually names them: singular, no leading article, no extra description. If the content gives its own name to an idea, use that name.\n")
    sb.append("5. Give each NEW concept a one-sentence plain definition of at most 25 words.\n")
    sb.append("6. List each concept once.\n")
    sb.append("7. Order the lines by how central each concept is to the content, most central first: the ideas the content is mainly about before the ones it only uses along the way. EXISTING and NEW lines share this one order.\n\n")
    sb.append("Respond with one line per concept and nothing else:\n")
    sb.append("EXISTING: [exact name from the list]\n")
    sb.append("NEW: [name] | [one-sentence definition]\n")
    sb.append("If the content covers no concepts, respond with: NONE")
    return sb.toString()
}

fun buildExtractionUserPrompt(text: String, isChat: Boolean): String =
    if (isChat) "Conversation:\n---\n${text.trim()}\n---\n\nList the concepts, most central first."
    else "Content:\n---\n${text.trim()}\n---\n\nList the concepts, most central first."

/** Parses the EXISTING / NEW lines, keeping the AI's order. Returns null when the reply is an error. */
fun parseExtraction(response: String): List<ParsedConcept>? {
    val trimmed = response.trim()
    if (trimmed.isEmpty() || trimmed == "No response." ||
        trimmed.startsWith("API Error") || trimmed.startsWith("Failed to parse API response")
    ) return null
    fun clean(s: String) = s.trim().trim('[', ']', '"', '*', '.', ' ').trim()
    val items = mutableListOf<ParsedConcept>()
    trimmed.lines().forEach { raw ->
        // Tolerate list markers, numbering and bold labels the model may add
        val line = raw.trim().trimStart('-', '*', '•', ' ').replace(Regex("^\\d+[.)]\\s*"), "").replace("**", "")
        when {
            line.startsWith("EXISTING:", ignoreCase = true) ->
                clean(line.substringAfter(':')).takeIf { it.isNotEmpty() }?.let { items += ParsedConcept(it, "", claimedExisting = true) }
            line.startsWith("NEW:", ignoreCase = true) -> {
                val body = line.substringAfter(':')
                val name = clean(body.substringBefore('|'))
                val summary = if ('|' in body) clean(body.substringAfter('|')) else ""
                if (name.isNotEmpty()) items += ParsedConcept(name, summary, claimedExisting = false)
            }
        }
    }
    return items
}

sealed class ExtractionStatus {
    object Running : ExtractionStatus()
    object Failed : ExtractionStatus()
    data class Done(val linked: Int, val found: Int) : ExtractionStatus()
}

/**
 * Something concepts can be extracted from: an insight or a whole chat.
 * [source] builds the concept source recorded for a term found in it.
 */
private class ExtractionSubject(
    val id: String,
    val projectId: String,
    val text: String,
    val isChat: Boolean,
    val extraction: ConceptExtraction?,
    val save: suspend (ConceptExtraction?) -> Unit,
    val source: (term: String) -> ConceptSource
)

/** The chat as one transcript, the way it is sent to the AI. */
private fun chatTranscript(chat: ChatData): String =
    chat.messages.joinToString("\n\n") { msg ->
        "${if (msg.sender == MessageSender.USER) "User" else "Assistant"}: ${msg.text.trim()}"
    }

/**
 * Extracts concepts from insights and chats outside any screen's lifetime.
 * Concepts that already exist are linked straight away (an empty or ghost concept also gets
 * its page written); new ones are saved on the insight or chat for the user to generate or ignore.
 * Insights and chats are both addressed by ID (UUIDs, so they never clash).
 */
object ConceptExtractor {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // TEMP (UI review): skip the API. Extraction returns MOCK_CONCEPTS after a short wait, and
    // generating only clears the list — nothing is created or written. Set to false to use the AI.
    private const val MOCK_EXTRACTION = false
    private val MOCK_CONCEPTS = listOf(
        ExtractedConcept("Steerability", "The ability of a user to guide an AI system's output toward what they want without starting over."),
        ExtractedConcept("Human-in-the-loop", "A design pattern where the system pauses at defined checkpoints so a person can review, correct or approve the output before it continues to the next step."),
        ExtractedConcept("Escape hatch", "A way to leave an automated flow and take manual control."),
        ExtractedConcept("Probabilistic output", "Output that can differ between runs of the same input, because the model samples from a distribution of likely next tokens instead of following fixed rules."),
        ExtractedConcept("Golden dataset", "A curated set of inputs paired with ideal outputs, used as the reference when measuring model quality."),
        ExtractedConcept("Evaluation-driven development with human feedback loops", "Building and improving an AI feature by repeatedly scoring it against evals and feeding human corrections back into the dataset."),
        ExtractedConcept("Partial acceptance", "Letting a user keep the good parts of an AI result and fix only the rest."),
        ExtractedConcept("Error recovery", "")
    )

    /** Latest status per insight / chat ID. */
    val status = MutableStateFlow<Map<String, ExtractionStatus>>(emptyMap())

    fun start(context: Context, subjectId: String) {
        if (status.value[subjectId] == ExtractionStatus.Running) return
        val appContext = context.applicationContext
        status.update { it + (subjectId to ExtractionStatus.Running) }
        scope.launch {
            var result: ExtractionStatus = ExtractionStatus.Failed
            try {
                result = extract(appContext, subjectId)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                status.update { it + (subjectId to result) }
            }
        }
    }

    /** Finds the insight or chat with this ID. */
    private suspend fun loadSubject(context: Context, id: String): ExtractionSubject? {
        allStoredInsightsFlow(context).first().firstOrNull { it.id == id }?.let { insight ->
            return ExtractionSubject(
                id = insight.id,
                projectId = insight.projectId,
                text = insight.text,
                isChat = false,
                extraction = insight.conceptExtraction,
                save = { setInsightConceptExtraction(context, insight.id, it) },
                source = { term -> insightSource(insight, term) }
            )
        }
        chatFlow(context, id).first()?.let { chat ->
            return ExtractionSubject(
                id = chat.id,
                projectId = chat.projectId,
                text = chatTranscript(chat),
                isChat = true,
                extraction = chat.conceptExtraction,
                save = { setChatConceptExtraction(context, chat.id, it) },
                source = { term -> chatSource(chat, term) }
            )
        }
        return null
    }

    private suspend fun extract(context: Context, subjectId: String): ExtractionStatus {
        val subject = loadSubject(context, subjectId) ?: return ExtractionStatus.Failed
        if (subject.text.isBlank()) return ExtractionStatus.Done(linked = 0, found = 0)
        if (MOCK_EXTRACTION) {
            kotlinx.coroutines.delay(1500)  // long enough to see the loader
            subject.save(ConceptExtraction(pending = MOCK_CONCEPTS))
            return ExtractionStatus.Done(linked = 0, found = MOCK_CONCEPTS.size)
        }
        val settings = settingsFlow(context).first()
        if (settings.apiKey.isBlank()) return ExtractionStatus.Failed
        val project = projectsFlow(context).first().firstOrNull { it.id == subject.projectId }
        val concepts = conceptsFlow(context, subject.projectId).first()

        val response = askAi(
            settings,
            buildExtractionSystemPrompt(project, concepts, subject.isChat),
            buildExtractionUserPrompt(subject.text, subject.isChat)
        )
        val parsed = parseExtraction(response) ?: return ExtractionStatus.Failed

        // Code decides what matches, whatever the AI labelled it. The AI's order (most central
        // first) is kept, so the popup lists new concepts by importance.
        val linkIds = linkedSetOf<String>()
        val newOnes = mutableListOf<ExtractedConcept>()
        parsed.forEach { item ->
            val match = concepts.firstOrNull { it.matchesName(item.name) }
            when {
                match != null -> linkIds += match.id
                // A claimed match that isn't there becomes new, without a definition
                newOnes.none { it.name.equals(item.name, ignoreCase = true) } -> newOnes += ExtractedConcept(item.name, item.summary)
            }
        }

        concepts.filter { it.id in linkIds }.forEach { link(context, it.id, subject.source(it.name)) }
        subject.save(ConceptExtraction(pending = newOnes))
        return ExtractionStatus.Done(linked = linkIds.size, found = newOnes.size)
    }

    /**
     * Creates the chosen pending concepts, links them to the insight or chat and writes their pages.
     * Pending concepts that weren't chosen are dropped.
     */
    fun generate(context: Context, subjectId: String, chosen: List<ExtractedConcept>) {
        val appContext = context.applicationContext
        scope.launch {
            val subject = loadSubject(appContext, subjectId) ?: return@launch
            subject.save(subject.extraction?.copy(pending = emptyList()))
            if (MOCK_EXTRACTION) return@launch
            chosen.forEach { item ->
                // It may have been created since the extraction ran
                val existing = findDuplicateConcept(appContext, subject.projectId, item.name)
                if (existing != null) {
                    link(appContext, existing.id, subject.source(item.name))
                } else {
                    val now = System.currentTimeMillis()
                    val concept = Concept(
                        id = UUID.randomUUID().toString(),
                        projectId = subject.projectId,
                        name = item.name,
                        intro = item.summary,
                        createdAt = now,
                        updatedAt = now,
                        firstMetAt = now,
                        lastTouchedAt = now,
                        sources = listOf(subject.source(item.name))
                    )
                    upsertConcept(appContext, concept)
                    ConceptGeneration.start(appContext, concept.id)
                }
            }
        }
    }

    /** Drops the pending new concepts without creating any. */
    fun discard(context: Context, subjectId: String) {
        val appContext = context.applicationContext
        scope.launch {
            val subject = loadSubject(appContext, subjectId) ?: return@launch
            subject.save(subject.extraction?.copy(pending = emptyList()))
        }
    }

    /** Links a concept to where it was found; a ghost or empty concept also gets its page written. */
    private suspend fun link(context: Context, conceptId: String, source: ConceptSource) {
        val before = getConceptById(context, conceptId) ?: return
        addSourceToConcept(context, conceptId, source)
        if (before.isGhost || before.isEmpty) ConceptGeneration.start(context, conceptId)
    }

    private fun insightSource(insight: StoredInsight, term: String) = ConceptSource(
        type = ConceptSourceType.INSIGHT,
        insightId = insight.id,
        snippet = buildSnippet(insight.text, term)
    )

    /** Whole-chat link (same kind as chat settings' linked concepts); the snippet comes from the first message naming the term. */
    private fun chatSource(chat: ChatData, term: String): ConceptSource {
        val message = chat.messages.firstOrNull { it.text.contains(term, ignoreCase = true) }
        return ConceptSource(
            type = ConceptSourceType.CHAT,
            chatId = chat.id,
            snippet = message?.let { buildSnippet(it.text, term) } ?: chat.title
        )
    }
}
