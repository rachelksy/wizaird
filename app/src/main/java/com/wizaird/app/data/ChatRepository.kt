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

// ── Data Models ───────────────────────────────────────────────────────────────

enum class MessageSender { USER, AI }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ChatData(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val title: String = "",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val insightId: String? = null,  // Link to the insight that started this chat
    val contextWindowSize: Int = 50  // Number of last messages to send to AI for context
) {
    fun formattedCreatedAt(): String {
        val date = Date(createdAt)
        val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
        return "${dateFormat.format(date)}  •  ${timeFormat.format(date)}"
    }
}

// ── Repository ────────────────────────────────────────────────────────────────

private val KEY_CHATS = stringPreferencesKey("chats")
private val chatGson = Gson()

/** Flow of all chats for a specific project. */
fun chatsFlow(context: Context, projectId: String): Flow<List<ChatData>> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_CHATS] ?: return@map emptyList()
        val type = object : TypeToken<List<ChatData>>() {}.type
        val all: List<ChatData> = chatGson.fromJson(json, type) ?: emptyList()
        all.filter { it.projectId == projectId }
            .sortedByDescending { it.createdAt }
    }

/** Flow of a single chat by ID. */
fun chatFlow(context: Context, chatId: String): Flow<ChatData?> =
    context.dataStore.data.map { prefs ->
        val json = prefs[KEY_CHATS] ?: return@map null
        val type = object : TypeToken<List<ChatData>>() {}.type
        val all: List<ChatData> = chatGson.fromJson(json, type) ?: emptyList()
        all.firstOrNull { it.id == chatId }
    }

/** Save all chats. */
private suspend fun saveChats(context: Context, chats: List<ChatData>) {
    context.dataStore.edit { prefs ->
        prefs[KEY_CHATS] = chatGson.toJson(chats)
    }
}

/** Create or update a chat. */
suspend fun upsertChat(context: Context, chat: ChatData) {
    val current = mutableListOf<ChatData>()
    val prefs = context.dataStore.data.first()
    val json = prefs[KEY_CHATS]
    if (json != null) {
        val type = object : TypeToken<List<ChatData>>() {}.type
        current.addAll(chatGson.fromJson(json, type) ?: emptyList())
    }
    val idx = current.indexOfFirst { it.id == chat.id }
    if (idx >= 0) current[idx] = chat else current.add(chat)
    saveChats(context, current)
}

/** Delete a chat by ID. */
suspend fun deleteChat(context: Context, chatId: String) {
    val prefs = context.dataStore.data.first()
    val json = prefs[KEY_CHATS] ?: return
    val type = object : TypeToken<List<ChatData>>() {}.type
    val current: MutableList<ChatData> = chatGson.fromJson(json, type) ?: mutableListOf()
    current.removeAll { it.id == chatId }
    saveChats(context, current)
}

/** Add a message to an existing chat. */
suspend fun addMessageToChat(context: Context, chatId: String, message: ChatMessage) {
    val prefs = context.dataStore.data.first()
    val json = prefs[KEY_CHATS] ?: return
    val type = object : TypeToken<List<ChatData>>() {}.type
    val current: MutableList<ChatData> = chatGson.fromJson(json, type) ?: mutableListOf()
    val idx = current.indexOfFirst { it.id == chatId }
    if (idx >= 0) {
        val chat = current[idx]
        current[idx] = chat.copy(messages = chat.messages + message)
        saveChats(context, current)
    }
}
