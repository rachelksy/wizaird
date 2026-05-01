package com.wizaird.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import kotlinx.coroutines.Dispatchers
import java.lang.reflect.Type
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Represents an insight in the project's history with its ID for tracking.
 * This allows us to match insights between Project.insightHistory and StoredInsight.
 */
data class InsightHistoryEntry(
    val id: String,
    val text: String
)

/**
 * Custom deserializer for Project that handles migration of insightHistory field
 * from old List<String> format to new List<InsightHistoryEntry> format.
 */
class ProjectDeserializer : JsonDeserializer<Project> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): Project {
        val jsonObject = json.asJsonObject
        
        // Deserialize all fields normally except insightHistory
        val id = jsonObject.get("id")?.asString ?: UUID.randomUUID().toString()
        val name = jsonObject.get("name")?.asString ?: ""
        val instructions = jsonObject.get("instructions")?.asString ?: ""
        val background = jsonObject.get("background")?.asString ?: ""
        val learningProgress = jsonObject.get("learningProgress")?.asString ?: ""
        val picturePath = jsonObject.get("picturePath")?.asString ?: ""
        val chatCount = jsonObject.get("chatCount")?.asInt ?: 0
        val lastInsightTimestamp = jsonObject.get("lastInsightTimestamp")?.asLong ?: 0L
        val lastInsightText = jsonObject.get("lastInsightText")?.asString ?: ""
        val pinnedInsight = jsonObject.get("pinnedInsight")?.asBoolean ?: false
        val order = jsonObject.get("order")?.asInt ?: 0
        val queuedInsight = jsonObject.get("queuedInsight")?.asString ?: ""
        
        // Handle insightHistory migration
        val insightHistory = mutableListOf<InsightHistoryEntry>()
        val historyElement = jsonObject.get("insightHistory")
        
        if (historyElement != null && historyElement.isJsonArray) {
            val historyArray = historyElement.asJsonArray
            
            for (element in historyArray) {
                when {
                    element.isJsonPrimitive && element.asJsonPrimitive.isString -> {
                        // Old format: plain string - migrate to new format
                        insightHistory.add(InsightHistoryEntry(
                            id = UUID.randomUUID().toString(),
                            text = element.asString
                        ))
                    }
                    element.isJsonObject -> {
                        // New format: object with id and text
                        val obj = element.asJsonObject
                        val entryId = obj.get("id")?.asString ?: UUID.randomUUID().toString()
                        val entryText = obj.get("text")?.asString ?: ""
                        insightHistory.add(InsightHistoryEntry(id = entryId, text = entryText))
                    }
                }
            }
        }
        
        return Project(
            id = id,
            name = name,
            instructions = instructions,
            background = background,
            learningProgress = learningProgress,
            picturePath = picturePath,
            chatCount = chatCount,
            lastInsightTimestamp = lastInsightTimestamp,
            lastInsightText = lastInsightText,
            pinnedInsight = pinnedInsight,
            insightHistory = insightHistory,
            order = order,
            queuedInsight = queuedInsight
        )
    }
}

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val instructions: String = "",
    val background: String = "",
    val learningProgress: String = "",
    val picturePath: String = "",   // absolute path to file in internal storage, empty = none
    val chatCount: Int = 0,
    val lastInsightTimestamp: Long = 0L,
    val lastInsightText: String = "",
    val pinnedInsight: Boolean = false,  // If true, auto-generation is paused for this project
    val insightHistory: List<InsightHistoryEntry> = emptyList(),  // Last 20 insights with IDs, oldest first
    val order: Int = 0,  // Display order, lower numbers appear first
    val queuedInsight: String = ""  // Pre-generated insight ready to show instantly
)

private val KEY_PROJECTS = stringPreferencesKey("projects")
private val projectGson = GsonBuilder()
    .registerTypeAdapter(Project::class.java, ProjectDeserializer())
    .create()

