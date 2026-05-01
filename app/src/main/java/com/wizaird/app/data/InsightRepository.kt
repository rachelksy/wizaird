package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Repository for generating learning insights for projects.
 */

private const val SESSION_DURATION_MS = 30 * 60 * 1000L // 30 minutes

/**
 * Check if a new insight should be generated based on the 30-minute session timer.
 * Returns false if the insight is pinned (user wants to keep it).
 */
fun shouldGenerateNewInsight(project: Project): Boolean {
    // If insight is pinned, don't auto-generate
    if (project.pinnedInsight) return false
    
    if (project.lastInsightTimestamp == 0L) return true
    val now = System.currentTimeMillis()
    return (now - project.lastInsightTimestamp) >= SESSION_DURATION_MS
}

/**
 * Use the queued insight if available, otherwise generate a new one.
 * When using queued insight, generates a new queued insight in the background.
 * Returns InsightResult with either success or error details.
 */
suspend fun getNextInsight(
    context: Context,
    project: Project,
    settings: AiSettings
): InsightResult = withContext(Dispatchers.IO) {
    return@withContext try {
        // Check if we have a queued insight
        if (project.queuedInsight.isNotEmpty()) {
            println("=== USING QUEUED INSIGHT ===")
            println("Project: ${project.name}")
            
            // Use the queued insight as the current insight
            val insight = project.queuedInsight
            val insightId = java.util.UUID.randomUUID().toString()
            
            // Add to history
            val newEntry = InsightHistoryEntry(id = insightId, text = insight)
            val updatedHistory = (project.insightHistory + newEntry).takeLast(20)
            
            // Update project - clear queued insight temporarily
            val updatedProject = project.copy(
                lastInsightText = insight,
                lastInsightTimestamp = System.currentTimeMillis(),
                insightHistory = updatedHistory,
                queuedInsight = ""  // Will be filled by background generation
            )
            upsertProject(context, updatedProject)
            
            // Save to permanent storage
            saveInsight(context, project.id, insight, insightId)
            println("Queued insight promoted to current insight")
            
            // Generate new queued insight in background (non-blocking)
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                try {
                    println("Generating new queued insight in background...")
                    val queuedPrompt = buildInsightPrompt(updatedProject)
                    val newQueuedInsight = askAi(settings, queuedPrompt, "Generate an insight.")
                    
                    if (newQueuedInsight.isNotBlank() && newQueuedInsight != "No response.") {
                        // Update project with new queued insight
                        // Use updatedProject as base to avoid race condition
                        val finalProject = updatedProject.copy(queuedInsight = newQueuedInsight)
                        upsertProject(context, finalProject)
                        println("New queued insight generated and saved")
                    }
                } catch (e: Exception) {
                    println("WARNING: Failed to generate new queued insight - ${e.message}")
                }
            }
            
            println("=== QUEUED INSIGHT SUCCESS ===")
            InsightResult.Success(insight)
        } else {
            // No queued insight, generate normally (with queued)
            println("=== NO QUEUED INSIGHT, GENERATING NEW ===")
            generateInsight(context, project, settings, generateQueued = true)
        }
    } catch (e: Exception) {
        println("=== GET NEXT INSIGHT FAILED ===")
        println("Error: ${e.message}")
        e.printStackTrace()
        InsightResult.Error("Failed to get insight: ${e.message ?: "Unknown error"}")
    }
}

/**
 * Build the system prompt for insight generation.
 */
