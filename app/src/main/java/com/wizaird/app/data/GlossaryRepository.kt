package com.wizaird.app.data

import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID

/**
 * Legacy glossary data. Glossary words have been replaced by concepts (see ConceptRepository).
 * Only kept so old saved data and old backups can be migrated; the stored glossary list is
 * left untouched until the migration has been verified.
 */
data class GlossaryWord(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val word: String = "",
    val explanation: String = "",
    val aliases: String = "", // Comma-separated aliases for search
    val createdAt: Long = System.currentTimeMillis()
)

private val KEY_GLOSSARY_WORDS = stringPreferencesKey("glossary_words")
private val glossaryGson = Gson()

/** Reads all stored glossary words (every project). */
fun readGlossaryWords(prefs: Preferences): List<GlossaryWord> {
    val json = prefs[KEY_GLOSSARY_WORDS] ?: return emptyList()
    val type = object : TypeToken<List<GlossaryWord>>() {}.type
    return glossaryGson.fromJson(json, type) ?: emptyList()
}
