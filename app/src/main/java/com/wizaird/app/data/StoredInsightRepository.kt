package com.wizaird.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

data class StoredInsight(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val text: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun formattedCreatedAt(): String {
        val date = Date(createdAt)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return "${dateFormat.format(date)}  •  ${timeFormat.format(date)}"
    }
}

private val KEY_STORED_INSIGHTS = stringPreferencesKey("stored_insights")
private val KEY_PROJECTS = stringPreferencesKey("projects")
private val insightGson = Gson()

/** Flow of all stored insights for a specific project. */
fun storedInsightsFlow(context: Context, projectId: String): Flow<List<StoredInsight>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_STORED_INSIGHTS] ?: return@map emptyList()
        val type = object : TypeToken<List<StoredInsight>>() {}.type
        val all: List<StoredInsight> = insightGson.fromJson(json, type) ?: emptyList()
        all.filter { it.projectId == projectId }
            .sortedByDescending { it.createdAt }
    }

/** Flow of a single stored insight by ID. */
fun storedInsightFlow(context: Context, insightId: String): Flow<StoredInsight?> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_STORED_INSIGHTS] ?: return@map null
        val type = object : TypeToken<List<StoredInsight>>() {}.type
        val all: List<StoredInsight> = insightGson.fromJson(json, type) ?: emptyList()
        all.firstOrNull { it.id == insightId }
    }

/** Save a new insight to permanent storage. */
suspend fun saveInsight(context: Context, projectId: String, text: String, id: String = UUID.randomUUID().toString()) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_STORED_INSIGHTS]
        val type = object : TypeToken<List<StoredInsight>>() {}.type
        val current = if (json != null)
            insightGson.fromJson<List<StoredInsight>>(json, type)?.toMutableList() ?: mutableListOf()
        else
            mutableListOf()
        
        val newInsight = StoredInsight(
            id = id,
            projectId = projectId,
            text = text,
            createdAt = System.currentTimeMillis()
        )
        current.add(newInsight)
        prefs[KEY_STORED_INSIGHTS] = insightGson.toJson(current)
    }
}

/** Delete an insight by ID from both StoredInsight storage and Project.insightHistory. */
suspend fun deleteInsight(context: Context, insightId: String) {
    // First, get current data to find which project this insight belongs to
    val prefs = context.dataStore.data.first()
    
    // Find the insight to get its project ID
    val insightsJson = prefs[KEY_STORED_INSIGHTS]
    val insightType = object : TypeToken<List<StoredInsight>>() {}.type
    val insights: List<StoredInsight> = if (insightsJson != null) {
        insightGson.fromJson(insightsJson, insightType) ?: emptyList()
    } else {
        emptyList()
    }
    
    val insightToDelete = insights.firstOrNull { it.id == insightId }
    
    // Remove from StoredInsight storage
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_STORED_INSIGHTS] ?: return@edit
        val current: MutableList<StoredInsight> = insightGson.fromJson(json, insightType) ?: mutableListOf()
        current.removeAll { it.id == insightId }
        prefs[KEY_STORED_INSIGHTS] = insightGson.toJson(current)
    }
    
    // Remove from Project.insightHistory if we found the insight
    if (insightToDelete != null) {
        val projectsJson = prefs[KEY_PROJECTS]
        if (projectsJson != null) {
            // Use projectsFlow to get properly deserialized projects
            val projectsType = object : TypeToken<List<Project>>() {}.type
            val projects: MutableList<Project> = projectsFlow(context).first().toMutableList()
            
            val projectIndex = projects.indexOfFirst { it.id == insightToDelete.projectId }
            if (projectIndex >= 0) {
                val project = projects[projectIndex]
                val updatedHistory = project.insightHistory.filter { it.id != insightId }
                projects[projectIndex] = project.copy(insightHistory = updatedHistory)
                
                // Save updated projects
                saveProjects(context, projects)
            }
        }
    }
}

/** Move an insight to a different project in both StoredInsight storage and Project.insightHistory. */
suspend fun moveInsightToProject(context: Context, insightId: String, newProjectId: String) {
    // Get current data
    val prefs = context.dataStore.data.first()
    
    // Find the insight
    val insightsJson = prefs[KEY_STORED_INSIGHTS]
    val insightType = object : TypeToken<List<StoredInsight>>() {}.type
    val insights: List<StoredInsight> = if (insightsJson != null) {
        insightGson.fromJson(insightsJson, insightType) ?: emptyList()
    } else {
        emptyList()
    }
    
    val insightToMove = insights.firstOrNull { it.id == insightId } ?: return
    val oldProjectId = insightToMove.projectId
    
    // Update StoredInsight storage - change projectId
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_STORED_INSIGHTS] ?: return@edit
        val current: MutableList<StoredInsight> = insightGson.fromJson(json, insightType) ?: mutableListOf()
        val idx = current.indexOfFirst { it.id == insightId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(projectId = newProjectId)
            prefs[KEY_STORED_INSIGHTS] = insightGson.toJson(current)
        }
    }
    
    // Update Project.insightHistory - remove from old project, add to new project
    val projects: MutableList<Project> = projectsFlow(context).first().toMutableList()
    
    // Find the insight entry in the old project
    val oldProjectIndex = projects.indexOfFirst { it.id == oldProjectId }
    var insightEntry: InsightHistoryEntry? = null
    
    if (oldProjectIndex >= 0) {
        val oldProject = projects[oldProjectIndex]
        insightEntry = oldProject.insightHistory.firstOrNull { it.id == insightId }
        
        // Remove from old project
        val updatedOldHistory = oldProject.insightHistory.filter { it.id != insightId }
        projects[oldProjectIndex] = oldProject.copy(insightHistory = updatedOldHistory)
    }
    
    // Add to new project
    if (insightEntry != null) {
        val newProjectIndex = projects.indexOfFirst { it.id == newProjectId }
        if (newProjectIndex >= 0) {
            val newProject = projects[newProjectIndex]
            val updatedNewHistory = (newProject.insightHistory + insightEntry).takeLast(20)
            projects[newProjectIndex] = newProject.copy(insightHistory = updatedNewHistory)
        }
    }
    
    // Save updated projects
    saveProjects(context, projects)
}
