package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.flow.first

/**
 * Practice mode: a chat that starts with the AI asking a question about a source
 * (an insight, a concept or an AI chat reply), then gives feedback on the user's answers.
 */

// ── Origins ───────────────────────────────────────────────────────────────────

fun originFromInsight(insight: StoredInsight): ChatOrigin =
    ChatOrigin(type = ChatOriginType.INSIGHT, insightId = insight.id, text = insight.text)

fun originFromConcept(concept: Concept): ChatOrigin {
    val text = buildString {
        append("Concept: ${concept.name}\n")
        if (concept.aliases.isNotBlank()) append("Also known as: ${concept.aliases}\n")
        if (concept.intro.isNotBlank()) append("Summary: ${concept.intro}\n")
        if (concept.body.isNotBlank()) append("\n${concept.body}")
    }.trim()
    return ChatOrigin(type = ChatOriginType.CONCEPT, conceptId = concept.id, label = concept.name, text = text)
}

fun originFromMessage(chat: ChatData, message: ChatMessage): ChatOrigin =
    ChatOrigin(
        type = ChatOriginType.CHAT_MESSAGE,
        chatId = chat.id,
        messageId = message.id,
        label = chat.title,
        text = message.text
    )

/** A concept can be practised once it has something to practise from. */
val Concept.canPractice: Boolean get() = intro.isNotBlank() || body.isNotBlank()

/**
 * The chat's origin. Chats made before origins existed only have an insight ID, or nothing:
 * an insight chat starts with the insight's text, so it's matched by that.
 */
fun resolveChatOrigin(chat: ChatData, insights: List<StoredInsight>): ChatOrigin? {
    chat.origin?.let { return it }
    chat.insightId?.let { id ->
        return ChatOrigin(type = ChatOriginType.INSIGHT, insightId = id, text = insights.firstOrNull { it.id == id }?.text)
    }
    val first = chat.messages.firstOrNull()?.takeIf { it.sender == MessageSender.AI } ?: return null
    val match = insights.firstOrNull { it.projectId == chat.projectId && it.text.trim() == first.text.trim() } ?: return null
    return originFromInsight(match)
}

// ── Creating a practice chat ──────────────────────────────────────────────────

/** Saves an empty practice chat; the chat screen asks the first question. Returns the chat. */
suspend fun createPracticeChat(context: Context, projectId: String, origin: ChatOrigin, type: PracticeType): ChatData {
    val chat = ChatData(
        projectId = projectId,
        title = "Practice",
        origin = origin,
        practice = PracticeInfo(type = type)
    )
    upsertChat(context, chat)
    // A practice chat on a concept shows up in the concept page's Chats section
    if (origin.type == ChatOriginType.CONCEPT && origin.conceptId != null) {
        addSourceToConcept(context, origin.conceptId, ConceptSource(type = ConceptSourceType.CHAT, chatId = chat.id, snippet = chat.title))
    }
    return chat
}

/** Opening questions of earlier practice chats on the same source (latest [limit], oldest first). */
suspend fun earlierPracticeQuestions(context: Context, origin: ChatOrigin, excludeChatId: String, limit: Int = 50): List<String> =
    allChatsFlow(context).first()
        .filter { it.isPractice && it.id != excludeChatId && origin.sameSourceAs(it.origin) }
        .sortedBy { it.createdAt }
        .mapNotNull { c -> c.messages.firstOrNull { it.sender == MessageSender.AI }?.text }
        .takeLast(limit)

/** Chat title: "Practice: {concept}" for concepts, otherwise the question's first sentence. */
fun practiceTitle(origin: ChatOrigin, question: String): String {
    origin.label?.takeIf { it.isNotBlank() && origin.type == ChatOriginType.CONCEPT }?.let { return "Practice: $it" }
    val body = question.lines().dropWhile { it.isBlank() || it.trim().startsWith("**") && it.trim().endsWith("**") }
        .joinToString(" ").replace(Regex("[*_`#>]+"), "").trim()
    // First sentence, uncut: the one-line chat card ellipsizes it at the real edge
    val text = body.ifBlank { question }.replace(Regex("[*_`#>]+"), "").trim()
    return Regex("^.+?[.?!](?=\\s|$)").find(text)?.value ?: text.take(120)
}

/** The verdict on the reply's first line (**Correct** / **Partially correct** / **Incorrect**), if any. */
fun parseVerdict(reply: String): PracticeVerdict? {
    val first = reply.lineSequence().firstOrNull { it.isNotBlank() }?.lowercase() ?: return null
    val head = first.replace(Regex("[*_#:]"), " ").trim()
    return when {
        head.startsWith("partially correct") || head.startsWith("partly correct") -> PracticeVerdict.PARTIAL
        head.startsWith("incorrect") -> PracticeVerdict.INCORRECT
        head.startsWith("correct") -> PracticeVerdict.CORRECT
        else -> null
    }
}

