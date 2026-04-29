package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
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
    
    // TODO: Add last 10 insights when memory system is implemented
    // if (last10Insights.isNotEmpty()) {
    //     sb.append("Recent insights (most recent last):\n")
    //     last10Insights.forEach { sb.append("- $it\n") }
    // }
    
    sb.append("\n---\n\n")
    sb.append("Your task: Write one microlearning insight.\n\n")
    sb.append("RULES:\n")
    sb.append("1. One clear focus — a single idea, pattern, or insight the user can walk away having genuinely absorbed. Supporting ideas are fine if they serve that focus. Competing ideas are not.\n")
    sb.append("2. 150–250 words.\n")
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
        
        println("Calling askAi...")
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
        
        // Update project with new insight and timestamp
        val updatedProject = project.copy(
            lastInsightText = insight,
            lastInsightTimestamp = System.currentTimeMillis()
        )
        upsertProject(context, updatedProject)
        println("Project updated successfully")
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
