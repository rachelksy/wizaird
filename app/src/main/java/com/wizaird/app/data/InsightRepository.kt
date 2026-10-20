package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository for generating learning insights for projects.
 */

/**
 * Only a project that has never had an insight gets one automatically;
 * after that the user asks for new insights themselves.
 */
fun shouldGenerateNewInsight(project: Project): Boolean = project.lastInsightTimestamp == 0L

/**
 * Build the system prompt for insight generation.
 */
fun buildInsightPrompt(project: Project): String {
    val sb = StringBuilder()
    
    sb.append("You are generating a microlearning insight about: ${project.name}.\n")
    sb.append("Goal: ${project.instructions}.\n")
    
    if (project.background.isNotBlank()) {
        sb.append("What the user already knows: ${project.background}\n")
    } else {
        sb.append("Assume no prior knowledge. Start from first principles.\n")
    }
    
    if (project.learningProgress.isNotBlank()) {
        sb.append("What has been addressed so far in this project:\n${project.learningProgress}\n")
    }
    
    if (project.insightHistory.isNotEmpty()) {
        sb.append("Recent insights (most recent last):\n")
        project.insightHistory.forEach { entry ->
            sb.append("---\n${entry.text}\n")
        }
    }
    
    // Add current date (use if necessary)
    val currentDate = java.text.SimpleDateFormat("EEEE, MMMM d, yyyy", java.util.Locale.US).format(java.util.Date())
    sb.append("\nToday's date: $currentDate (use only if relevant to the learning content)\n")
    
    sb.append("\n---\n\n")
    sb.append("Your task: Write one microlearning insight.\n\n")
    
    // Add emphasis if prioritizeInstructions is enabled
    if (project.prioritizeInstructions && project.instructions.isNotBlank()) {
        sb.append("IMPORTANT: The user has specified custom instructions in \"Their goal\" above. These instructions should be given priority and followed closely when generating the insight.\n\n")
    }
    
    sb.append("RULES:\n")
    sb.append("1. One clear focus — a single idea, pattern, or insight the user can walk away having genuinely absorbed. Supporting ideas are fine if they serve that focus. Competing ideas are not.\n")
    sb.append("2. Typically 150–250 words, but use whatever length is needed to fully explain the idea. If clarity requires more space, extend up to 500 words. Don't let word count constrain what needs to be said.\n")
    sb.append("3. Tone: clear, direct, substantive.\n")
    sb.append("4. Use whatever format best serves the content — prose, a short list, a comparison. Do not default to any one format.\n")
    sb.append("5. Use the learning progress to reason about what has already been addressed and from what angle — then decide what the next logical step is given the goal. Topics that appear in the progress are not off-limits. They may still be worth returning to if a different angle or a deeper layer serves the goal. Only skip a topic if what needs to be said about it has genuinely already been said.\n")
    sb.append("6. Do not end with a question. End when the idea is complete.\n\n")
    sb.append("Write only the insight. No preamble, no labels.")
    
    return sb.toString()
}

/**
 * Result of insight generation.
 */
sealed class InsightResult {
    data class Success(val insight: String, val thinkingContent: String? = null) : InsightResult()
    data class Error(val message: String) : InsightResult()
}

/**
 * Generate a new insight for the given project.
 * Returns InsightResult with either success or error details.
 */
