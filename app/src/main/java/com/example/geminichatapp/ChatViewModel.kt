// In ChatViewModel.kt
package com.example.geminichatapp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// The ViewModel now takes the DAO as a constructor parameter
class ChatViewModel(private val chatDao: ChatDao) : ViewModel() {

    private val _chatHistory = MutableLiveData<List<ChatMessage>>(emptyList())
    val chatHistory: LiveData<List<ChatMessage>> = _chatHistory

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // New function to load messages for a specific chat session from the database
    fun loadMessagesForSession(sessionId: Long) {
        viewModelScope.launch {
            chatDao.getMessagesForSession(sessionId).collect { messages ->
                _chatHistory.postValue(messages)
            }
        }
    }

    // The function signature and logic are now updated for the database
    fun sendMessage(sessionId: Long, prompt: String, selectedModel: String) {
        if (prompt.isBlank() || sessionId == -1L) return

        viewModelScope.launch {
            _isLoading.postValue(true)

            // 1. Create ChatMessage with the required sessionId
            val userMessage = ChatMessage(sessionId = sessionId, text = prompt, role = Role.USER)
            chatDao.insertMessage(userMessage)

            val session = chatDao.getAllSessions().first().find { it.id == sessionId }
            if (session != null && session.title == "New Chat") {
                session.title = prompt.take(30)
                chatDao.updateSession(session)
            }

            try {
                val generativeModel = GenerativeModel(
                    modelName = selectedModel,
                    apiKey = BuildConfig.GEMINI_API_KEY
                )

                val history = chatDao.getMessagesForSession(sessionId).first()
                    .map { content(it.role.name.lowercase()) { text(it.text) } }
                    .dropLast(1)

                val chat = generativeModel.startChat(history)
                val response = chat.sendMessage(prompt)

                response.text?.let {
                    // 2. Create model's response with the sessionId
                    val modelMessage = ChatMessage(sessionId = sessionId, text = it, role = Role.MODEL)
                    chatDao.insertMessage(modelMessage)
                }

            } catch (e: Exception) {
                Log.e("ChatViewModel", "Gemini API Error: ${e.message}", e)
                val errorMessage = ChatMessage(sessionId = sessionId, text = "Error: ${e.message}", role = Role.MODEL)
                chatDao.insertMessage(errorMessage)
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
}