fun buildInsightPrompt(project: Project): String {
    val sb = StringBuilder()
    
    sb.append("You are a learning guide helping the user build genuine understanding of: ${project.name}.\n")
    sb.append("Their goal: ${project.instructions}.\n")
    
    if (project.background.isNotBlank()) {
        sb.append("What they already know: ${project.background}\n")
    } else {
        sb.append("Assume no prior knowledge. Start from first principles.\n")
    }
    
    if (project.learningProgress.isNotBlank()) {
        sb.append("What they have learned so far:\n${project.learningProgress}\n")
    }
    
    if (project.insightHistory.isNotEmpty()) {
        sb.append("Recent insights (most recent last):\n")
        project.insightHistory.forEach { entry ->
            sb.append("---\n${entry.text}\n")
        }
    }
    
    sb.append("\n---\n\n")
    sb.append("Your task: Write one microlearning insight.\n\n")
    sb.append("RULES:\n")
    sb.append("1. One clear focus — a single idea, pattern, or insight the user can walk away having genuinely absorbed. Supporting ideas are fine if they serve that focus. Competing ideas are not.\n")
    sb.append("2. Typically 150–250 words, but use whatever length is needed to fully explain the idea. If clarity requires more space, extend up to 500 words. Don't let word count constrain what needs to be said.\n")
    sb.append("3. Tone: clear, direct, substantive.\n")
    sb.append("4. Use whatever format best serves the content — prose, a short list, a comparison. Do not default to any one format.\n")
    sb.append("5. Do not repeat ground already covered in the learning progress or recent insights. You may revisit a topic if you are approaching it from a genuinely different angle that adds new understanding.\n")
    sb.append("6. Do not end with a question. End when the idea is complete.\n\n")
    sb.append("Write only the insight. No preamble, no labels.")
    
    return sb.toString()
}

/**
 * Result of insight generation.
 */
sealed class InsightResult {
    data class Success(val insight: String) : InsightResult()
    data class Error(val message: String) : InsightResult()
}

/**
 * Generate a new insight for the given project.
 * If generateQueued is true, generates 2 insights: one to show and one to queue.
 * Returns InsightResult with either success or error details.
 */
suspend fun generateInsight(
    context: Context,
    project: Project,
    settings: AiSettings,
    generateQueued: Boolean = true
): InsightResult = withContext(Dispatchers.IO) {
    return@withContext try {
        println("=== INSIGHT GENERATION START ===")
        println("Project: ${project.name}")
        println("Project ID: ${project.id}")
        println("Provider: ${settings.provider}")
        println("Model: ${settings.model}")
        println("API Key length: ${settings.apiKey.length}")
        println("Generate queued: $generateQueued")
        
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
        
        println("Calling askAi for main insight...")
        val insight = askAi(settings, systemPrompt, "Generate an insight.")
        println("AI response received (${insight.length} chars)")
        println("--- INSIGHT START ---")
        println(insight)
        println("--- INSIGHT END ---")
        
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
        val newEntry = InsightHistoryEntry(id = insightId, text = insight)
        val updatedHistory = (project.insightHistory + newEntry).takeLast(20)
        
        // Generate queued insight if requested
        var queuedInsightText = ""
        if (generateQueued) {
            println("Generating queued insight...")
            // Update the prompt to include the insight we just generated
            val tempProject = project.copy(
                lastInsightText = insight,
                insightHistory = updatedHistory
            )
            val queuedPrompt = buildInsightPrompt(tempProject)
            
            try {
                queuedInsightText = askAi(settings, queuedPrompt, "Generate an insight.")
                println("Queued insight generated (${queuedInsightText.length} chars)")
                println("--- QUEUED INSIGHT START ---")
                println(queuedInsightText)
                println("--- QUEUED INSIGHT END ---")
                
                // Validate queued insight
                if (queuedInsightText.isBlank() || queuedInsightText == "No response.") {
                    println("WARNING: Invalid queued insight, will skip")
                    queuedInsightText = ""
                }
            } catch (e: Exception) {
                println("WARNING: Failed to generate queued insight - ${e.message}")
                // Continue without queued insight
                queuedInsightText = ""
            }
        }
        
        val updatedProject = project.copy(
            lastInsightText = insight,
            lastInsightTimestamp = System.currentTimeMillis(),
            insightHistory = updatedHistory,
            queuedInsight = queuedInsightText
        )
        upsertProject(context, updatedProject)
        println("Project updated with new insight (history size: ${updatedHistory.size})")
        if (queuedInsightText.isNotEmpty()) {
            println("Queued insight saved to project")
        }
        
        // Save to permanent storage with the same ID
        saveInsight(context, project.id, insight, insightId)
        println("Insight saved to permanent storage with ID: $insightId")
        
        println("=== INSIGHT GENERATION SUCCESS ===")
        
        InsightResult.Success(insight)
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
