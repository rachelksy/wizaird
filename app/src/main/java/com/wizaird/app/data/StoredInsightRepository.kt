package com.wizaird.app.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.Flow
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

/** Save a new insight to permanent storage. */
suspend fun saveInsight(context: Context, projectId: String, text: String) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_STORED_INSIGHTS]
        val type = object : TypeToken<List<StoredInsight>>() {}.type
        val current = if (json != null)
            insightGson.fromJson<List<StoredInsight>>(json, type)?.toMutableList() ?: mutableListOf()
        else
            mutableListOf()
        
        val newInsight = StoredInsight(
            projectId = projectId,
            text = text,
            createdAt = System.currentTimeMillis()
        )
        current.add(newInsight)
        prefs[KEY_STORED_INSIGHTS] = insightGson.toJson(current)
    }
}

/** Delete an insight by ID. */
suspend fun deleteInsight(context: Context, insightId: String) {
    context.dataStore.edit { prefs ->
        val json = prefs[KEY_STORED_INSIGHTS] ?: return@edit
        val type = object : TypeToken<List<StoredInsight>>() {}.type
        val current: MutableList<StoredInsight> = insightGson.fromJson(json, type) ?: mutableListOf()
        current.removeAll { it.id == insightId }
        prefs[KEY_STORED_INSIGHTS] = insightGson.toJson(current)
    }
}