suspend fun generateInsight(
    context: Context,
    project: Project,
    settings: AiSettings
): InsightResult = withContext(Dispatchers.IO) {
    return@withContext try {
        println("=== INSIGHT GENERATION START ===")
        println("Project: ${project.name}")
        println("Project ID: ${project.id}")
        println("Provider: ${settings.provider}")
        println("Model: ${settings.model}")
        println("API Key length: ${settings.apiKey.length}")
        
        // Validate settings
        if (settings.apiKey.isBlank()) {
            println("ERROR: API key is blank")
            println("=== INSIGHT GENERATION FAILED ===")
            return@withContext InsightResult.Error("API key not configured. Check settings.")
        }
        
        val systemPrompt = buildInsightPrompt(project)
        println("System prompt built (${systemPrompt.length} chars)")
        println("--- SYSTEM PROMPT START ---")
        println(systemPrompt)
        println("--- SYSTEM PROMPT END ---")
        
        println("Calling askAi for insight...")
        val insightResponse = askAiWithThinking(settings, systemPrompt, "Generate an insight.")
        val insight = insightResponse.answer
        println("AI response received (${insight.length} chars)")
        println("--- INSIGHT START ---")
        println(insight)
        println("--- INSIGHT END ---")
        if (insightResponse.thinkingContent != null) {
            println("--- INSIGHT THINKING (${insightResponse.thinkingContent.length} chars) ---")
        }
        
        // Check if we got a valid response
        if (insight.isBlank() || insight == "No response.") {
            println("ERROR: Invalid response from AI")
            println("=== INSIGHT GENERATION FAILED ===")
            return@withContext InsightResult.Error("AI returned empty response. Try again.")
        }
        
        // Generate ID for this insight
        val insightId = java.util.UUID.randomUUID().toString()
        
        // Update project with new insight and timestamp
        // Keep last 20 insights in history, oldest first
        // Re-read the project: it may have been edited while the insight was generating
        val latest = projectsFlow(context).first().firstOrNull { it.id == project.id } ?: project
        val newEntry = InsightHistoryEntry(id = insightId, text = insight)
        val updatedHistory = (latest.insightHistory + newEntry).takeLast(20)

        val updatedProject = latest.copy(
            lastInsightText = insight,
            lastInsightTimestamp = System.currentTimeMillis(),
            insightHistory = updatedHistory
        )
        upsertProject(context, updatedProject)
        println("Project updated with new insight (history size: ${updatedHistory.size})")
        
        // Save to permanent storage with the same ID
        saveInsight(context, project.id, insight, insightId, insightResponse.thinkingContent)
        println("Insight saved to permanent storage with ID: $insightId")
        
        println("=== INSIGHT GENERATION SUCCESS ===")
        
        InsightResult.Success(insight, insightResponse.thinkingContent)
    } catch (e: java.net.UnknownHostException) {
        println("=== INSIGHT GENERATION FAILED ===")
        println("Error: UnknownHostException - No internet connection")
        println("Message: ${e.message}")
        e.printStackTrace()
        InsightResult.Error("No internet connection. Check your network.")
    } catch (e: java.net.SocketTimeoutException) {
        println("=== INSIGHT GENERATION FAILED ===")
        println("Error: SocketTimeoutException - Request timed out")
        println("Message: ${e.message}")
        e.printStackTrace()
        InsightResult.Error("Request timed out. Try again.")
    } catch (e: Exception) {
        println("=== INSIGHT GENERATION FAILED ===")
        println("Error type: ${e.javaClass.simpleName}")
        println("Error message: ${e.message}")
        println("Stack trace:")
        e.printStackTrace()
        InsightResult.Error("API call failed: ${e.message ?: "Unknown error"}")
    }
}

/**
 * Runs insight generation outside any screen's lifetime, so switching projects
 * or leaving the home screen doesn't cancel a generation that's already running.
 */
object InsightGeneration {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** IDs of projects with an insight being generated. */
    val generating = MutableStateFlow<Set<String>>(emptySet())

    /** Error from the last failed attempt per project, cleared when a new attempt starts. */
    val errors = MutableStateFlow<Map<String, String>>(emptyMap())

    private val _finished = MutableSharedFlow<Pair<String, InsightResult>>(extraBufferCapacity = 8)
    /** Emits (projectId, result) when a generation finishes. */
    val finished: SharedFlow<Pair<String, InsightResult>> = _finished

    /** Starts generating an insight for [project]. Does nothing if one is already running for it. */
    fun start(context: Context, project: Project) {
        if (project.id in generating.value) return
        val appContext = context.applicationContext
        generating.update { it + project.id }
        errors.update { it - project.id }
        scope.launch {
            var result: InsightResult = InsightResult.Error("Generation stopped.")
            try {
                result = generateInsight(appContext, project, settingsFlow(appContext).first())
            } finally {
                (result as? InsightResult.Error)?.let { err -> errors.update { it + (project.id to err.message) } }
                // Clear the loading state before announcing, so the bubble shows the result straight away
                generating.update { it - project.id }
                _finished.tryEmit(project.id to result)
            }
        }
    }
}
