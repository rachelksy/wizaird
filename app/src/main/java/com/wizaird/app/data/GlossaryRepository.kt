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

data class GlossaryWord(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val word: String = "",
    val explanation: String = "",
    val aliases: String = "", // Comma-separated aliases for search
    val createdAt: Long = System.currentTimeMillis()
) {
    fun formattedCreatedAt(): String {
        val date = Date(createdAt)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return "${dateFormat.format(date)}  •  ${timeFormat.format(date)}"
    }
}

enum class GlossarySortOrder {
    DATE_DESC,      // Most recent first (default)
    ALPHABETICAL    // A-Z
}

private val KEY_GLOSSARY_WORDS = stringPreferencesKey("glossary_words")
private val glossaryGson = Gson()

/** Flow of all glossary words for a specific project. */
fun glossaryWordsFlow(
    context: Context,
    projectId: String,
    sortOrder: GlossarySortOrder = GlossarySortOrder.DATE_DESC
): Flow<List<GlossaryWord>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_GLOSSARY_WORDS] ?: return@map emptyList()
        val type = object : TypeToken<List<GlossaryWord>>() {}.type
        val all: List<GlossaryWord> = glossaryGson.fromJson(json, type) ?: emptyList()
        val filtered = all.filter { it.projectId == projectId }
        
        when (sortOrder) {
            GlossarySortOrder.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            GlossarySortOrder.ALPHABETICAL -> filtered.sortedBy { it.word.lowercase() }
        }
    }

/** Flow of a single glossary word by ID. */
fun glossaryWordFlow(context: Context, wordId: String): Flow<GlossaryWord?> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_GLOSSARY_WORDS] ?: return@map null
        val type = object : TypeToken<List<GlossaryWord>>() {}.type
        val all: List<GlossaryWord> = glossaryGson.fromJson(json, type) ?: emptyList()
        all.firstOrNull { it.id == wordId }
    }

/** Get a single glossary word by ID (suspend function). */
suspend fun getGlossaryWordById(context: Context, wordId: String): GlossaryWord? {
    val prefs = context.dataStore.data.first()
    val json = prefs[KEY_GLOSSARY_WORDS] ?: return null
    val type = object : TypeToken<List<GlossaryWord>>() {}.type
    val all: List<GlossaryWord> = glossaryGson.fromJson(json, type) ?: emptyList()
    return all.firstOrNull { it.id == wordId }
}

/** Save or update a glossary word. */
suspend fun upsertGlossaryWord(context: Context, word: GlossaryWord) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_GLOSSARY_WORDS]
        val type = object : TypeToken<List<GlossaryWord>>() {}.type
        val current = if (json != null)
            glossaryGson.fromJson<List<GlossaryWord>>(json, type)?.toMutableList() ?: mutableListOf()
        else
            mutableListOf()
        
        val idx = current.indexOfFirst { it.id == word.id }
        if (idx >= 0) {
            current[idx] = word
        } else {
            current.add(word)
        }
        prefs[KEY_GLOSSARY_WORDS] = glossaryGson.toJson(current)
    }
}

/** Creates a new glossary word and returns it. */
fun newGlossaryWord(projectId: String): GlossaryWord {
    return GlossaryWord(
        id = UUID.randomUUID().toString(),
        projectId = projectId,
        word = "",
        explanation = "",
        aliases = "",
        createdAt = System.currentTimeMillis()
    )
}

/** Delete a glossary word by ID. */
suspend fun deleteGlossaryWord(context: Context, wordId: String) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_GLOSSARY_WORDS] ?: return@edit
        val type = object : TypeToken<List<GlossaryWord>>() {}.type
        val current: MutableList<GlossaryWord> = glossaryGson.fromJson(json, type) ?: mutableListOf()
        current.removeAll { it.id == wordId }
        prefs[KEY_GLOSSARY_WORDS] = glossaryGson.toJson(current)
    }
}

/** Move a glossary word to a different project. */
suspend fun moveGlossaryWordToProject(context: Context, wordId: String, newProjectId: String) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_GLOSSARY_WORDS] ?: return@edit
        val type = object : TypeToken<List<GlossaryWord>>() {}.type
        val current: MutableList<GlossaryWord> = glossaryGson.fromJson(json, type) ?: mutableListOf()
        val idx = current.indexOfFirst { it.id == wordId }
        if (idx >= 0) {
            current[idx] = current[idx].copy(projectId = newProjectId)
            prefs[KEY_GLOSSARY_WORDS] = glossaryGson.toJson(current)
        }
    }
}

/** Search glossary words by query (searches word and aliases). */
fun searchGlossaryWords(
    context: Context,
    projectId: String,
    query: String,
    sortOrder: GlossarySortOrder = GlossarySortOrder.DATE_DESC
): Flow<List<GlossaryWord>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_GLOSSARY_WORDS] ?: return@map emptyList()
        val type = object : TypeToken<List<GlossaryWord>>() {}.type
        val all: List<GlossaryWord> = glossaryGson.fromJson(json, type) ?: emptyList()
        
        val searchQuery = query.lowercase().trim()
        if (searchQuery.isEmpty()) {
            // No search query, return all for this project
            val filtered = all.filter { it.projectId == projectId }
            return@map when (sortOrder) {
                GlossarySortOrder.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
                GlossarySortOrder.ALPHABETICAL -> filtered.sortedBy { it.word.lowercase() }
            }
        }
        
        // Filter by project and search in word + aliases
        val filtered = all.filter { glossaryWord ->
            glossaryWord.projectId == projectId && (
                glossaryWord.word.lowercase().contains(searchQuery) ||
                glossaryWord.aliases.lowercase().contains(searchQuery)
            )
        }
        
        when (sortOrder) {
            GlossarySortOrder.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            GlossarySortOrder.ALPHABETICAL -> filtered.sortedBy { it.word.lowercase() }
        }
    }