// ── Prompts ───────────────────────────────────────────────────────────────────

private fun sourceLabel(origin: ChatOrigin): String = when (origin.type) {
    ChatOriginType.INSIGHT -> "Insight"
    ChatOriginType.CONCEPT -> "Concept: ${origin.label ?: ""}".trim()
    ChatOriginType.CHAT_MESSAGE -> "Chat reply" + (origin.label?.let { " from \"$it\"" } ?: "")
}

private fun StringBuilder.appendProjectContext(project: Project) {
    append("You are running a practice session in a learning app. The user is studying: ${project.name}.\n")
    append("Goal: ${project.instructions}.\n")
    if (project.background.isNotBlank()) {
        append("What the user already knows: ${project.background}\n")
    }
}

/** Prompt A: the opening question. */
fun buildPracticeQuestionPrompt(project: Project, origin: ChatOrigin, type: PracticeType, earlierQuestions: List<String>): String {
    val sb = StringBuilder()
    sb.appendProjectContext(project)
    sb.append("\nThe practice is about this material, which the user has already read:\n")
    sb.append("SOURCE (${sourceLabel(origin)}):\n---\n${origin.text.orEmpty()}\n---\n")
    if (earlierQuestions.isNotEmpty()) {
        sb.append("\nQuestions already asked about this material (oldest first):\n")
        earlierQuestions.forEach { sb.append("---\n$it\n") }
        sb.append("---\n")
    }
    sb.append("\nYour task: ask ONE practice question that makes the user use this material, not just recall it.\n\n")
    if (type == PracticeType.SURPRISE) {
        sb.append("QUESTION TYPE: your choice. Choose the type that best tests this material, and avoid the types and angles of the questions already asked. Never choose Quick check.\n\n")
    } else {
        sb.append("QUESTION TYPE: ${type.label}\n\n")
    }
    sb.append("TYPES:\n")
    sb.append("- Explain it: explain the core idea in their own words to a specific person who fits the goal.\n")
    sb.append("- Apply it: a short, realistic situation from the world of the goal where they must use the idea to decide or act.\n")
    sb.append("- Spot the flaw: a short claim, plan or statement that misuses the idea; they find what's wrong and fix it.\n")
    sb.append("- Compare: how this idea differs from a neighbouring idea it's easy to confuse it with.\n")
    sb.append("- Predict: what happens if something in the situation changes, and why.\n")
    sb.append("- Quick check: a short situation and 3–4 options, exactly one correct. Every wrong option must be a plausible mistake someone learning this would make; no joke options, no \"all/none of the above\". Vary which letter is correct. Label the options A, B, C, D, each on its own line.\n\n")
    sb.append("RULES:\n")
    sb.append("1. Test the main idea of the source, not a minor detail or a term to memorise.\n")
    sb.append("2. Make the situation concrete and specific to the goal. Frame it as an interview or work conversation only if the goal calls for it, and not by default.\n")
    sb.append("3. It must be answerable from the source plus reasoning, in a few sentences. No trick questions.\n")
    sb.append("4. Don't hint at the answer or restate the source.\n")
    sb.append("5. Don't repeat a question already asked, or ask the same thing in different words.\n\n")
    sb.append("Start with the type in bold on its own line (e.g. **Apply it**), then the question. One question only. No preamble.")
    return sb.toString()
}

/** If a model still writes a next question into its feedback, the app splits it off at this line. */
const val NEXT_QUESTION_MARKER = "---NEXT QUESTION---"

private fun followUpTypeRule(type: PracticeType): String =
    if (type == PracticeType.SURPRISE) "of whichever type (Explain it, Apply it, Spot the flaw, Compare, Predict) fits best, varying the types over the session"
    else "of the same type (${type.label})"

