package com.wizaird.app.data

import android.content.Context
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class Project(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val instructions: String = "",
    val picturePath: String = "",   // absolute path to file in internal storage, empty = none
    val chatCount: Int = 0
)

private val KEY_PROJECTS = stringPreferencesKey("projects")
private val projectGson = Gson()

fun projectsFlow(context: Context): Flow<List<Project>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_PROJECTS] ?: return@map emptyList()
        val type = object : TypeToken<List<Project>>() {}.type
        projectGson.fromJson(json, type) ?: emptyList()
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
    if (idx >= 0) current[idx] = project else current.add(project)
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
