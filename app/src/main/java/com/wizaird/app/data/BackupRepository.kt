package com.wizaird.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Complete backup data structure containing all app data.
 */
data class BackupData(
    val version: Int = 1,  // For future compatibility
    val exportDate: String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
    val projects: List<Project> = emptyList(),
    val notes: List<NoteData> = emptyList(),
    val chats: List<ChatData> = emptyList(),
    val storedInsights: List<StoredInsight> = emptyList(),
    val settings: AiSettings = AiSettings(),
    val imageFiles: List<String> = emptyList()  // List of image filenames included in backup
)

private val gson = GsonBuilder()
    .registerTypeAdapter(Project::class.java, ProjectDeserializer())
    .setPrettyPrinting()
    .create()

/**
 * Export all app data to a ZIP file containing:
 * - backup.json: All data in JSON format
 * - images/: Folder with all project images
 * 
 * @param context Application context
 * @param destinationUri URI where the ZIP file should be saved (from SAF)
 * @return Result with success message or error
 */
suspend fun exportBackup(context: Context, destinationUri: Uri): Result<String> = withContext(Dispatchers.IO) {
    try {
        // Collect all data from DataStore
        val prefs = context.dataStore.data.first()
        
        // Parse projects
        val projectsJson = prefs[stringPreferencesKey("projects")]
        val projectsType = object : TypeToken<List<Project>>() {}.type
        val projects: List<Project> = if (projectsJson != null) {
            gson.fromJson(projectsJson, projectsType) ?: emptyList()
        } else {
            emptyList()
        }
        
        // Parse notes
        val notesJson = prefs[stringPreferencesKey("notes")]
        val notesType = object : TypeToken<List<NoteData>>() {}.type
        val notes: List<NoteData> = if (notesJson != null) {
            gson.fromJson(notesJson, notesType) ?: emptyList()
        } else {
            emptyList()
        }
        
        // Parse chats
        val chatsJson = prefs[stringPreferencesKey("chats")]
        val chatsType = object : TypeToken<List<ChatData>>() {}.type
        val chats: List<ChatData> = if (chatsJson != null) {
            gson.fromJson(chatsJson, chatsType) ?: emptyList()
        } else {
            emptyList()
        }
        
        // Parse stored insights
        val insightsJson = prefs[stringPreferencesKey("stored_insights")]
        val insightsType = object : TypeToken<List<StoredInsight>>() {}.type
        val insights: List<StoredInsight> = if (insightsJson != null) {
            gson.fromJson(insightsJson, insightsType) ?: emptyList()
        } else {
            emptyList()
        }
        
        // Parse settings
        val settingsJson = prefs[stringPreferencesKey("ai_settings")]
        val settings: AiSettings = if (settingsJson != null) {
            gson.fromJson(settingsJson, AiSettings::class.java) ?: AiSettings()
        } else {
            AiSettings()
        }
        
        // Collect image files
        val imageFiles = mutableListOf<String>()
        val imagePaths = projects.mapNotNull { project ->
            if (project.picturePath.isNotEmpty() && File(project.picturePath).exists()) {
                project.picturePath
            } else null
        }
        
        // Create backup data structure
        val backupData = BackupData(
            version = 1,
            projects = projects,
            notes = notes,
            chats = chats,
            storedInsights = insights,
            settings = settings,
            imageFiles = imagePaths.map { File(it).name }
        )
        
        // Write to ZIP file
        context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
            ZipOutputStream(outputStream).use { zipOut ->
                // Write backup.json
                val jsonEntry = ZipEntry("backup.json")
                zipOut.putNextEntry(jsonEntry)
                zipOut.write(gson.toJson(backupData).toByteArray())
                zipOut.closeEntry()
                
                // Write image files
                imagePaths.forEach { imagePath ->
                    val imageFile = File(imagePath)
                    if (imageFile.exists()) {
                        val imageEntry = ZipEntry("images/${imageFile.name}")
                        zipOut.putNextEntry(imageEntry)
                        imageFile.inputStream().use { input ->
                            input.copyTo(zipOut)
                        }
                        zipOut.closeEntry()
                    }
                }
            }
        }
        
        val stats = buildString {
            append("Backup created successfully!\n\n")
            append("Exported:\n")
            append("• ${projects.size} projects\n")
            append("• ${notes.size} notes\n")
            append("• ${chats.size} chats\n")
            append("• ${insights.size} insights\n")
            append("• ${imagePaths.size} images\n")
            append("• Settings")
        }
        
        Result.success(stats)
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(Exception("Failed to create backup: ${e.message}"))
    }
}

/**
 * Import all app data from a ZIP backup file.
 * 
 * @param context Application context
 * @param sourceUri URI of the ZIP backup file (from SAF)
 * @param mergeMode If true, merge with existing data. If false, replace all data.
 * @return Result with success message or error
 */
