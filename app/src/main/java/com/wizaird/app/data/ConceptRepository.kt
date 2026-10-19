package com.wizaird.app.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
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

// Every field has a default so Gson can load concepts saved before a field existed.

enum class ConceptStatus { FULL, GHOST }

enum class ConceptSourceType { INSIGHT, CHAT_MESSAGE, NOTE }

/** Where a concept was met. Stores a snippet so a deleted message/insight leaves no blank. */
data class ConceptSource(
    val id: String = UUID.randomUUID().toString(),
    val type: ConceptSourceType = ConceptSourceType.CHAT_MESSAGE,
    val chatId: String? = null,
    val messageId: String? = null,
    val insightId: String? = null,
    val noteId: String? = null,
    val snippet: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class Concept(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String = "",
    val name: String = "",
    val intro: String = "",          // 1–2 sentence summary, shown on list cards
    val body: String = "",           // long-form markdown article, shown on the concept page
    val aliases: String = "",        // comma-separated, used for search and duplicate checks
    val status: ConceptStatus = ConceptStatus.FULL,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val firstMetAt: Long? = null,
    val lastTouchedAt: Long? = null,
    val sources: List<ConceptSource> = emptyList(),
    val relatedConceptIds: List<String> = emptyList(),
    val copiedFromId: String? = null  // set when copied from another project
) {
    val isGhost: Boolean get() = status == ConceptStatus.GHOST

    /** True when the concept has no written content yet. */
    val isEmpty: Boolean get() = intro.isBlank() && body.isBlank()

    fun aliasList(): List<String> =
        aliases.split(",").map { it.trim() }.filter { it.isNotEmpty() }

    fun formattedCreatedAt(): String {
        val date = Date(createdAt)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        return dateFormat.format(date)
    }
}

enum class ConceptSortOrder {
    DATE_DESC,      // Most recent first (default)
    ALPHABETICAL    // A-Z
}

enum class ConceptFilter { ALL, GHOSTS_ONLY, HIDE_GHOSTS }

private val KEY_CONCEPTS = stringPreferencesKey("concepts")
private val KEY_GLOSSARY_MIGRATED = booleanPreferencesKey("concepts_glossary_migrated")
private val conceptGson = Gson()
private val conceptListType = object : TypeToken<List<Concept>>() {}.type

private fun readConcepts(prefs: Preferences): List<Concept> {
    val json = prefs[KEY_CONCEPTS] ?: return emptyList()
    return conceptGson.fromJson<List<Concept>>(json, conceptListType) ?: emptyList()
}

/** Shared by the list, search and duplicate check so they all match the same way. */
fun Concept.matchesName(query: String): Boolean {
    val q = query.trim().lowercase()
    if (q.isEmpty()) return false
    return name.trim().lowercase() == q || aliasList().any { it.lowercase() == q }
}

/** Concepts for a project, filtered by search (name + aliases) and ghost filter, then sorted. */
fun conceptsFlow(
    context: Context,
    projectId: String,
    query: String = "",
    sortOrder: ConceptSortOrder = ConceptSortOrder.DATE_DESC,
    filter: ConceptFilter = ConceptFilter.ALL
): Flow<List<Concept>> =
    context.dataStore.data.map { prefs ->
        val q = query.lowercase().trim()
        val filtered = readConcepts(prefs).filter { c ->
            c.projectId == projectId &&
                (q.isEmpty() || c.name.lowercase().contains(q) || c.aliases.lowercase().contains(q)) &&
                when (filter) {
                    ConceptFilter.ALL -> true
                    ConceptFilter.GHOSTS_ONLY -> c.isGhost
                    ConceptFilter.HIDE_GHOSTS -> !c.isGhost
                }
        }
        when (sortOrder) {
            ConceptSortOrder.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
            ConceptSortOrder.ALPHABETICAL -> filtered.sortedBy { it.name.lowercase() }
        }
    }

fun conceptFlow(context: Context, conceptId: String): Flow<Concept?> =
    context.dataStore.data.map { prefs -> readConcepts(prefs).firstOrNull { it.id == conceptId } }

suspend fun getConceptById(context: Context, conceptId: String): Concept? =
    readConcepts(context.dataStore.data.first()).firstOrNull { it.id == conceptId }

/** Finds a concept in the project whose name or alias matches [term] (case-insensitive). */
suspend fun findDuplicateConcept(context: Context, projectId: String, term: String, excludeId: String? = null): Concept? =
    readConcepts(context.dataStore.data.first())
        .firstOrNull { it.projectId == projectId && it.id != excludeId && it.matchesName(term) }

suspend fun upsertConcept(context: Context, concept: Concept) {
    context.dataStore.edit { prefs ->
        val current = readConcepts(prefs).toMutableList()
        val idx = current.indexOfFirst { it.id == concept.id }
        if (idx >= 0) current[idx] = concept else current.add(concept)
        prefs[KEY_CONCEPTS] = conceptGson.toJson(current)
    }
}

/** Deletes a concept and removes it from note tags and other concepts' related lists. */
suspend fun deleteConcept(context: Context, conceptId: String) {
    context.dataStore.edit { prefs ->
        val remaining = readConcepts(prefs)
            .filter { it.id != conceptId }
            .map { c ->
                if (conceptId in c.relatedConceptIds) c.copy(relatedConceptIds = c.relatedConceptIds - conceptId) else c
            }
        prefs[KEY_CONCEPTS] = conceptGson.toJson(remaining)
    }
    removeConceptFromNotes(context, conceptId)
}

/**
 * Copies a concept into another project as a new concept with its own ID.
 * The original keeps all its links; the copy remembers where it came from.
 * Sources are not copied because they point at chats/insights in the original project.
 */
suspend fun copyConceptToProject(context: Context, conceptId: String, targetProjectId: String) {
    val original = getConceptById(context, conceptId) ?: return
    val now = System.currentTimeMillis()
    upsertConcept(
        context,
        original.copy(
            id = UUID.randomUUID().toString(),
            projectId = targetProjectId,
            createdAt = now,
            updatedAt = now,
            sources = emptyList(),
            relatedConceptIds = emptyList(),
            copiedFromId = original.id
        )
    )
}

/** Records where a concept was met, and bumps its first-met / last-touched dates. */
suspend fun addSourceToConcept(context: Context, conceptId: String, source: ConceptSource) {
    val concept = getConceptById(context, conceptId) ?: return
    // Skip if this exact place is already recorded
    val alreadyThere = concept.sources.any {
        it.type == source.type && it.chatId == source.chatId &&
            it.messageId == source.messageId && it.insightId == source.insightId && it.noteId == source.noteId
    }
    val now = System.currentTimeMillis()
    upsertConcept(
        context,
        concept.copy(
            sources = if (alreadyThere) concept.sources else concept.sources + source,
            firstMetAt = concept.firstMetAt ?: source.createdAt,
            lastTouchedAt = now
        )
    )
}

/** Cuts a ~200 character snippet around [term] from [text], so the source still makes sense later. */
fun buildSnippet(text: String, term: String, radius: Int = 100): String {
    val plain = text.replace(Regex("\\s+"), " ").trim()
    if (plain.length <= radius * 2) return plain
    val idx = plain.lowercase().indexOf(term.lowercase()).takeIf { it >= 0 } ?: return plain.take(radius * 2).trimEnd() + "…"
    val start = (idx - radius).coerceAtLeast(0)
    val end = (idx + term.length + radius).coerceAtMost(plain.length)
    return (if (start > 0) "…" else "") + plain.substring(start, end).trim() + (if (end < plain.length) "…" else "")
}

// ── Glossary → concepts migration ─────────────────────────────────────────────

private fun GlossaryWord.toConcept(): Concept = Concept(
    id = id,                      // same ID, so re-running can never duplicate
    projectId = projectId,
    name = word,
    intro = explanation,
    aliases = aliases,
    status = ConceptStatus.FULL,
    createdAt = createdAt,
    updatedAt = createdAt
)

/** Adds concepts for glossary words that don't have one yet (matched by ID). Returns how many were added. */
private fun mergeGlossaryIntoConcepts(prefs: androidx.datastore.preferences.core.MutablePreferences, words: List<GlossaryWord>): Int {
    val concepts = readConcepts(prefs).toMutableList()
    val existingIds = concepts.map { it.id }.toHashSet()
    val newOnes = words.filter { it.id !in existingIds }.map { it.toConcept() }
    if (newOnes.isNotEmpty()) {
        concepts.addAll(newOnes)
        prefs[KEY_CONCEPTS] = conceptGson.toJson(concepts)
    }
    return newOnes.size
}

/**
 * One-time migration of glossary words into concepts. Runs at app start.
 * The old glossary data is left untouched so it can be checked; the "done" flag stops it from
 * running again (otherwise a concept the user deleted would come back on the next launch).
 */
suspend fun migrateGlossaryToConceptsIfNeeded(context: Context) {
    context.dataStore.edit { prefs ->
        if (prefs[KEY_GLOSSARY_MIGRATED] == true) return@edit
        mergeGlossaryIntoConcepts(prefs, readGlossaryWords(prefs))
        prefs[KEY_GLOSSARY_MIGRATED] = true
    }
}

/** Used by backup import: converts glossary words from an older backup into concepts. */
suspend fun migrateGlossaryWordsToConcepts(context: Context, words: List<GlossaryWord>): Int {
    var added = 0
    context.dataStore.edit { prefs -> added = mergeGlossaryIntoConcepts(prefs, words) }
    return added
}
