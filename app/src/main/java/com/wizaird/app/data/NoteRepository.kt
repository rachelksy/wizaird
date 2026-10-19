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

data class NoteData(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val body: String = "",
    val createdAt: String = "",   // formatted display string, e.g. "Nov 12, 2024"
    val updatedAt: String = "",
    val createdAtMs: Long = 0L,   // epoch millis for sorting
    val conceptIds: List<String> = emptyList()  // concepts this note is tagged with
)

private val KEY_NOTES = stringPreferencesKey("notes")
private val noteGson = Gson()

private fun formattedDate(): String =
    SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())

fun notesFlow(context: Context, projectId: String): Flow<List<NoteData>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_NOTES] ?: return@map emptyList()
        val type = object : TypeToken<List<NoteData>>() {}.type
        val all: List<NoteData> = noteGson.fromJson(json, type) ?: emptyList()
        all.filter { it.projectId == projectId }
            .sortedByDescending { it.createdAtMs }
    }

suspend fun getNoteById(context: Context, noteId: String): NoteData? {
    val prefs = context.dataStore.data.first()
    val json = prefs[KEY_NOTES] ?: return null
    val type = object : TypeToken<List<NoteData>>() {}.type
    val all: List<NoteData> = noteGson.fromJson(json, type) ?: emptyList()
    return all.firstOrNull { it.id == noteId }
}

suspend fun upsertNote(context: Context, note: NoteData) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_NOTES]
        val type = object : TypeToken<List<NoteData>>() {}.type
        val current = if (json != null)
            noteGson.fromJson<List<NoteData>>(json, type)?.toMutableList() ?: mutableListOf()
        else
            mutableListOf()
        val idx = current.indexOfFirst { it.id == note.id }
        if (idx >= 0) current[idx] = note else current.add(note)
        prefs[KEY_NOTES] = noteGson.toJson(current)
    }
}

/** Creates a new note and returns it. */
fun newNote(projectId: String): NoteData {
    val now = System.currentTimeMillis()
    return NoteData(
        id = UUID.randomUUID().toString(),
        projectId = projectId,
        body = "",
        createdAt = formattedDate(),
        updatedAt = formattedDate(),
        createdAtMs = now
    )
}

/** Delete a note by ID. */
suspend fun deleteNote(context: Context, noteId: String) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_NOTES] ?: return@edit
        val type = object : TypeToken<List<NoteData>>() {}.type
        val current: MutableList<NoteData> = noteGson.fromJson(json, type) ?: mutableListOf()
        current.removeAll { it.id == noteId }
        prefs[KEY_NOTES] = noteGson.toJson(current)
    }
}

/** Move a note to a different project. */
suspend fun moveNoteToProject(context: Context, noteId: String, newProjectId: String) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_NOTES] ?: return@edit
        val type = object : TypeToken<List<NoteData>>() {}.type
        val current: MutableList<NoteData> = noteGson.fromJson(json, type) ?: mutableListOf()
        val idx = current.indexOfFirst { it.id == noteId }
        if (idx >= 0) {
            // Concepts belong to a project, so tags don't follow the note
            current[idx] = current[idx].copy(projectId = newProjectId, conceptIds = emptyList())
            prefs[KEY_NOTES] = noteGson.toJson(current)
        }
    }
}

/** Notes in a project tagged with a concept. */
fun notesForConceptFlow(context: Context, conceptId: String): Flow<List<NoteData>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_NOTES] ?: return@map emptyList()
        val type = object : TypeToken<List<NoteData>>() {}.type
        val all: List<NoteData> = noteGson.fromJson(json, type) ?: emptyList()
        all.filter { conceptId in it.conceptIds }.sortedByDescending { it.createdAtMs }
    }

/** Replace the concept tags on a note. */
suspend fun setNoteConcepts(context: Context, noteId: String, conceptIds: List<String>) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_NOTES] ?: return@edit
        val type = object : TypeToken<List<NoteData>>() {}.type
        val current: MutableList<NoteData> = noteGson.fromJson(json, type) ?: mutableListOf()
        val idx = current.indexOfFirst { it.id == noteId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(conceptIds = conceptIds)
            prefs[KEY_NOTES] = noteGson.toJson(current)
        }
    }
    // A ghost linked to a note is no longer a ghost
    promoteGhosts(context, conceptIds)
}

/** Remove a deleted concept from every note's tags. */
suspend fun removeConceptFromNotes(context: Context, conceptId: String) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_NOTES] ?: return@edit
        val type = object : TypeToken<List<NoteData>>() {}.type
        val current: List<NoteData> = noteGson.fromJson(json, type) ?: return@edit
        if (current.none { conceptId in it.conceptIds }) return@edit
        prefs[KEY_NOTES] = noteGson.toJson(current.map { it.copy(conceptIds = it.conceptIds - conceptId) })
    }
}