suspend fun importBackup(
    context: Context,
    sourceUri: Uri,
    mergeMode: Boolean = false
): Result<String> = withContext(Dispatchers.IO) {
    try {
        var backupData: BackupData? = null
        val imageFiles = mutableMapOf<String, ByteArray>()
        
        // Read ZIP file
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "backup.json" -> {
                            val jsonContent = zipIn.readBytes().toString(Charsets.UTF_8)
                            backupData = gson.fromJson(jsonContent, BackupData::class.java)
                        }
                        entry.name.startsWith("images/") -> {
                            val fileName = entry.name.substringAfter("images/")
                            imageFiles[fileName] = zipIn.readBytes()
                        }
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        
        if (backupData == null) {
            return@withContext Result.failure(Exception("Invalid backup file: backup.json not found"))
        }
        
        val backup = backupData!!
        
        // Restore image files and update paths
        val imageDir = File(context.filesDir, "project_pictures").also { it.mkdirs() }
        val oldToNewImagePaths = mutableMapOf<String, String>()
        
        imageFiles.forEach { (fileName, bytes) ->
            val destFile = File(imageDir, fileName)
            destFile.writeBytes(bytes)
            // Map old filename to new absolute path
            oldToNewImagePaths[fileName] = destFile.absolutePath
        }
        
        // Update project image paths to new absolute paths
        val updatedProjects = backup.projects.map { project ->
            if (project.picturePath.isNotEmpty()) {
                val fileName = File(project.picturePath).name
                val newPath = oldToNewImagePaths[fileName] ?: ""
                project.copy(picturePath = newPath)
            } else {
                project
            }
        }
        
        // Merge or replace data
        if (mergeMode) {
            // Merge with existing data
            val prefs = context.dataStore.data.first()
            
            // Merge projects (by ID)
            val existingProjectsJson = prefs[stringPreferencesKey("projects")]
            val projectsType = object : TypeToken<List<Project>>() {}.type
            val existingProjects: List<Project> = if (existingProjectsJson != null) {
                gson.fromJson(existingProjectsJson, projectsType) ?: emptyList()
            } else {
                emptyList()
            }
            val mergedProjects = mergeById(existingProjects, updatedProjects) { it.id }
            saveProjects(context, mergedProjects)
            
            // Merge notes (by ID)
            val existingNotesJson = prefs[stringPreferencesKey("notes")]
            val notesType = object : TypeToken<List<NoteData>>() {}.type
            val existingNotes: List<NoteData> = if (existingNotesJson != null) {
                gson.fromJson(existingNotesJson, notesType) ?: emptyList()
            } else {
                emptyList()
            }
            val mergedNotes = mergeById(existingNotes, backup.notes) { it.id }
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("notes")] = gson.toJson(mergedNotes)
            }
            
            // Merge chats (by ID)
            val existingChatsJson = prefs[stringPreferencesKey("chats")]
            val chatsType = object : TypeToken<List<ChatData>>() {}.type
            val existingChats: List<ChatData> = if (existingChatsJson != null) {
                gson.fromJson(existingChatsJson, chatsType) ?: emptyList()
            } else {
                emptyList()
            }
            val mergedChats = mergeById(existingChats, backup.chats) { it.id }
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("chats")] = gson.toJson(mergedChats)
            }
            
            // Merge insights (by ID)
            val existingInsightsJson = prefs[stringPreferencesKey("stored_insights")]
            val insightsType = object : TypeToken<List<StoredInsight>>() {}.type
            val existingInsights: List<StoredInsight> = if (existingInsightsJson != null) {
                gson.fromJson(existingInsightsJson, insightsType) ?: emptyList()
            } else {
                emptyList()
            }
            val mergedInsights = mergeById(existingInsights, backup.storedInsights) { it.id }
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("stored_insights")] = gson.toJson(mergedInsights)
            }
            
            // Don't merge settings in merge mode - keep existing
        } else {
            // Replace all data
            saveProjects(context, updatedProjects)
            context.dataStore.edit { prefs ->
                prefs[stringPreferencesKey("notes")] = gson.toJson(backup.notes)
                prefs[stringPreferencesKey("chats")] = gson.toJson(backup.chats)
                prefs[stringPreferencesKey("stored_insights")] = gson.toJson(backup.storedInsights)
                prefs[stringPreferencesKey("ai_settings")] = gson.toJson(backup.settings)
            }
        }
        
        val stats = buildString {
            append("Backup restored successfully!\n\n")
            append("Imported:\n")
            append("• ${updatedProjects.size} projects\n")
            append("• ${backup.notes.size} notes\n")
            append("• ${backup.chats.size} chats\n")
            append("• ${backup.storedInsights.size} insights\n")
            append("• ${imageFiles.size} images\n")
            if (!mergeMode) {
                append("• Settings")
            }
        }
        
        Result.success(stats)
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(Exception("Failed to restore backup: ${e.message}"))
    }
}

/**
 * Merge two lists by ID, preferring items from the new list when IDs match.
 */
private fun <T> mergeById(existing: List<T>, new: List<T>, getId: (T) -> String): List<T> {
    val existingMap = existing.associateBy(getId).toMutableMap()
    new.forEach { item ->
        existingMap[getId(item)] = item
    }
    return existingMap.values.toList()
}

/**
 * Get backup file statistics without importing.
 */
suspend fun getBackupInfo(context: Context, sourceUri: Uri): Result<BackupData> = withContext(Dispatchers.IO) {
    try {
        var backupData: BackupData? = null
        
        context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
            ZipInputStream(inputStream).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    if (entry.name == "backup.json") {
                        val jsonContent = zipIn.readBytes().toString(Charsets.UTF_8)
                        backupData = gson.fromJson(jsonContent, BackupData::class.java)
                        break
                    }
                    zipIn.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        
        if (backupData == null) {
            Result.failure(Exception("Invalid backup file"))
        } else {
            Result.success(backupData!!)
        }
    } catch (e: Exception) {
        e.printStackTrace()
        Result.failure(Exception("Failed to read backup: ${e.message}"))
    }
}