fun projectsFlow(context: Context): Flow<List<Project>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_PROJECTS] ?: return@map emptyList()
        val type = object : TypeToken<List<Project>>() {}.type
        val projects: List<Project> = projectGson.fromJson(json, type) ?: emptyList()
        // Sort by order field
        projects.sortedBy { it.order }
    }

suspend fun saveProjects(context: Context, projects: List<Project>) {
    context.dataStore.edit { prefs ->
        prefs[KEY_PROJECTS] = projectGson.toJson(projects)
    }
}

suspend fun upsertProject(context: Context, project: Project) {
    val current = mutableListOf<Project>()
    val prefs = context.dataStore.data.first()
    val json = prefs[KEY_PROJECTS]
    if (json != null) {
        val type = object : TypeToken<List<Project>>() {}.type
        current.addAll(projectGson.fromJson(json, type) ?: emptyList())
    }
    val idx = current.indexOfFirst { it.id == project.id }
    if (idx >= 0) {
        current[idx] = project
    } else {
        // New project - assign it the highest order value
        val maxOrder = current.maxOfOrNull { it.order } ?: -1
        current.add(project.copy(order = maxOrder + 1))
    }
    saveProjects(context, current)
}

/** Copies the picked URI into internal storage and returns the local file path. */
suspend fun copyPictureToInternal(context: Context, uri: Uri): String =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "project_pictures").also { it.mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        dest.absolutePath
    }

/** Delete a project by ID. */
suspend fun deleteProject(context: Context, projectId: String) {
    val prefs = context.dataStore.data.first()
    val json = prefs[KEY_PROJECTS] ?: return
    val type = object : TypeToken<List<Project>>() {}.type
    val current: MutableList<Project> = projectGson.fromJson(json, type) ?: mutableListOf()
    
    // Delete the project's picture file if it exists
    val project = current.firstOrNull { it.id == projectId }
    if (project != null && project.picturePath.isNotEmpty()) {
        withContext(Dispatchers.IO) {
            File(project.picturePath).delete()
        }
    }
    
    current.removeAll { it.id == projectId }
    saveProjects(context, current)
}

/** Reorder projects by moving a project from one position to another. */
suspend fun reorderProjects(context: Context, fromIndex: Int, toIndex: Int) {
    try {
        println("ProjectRepository: reorderProjects called - fromIndex=$fromIndex, toIndex=$toIndex")
        
        val prefs = context.dataStore.data.first()
        val json = prefs[KEY_PROJECTS]
        if (json == null) {
            println("ProjectRepository: No projects found in datastore")
            return
        }
        
        val type = object : TypeToken<List<Project>>() {}.type
        val current: MutableList<Project> = projectGson.fromJson(json, type) ?: mutableListOf()
        
        println("ProjectRepository: Current projects count=${current.size}")
        
        // Sort by current order
        val sorted = current.sortedBy { it.order }.toMutableList()
        
        if (fromIndex < 0 || fromIndex >= sorted.size) {
            println("ProjectRepository: Invalid fromIndex=$fromIndex, size=${sorted.size}")
            return
        }
        
        if (toIndex < 0 || toIndex >= sorted.size) {
            println("ProjectRepository: Invalid toIndex=$toIndex, size=${sorted.size}")
            return
        }
        
        if (fromIndex == toIndex) {
            println("ProjectRepository: fromIndex equals toIndex, no reorder needed")
            return
        }
        
        println("ProjectRepository: Moving project from $fromIndex to $toIndex")
        
        // Move the item
        val item = sorted.removeAt(fromIndex)
        sorted.add(toIndex, item)
        
        // Reassign order values
        val reordered = sorted.mapIndexed { index, project ->
            project.copy(order = index)
        }
        
        println("ProjectRepository: Saving reordered projects")
        saveProjects(context, reordered)
        println("ProjectRepository: Reorder complete")
    } catch (e: Exception) {
        println("ProjectRepository: Error in reorderProjects - ${e.message}")
        e.printStackTrace()
    }
}