/** Prompt B: every reply in a practice chat (replaces the normal chat prompt), and the next question. */
fun buildPracticeChatSystemPrompt(project: Project, origin: ChatOrigin, type: PracticeType): String {
    val sb = StringBuilder()
    sb.appendProjectContext(project)
    sb.append("\nSOURCE the practice is based on (${sourceLabel(origin)}):\n---\n${origin.text.orEmpty()}\n---\n\n")
    sb.append("The conversation is a practice session: you ask practice questions and give feedback, the user answers and can discuss the feedback. The last line of each request tells you what the user's latest message is.\n\n")
    sb.append("WHEN THE USER ANSWERS THE OPEN PRACTICE QUESTION:\n")
    sb.append("1. Start with the verdict in bold on its own line: **Correct**, **Partially correct**, or **Incorrect**, followed by one sentence saying why.\n")
    sb.append("   Judge against what this question asked, not everything there is to know about the topic. A short answer that gets the key idea is **Correct**.\n")
    sb.append("2. What they got right: be specific, and quote their answer where possible.\n")
    sb.append("3. Missing: only what this question needed and they didn't give. Leave this out if nothing is missing.\n")
    sb.append("4. To go further (optional): extra depth worth knowing. This never affects the verdict.\n")
    sb.append("5. A stronger answer, in their own voice. Skip it for **Correct** unless it adds something real.\n")
    sb.append("Then stop. Don't ask another question: the user asks for the next one when they're ready, and may want to discuss the feedback first.\n\n")
    sb.append("FOR A QUICK CHECK (a question with lettered options):\n")
    sb.append("The user may answer with just a letter. The verdict is **Correct** or **Incorrect**. Explain why the correct option is right and why each wrong option is tempting but wrong.\n\n")
    sb.append("WHEN THE USER DISCUSSES (asks about the feedback, pushes back, or goes deeper):\n")
    sb.append("Answer fully, like a good tutor, with no verdict and no new practice question. If they push back and they're right, say so.\n\n")
    sb.append("WHEN ASKED FOR THE NEXT QUESTION:\n")
    sb.append("Write one practice question ${followUpTypeRule(type)}. Make it a step harder if their last verdict was **Correct**, simpler and on the missed part otherwise, and use what came up in the discussion. Start with its type in bold on its own line (e.g. **Apply it**). For a Quick check: 3–4 options, exactly one correct, every wrong option a plausible mistake, vary which letter is correct, each option (A, B, C, D) on its own line. Don't repeat a question already asked. Write only the question.\n\n")
    sb.append("RULES:\n")
    sb.append("- Judge the idea, not the wording. A correct answer that goes beyond the source counts, and if the user is right where the source is incomplete, say so.\n")
    sb.append("- If the user says they don't know or asks for help instead of answering, don't give a verdict. Explain the missing piece and invite them to try again.\n")
    sb.append("- Length: as long as the reply needs to be. No filler.")
    return sb.toString()
}

/** Last line of the request: tells the model whether the user's latest message answers the open question. */
fun practiceTurnInstruction(questionOpen: Boolean): String =
    if (questionOpen) "\n\n(Task: a practice question is open. If the user's latest message answers it, give feedback with a verdict. If it's a question or comment instead, reply without a verdict.)"
    else "\n\n(Task: no practice question is open. The user's latest message is part of the discussion: reply without a verdict and don't ask a new practice question.)"

/** User prompt for writing only the next question (NEXT QUESTION button, or regenerating a question). */
fun buildNextQuestionRequest(conversation: String, replacedQuestions: List<String>): String = buildString {
    append(conversation)
    append("\n\n(Task: the user asked for the next practice question. Write only the question, following WHEN ASKED FOR THE NEXT QUESTION.")
    if (replacedQuestions.isNotEmpty()) {
        append(" It must be different from these questions, which the user replaced:\n")
        replacedQuestions.forEach { append("- ${it.replace("\n", " ")}\n") }
    }
    append(")")
}

/** Splits a practice reply into (feedback, next question). No marker = the whole reply is feedback. */
fun splitPracticeReply(reply: String): Pair<String, String?> {
    val match = Regex("(?m)^\\s*-{2,}\\s*NEXT QUESTION\\s*-{2,}\\s*$").find(reply) ?: return reply.trim() to null
    val feedback = reply.substring(0, match.range.first).trim()
    val question = reply.substring(match.range.last + 1).trim()
    return feedback to question.ifBlank { null }
}

/** This session's score: Correct = 1, Partially correct = ½, Incorrect = 0. Null before the first verdict. */
fun practiceScore(verdicts: List<PracticeVerdict>): Int? {
    if (verdicts.isEmpty()) return null
    val points = verdicts.sumOf { when (it) { PracticeVerdict.CORRECT -> 2; PracticeVerdict.PARTIAL -> 1; PracticeVerdict.INCORRECT -> 0 }.toInt() }
    return Math.round(points * 50.0 / verdicts.size).toInt()
}

/**
 * The chat's markdown joins single line breaks into one paragraph, so the bold type line and
 * Quick check options (A. B. C. D.) would run together. Give each its own paragraph.
 */
fun formatPracticeQuestion(question: String): String =
    question.trim().lines().joinToString("\n") { line ->
        val t = line.trim()
        when {
            Regex("^\\*\\*[^*]+\\*\\*$").matches(t) -> "$t\n"                 // **Apply it**
            Regex("^\\(?[A-Da-d][.):]\\s+.+").matches(t) -> "\n$t"             // A. option
            else -> line
        }
    }.replace(Regex("\n{3,}"), "\n\n").trim()
