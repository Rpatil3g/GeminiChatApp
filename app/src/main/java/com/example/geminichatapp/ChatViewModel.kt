package com.example.geminichatapp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.lang.Exception

class ChatViewModel(private val chatDao: ChatDao) : ViewModel() {

    private val _chatHistory = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatHistory: LiveData<List<ChatMessage>> = _chatHistory

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun loadMessagesForSession(sessionId: Long) {
        viewModelScope.launch {
            chatDao.getMessagesForSession(sessionId).collect { messages ->
                _chatHistory.postValue(messages)
            }
        }
    }

    // --- THIS FUNCTION HAS BEEN COMPLETELY REWRITTEN FOR STREAMING ---
    fun sendMessage(sessionId: Long, prompt: String, selectedModel: String) {
        if (prompt.isBlank() || sessionId == -1L) return

        viewModelScope.launch {
            // 1. Save user message to the database immediately.
            // The UI will auto-update because _chatHistory is observing the database.
            val userMessage = ChatMessage(sessionId = sessionId, text = prompt, role = Role.USER)
            chatDao.insertMessage(userMessage)

            // Update session title if it's the first message
            val session = chatDao.getAllSessions().first().find { it.id == sessionId }
            if (session != null && session.title == "New Chat") {
                session.title = prompt.take(30)
                chatDao.updateSession(session)
            }

            _isLoading.postValue(true)

            // 2. Prepare for the streaming response.
            // Add a temporary, empty message to the UI for the AI's response.
            val currentMessages = _chatHistory.value ?: emptyList()
            val tempModelMessage = ChatMessage(sessionId = sessionId, text = "", role = Role.MODEL)
            _chatHistory.postValue(currentMessages + tempModelMessage)

            val responseTextBuilder = StringBuilder()

            try {
                val generativeModel = GenerativeModel(
                    modelName = selectedModel,
                    apiKey = BuildConfig.GEMINI_API_KEY
                )
                val history = chatDao.getMessagesForSession(sessionId).first()
                    .map { content(it.role.name.lowercase()) { text(it.text) } }
                    .dropLast(1)
                val chat = generativeModel.startChat(history)

                // 3. Start the stream and collect chunks.
                chat.sendMessageStream(prompt)
                    .onEach { chunk ->
                        // Append each chunk of text to our builder
                        chunk.text?.let {
                            responseTextBuilder.append(it)
                        }
                        // Update the text of the last message in our temporary UI list
                        val updatedMessages = _chatHistory.value?.toMutableList()
                        updatedMessages?.last()?.let {
                            val updatedMessage = it.copy(text = responseTextBuilder.toString())
                            updatedMessages[updatedMessages.size - 1] = updatedMessage
                            _chatHistory.postValue(updatedMessages)
                        }
                    }
                    .onCompletion {
                        // This is called when the stream is finished.
                        val finalMessage = ChatMessage(
                            sessionId = sessionId,
                            text = responseTextBuilder.toString(),
                            role = Role.MODEL
                        )
                        // 4. Save the single, complete message to the database.
                        chatDao.insertMessage(finalMessage)
                        _isLoading.postValue(false)
                    }
                    .collect() // This starts the collection process.

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Gemini API Stream Error: ${e.message}", e)
                val errorMessage = ChatMessage(sessionId = sessionId, text = "Error: ${e.message}", role = Role.MODEL)
                chatDao.insertMessage(errorMessage)
                _isLoading.postValue(false)
            }
        }
    }
}