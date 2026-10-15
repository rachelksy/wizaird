package com.wizaird.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Generates a merged learning progress summary by combining:
 *   - the existing learning progress text (so context is never lost)
 *   - the last N stored insights for this project
 *   - the last M chats (flattened to a readable exchange transcript)
 *
 * The result is written back into the project's learningProgress field.
 * Before overwriting, the old value is pushed into learningProgressHistory (capped at 2).
 */

sealed class LearningProgressResult {
    data class Success(val summary: String) : LearningProgressResult()
    data class Error(val message: String) : LearningProgressResult()
}

/**
 * Build the system prompt for the learning progress generator.
 *
 * @param project       The project being summarised.
 * @param recentInsights Last N StoredInsights, newest-first (already sliced by caller).
 * @param recentChats   Last M ChatData entries, newest-first (already sliced by caller).
 */
fun buildLearningProgressPrompt(
    project: Project,
    recentInsights: List<StoredInsight>,
    recentChats: List<ChatData>
): String {
    val sb = StringBuilder()

    sb.append("Topic: ${project.name}.\n")
    sb.append("Goal: ${project.instructions}.\n\n")
    sb.append("PURPOSE: This field records what has been addressed so far in this learning project, and from what angle. ")
    sb.append("It is used to avoid repeating what has already been communicated, and to identify what has been touched on but not fully explored — so the next content can build logically from where things left off. ")
    sb.append("Topics that appear here may still be worth returning to if a different angle or a deeper layer serves the user's goal.\n\n")

    // Existing progress — the model must build ON TOP of this, not replace it
    if (project.learningProgress.isNotBlank()) {
        sb.append("EXISTING LEARNING PROGRESS (keep this as the base — do not discard any of it):\n")
        sb.append(project.learningProgress.trim())
        sb.append("\n\n")
    }

    // Background — tells the model what to treat as prior knowledge vs new learning
    if (project.background.isNotBlank()) {
        sb.append("PRIOR BACKGROUND (pre-existing knowledge — do NOT list this as learned progress):\n")
        sb.append(project.background.trim())
        sb.append("\n\n")
    }

    // Recent insights — ordered oldest → newest so the model reads them chronologically
    if (recentInsights.isNotEmpty()) {
        sb.append("RECENT INSIGHTS (oldest first):\n")
        recentInsights.reversed().forEachIndexed { index, insight ->
            sb.append("${index + 1}. ${insight.text.trim()}\n")
        }
        sb.append("\n")
    }

    // Recent chats — summarised as exchanges
    if (recentChats.isNotEmpty()) {
        sb.append("RECENT CHAT EXCHANGES (oldest first):\n")
        recentChats.reversed().forEach { chat ->
            if (chat.messages.isNotEmpty()) {
                if (chat.title.isNotBlank()) {
                    sb.append("Chat: \"${chat.title}\"\n")
                }
                // Only include user questions and AI answers — skip empty messages
                chat.messages.filter { it.text.isNotBlank() }.forEach { msg ->
                    val role = if (msg.sender == MessageSender.USER) "User" else "AI"
                    sb.append("  $role: ${msg.text.trim()}\n")
                }
                sb.append("\n")
            }
        }
    }

    sb.append("---\n\n")
    sb.append("YOUR TASK: Write an updated progress record.\n\n")
    sb.append("RULES:\n")
    sb.append("1. Start from the EXISTING LEARNING PROGRESS above — update it, do not replace it.\n")
    sb.append("2. For each topic, describe what was actually addressed and from what angle — what was covered and what was not — so the next generation can reason about what the logical next step would be. Do not use abstract labels like 'basic' or 'intermediate'. Be concrete about what ground was covered.\n")
    sb.append("3. Add any new topics or threads from the recent insights and chats that are not yet captured.\n")
    sb.append("4. If a topic was revisited from a new angle or taken further, update its entry to reflect what was actually covered this time.\n")
    sb.append("5. Do NOT include anything from the PRIOR BACKGROUND — that is pre-existing knowledge, not progress made in this project.\n")
    sb.append("6. Do NOT overlap with the Background field. This field describes what was explored in this project, not what the user already knew coming in.\n")
    sb.append("7. Be specific and concrete. Someone reading this should be able to determine what has been covered and what logical next steps remain, without re-reading every insight and chat.\n")
    sb.append("8. Do not add preamble, meta-commentary, or section headings. Write only the progress record.\n")

    return sb.toString()
}

