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
    val updatedAt: String = ""
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
fun newNote(projectId: String): NoteData = NoteData(
    id = UUID.randomUUID().toString(),
    projectId = projectId,
    body = "",
    createdAt = formattedDate(),
    updatedAt = formattedDate()
)

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