/**
 * Call the AI to generate an updated learning progress summary, then save it.
 *
 * On success:
 *   - Pushes the current learningProgress into learningProgressHistory (capped at 2 entries).
 *   - Updates the project's learningProgress with the new summary.
 *   - Saves progressInsightCount and progressChatCount if they changed.
 *
 * Returns the new summary string on success, or an error message.
 */
suspend fun generateLearningProgress(
    context: Context,
    project: Project,
    settings: AiSettings,
    insightCount: Int = project.progressInsightCount,
    chatCount: Int = project.progressChatCount
): LearningProgressResult = withContext(Dispatchers.IO) {
    return@withContext try {
        println("=== LEARNING PROGRESS GENERATION START ===")
        println("Project: ${project.name}, insightCount=$insightCount, chatCount=$chatCount")

        if (settings.apiKey.isBlank()) {
            return@withContext LearningProgressResult.Error("API key not configured. Check settings.")
        }

        // Fetch insights — storedInsightsFlow is already sorted newest-first
        val allInsights = context.dataStore.data.first().let { prefs ->
            val json = prefs[androidx.datastore.preferences.core.stringPreferencesKey("stored_insights")]
                ?: return@let emptyList<StoredInsight>()
            val type = object : com.google.gson.reflect.TypeToken<List<StoredInsight>>() {}.type
            val all: List<StoredInsight> = com.google.gson.Gson().fromJson(json, type) ?: emptyList()
            all.filter { it.projectId == project.id }.sortedByDescending { it.createdAt }
        }
        val recentInsights = allInsights.take(insightCount)
        println("Fetched ${recentInsights.size} insights (of ${allInsights.size} total)")

        // Fetch chats — chatsFlow is already sorted newest-first
        val allChats = context.dataStore.data.first().let { prefs ->
            val json = prefs[androidx.datastore.preferences.core.stringPreferencesKey("chats")]
                ?: return@let emptyList<ChatData>()
            val type = object : com.google.gson.reflect.TypeToken<List<ChatData>>() {}.type
            val all: List<ChatData> = com.google.gson.Gson().fromJson(json, type) ?: emptyList()
            all.filter { it.projectId == project.id }.sortedByDescending { it.createdAt }
        }
        val recentChats = allChats.take(chatCount)
        println("Fetched ${recentChats.size} chats (of ${allChats.size} total)")

        val systemPrompt = buildLearningProgressPrompt(project, recentInsights, recentChats)
        println("Prompt built (${systemPrompt.length} chars)")

        val result = askAi(settings, systemPrompt, "Generate the updated learning progress summary.")
        println("AI response (${result.length} chars): $result")

        if (result.isBlank() || result == "No response.") {
            return@withContext LearningProgressResult.Error("AI returned an empty response. Try again.")
        }

        // Push the current progress into history (cap at 2), then save
        val updatedHistory = if (project.learningProgress.isNotBlank()) {
            (listOf(project.learningProgress) + project.learningProgressHistory).take(2)
        } else {
            project.learningProgressHistory
        }

        val updatedProject = project.copy(
            learningProgress = result,
            learningProgressHistory = updatedHistory,
            progressInsightCount = insightCount,
            progressChatCount = chatCount
        )
        upsertProject(context, updatedProject)
        println("=== LEARNING PROGRESS GENERATION SUCCESS ===")

        LearningProgressResult.Success(result)
    } catch (e: java.net.UnknownHostException) {
        println("LearningProgress ERROR: No internet - ${e.message}")
        LearningProgressResult.Error("No internet connection. Check your network.")
    } catch (e: java.net.SocketTimeoutException) {
        println("LearningProgress ERROR: Timeout - ${e.message}")
        LearningProgressResult.Error("Request timed out. Try again.")
    } catch (e: Exception) {
        println("LearningProgress ERROR: ${e.javaClass.simpleName} - ${e.message}")
        e.printStackTrace()
        LearningProgressResult.Error("Generation failed: ${e.message ?: "Unknown error"}")
    }
}
