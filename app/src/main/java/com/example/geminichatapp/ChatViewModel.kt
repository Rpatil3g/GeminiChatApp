package com.example.geminichatapp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    fun sendMessage(sessionId: Long, prompt: String, selectedModel: String) {
        if (prompt.isBlank() || sessionId == -1L) return

        viewModelScope.launch {
            _isLoading.postValue(true)

            // --- BACKGROUND THREAD WORK ---
            withContext(Dispatchers.IO) {
                // 1. Save user message and update session title as before
                val userMessage = ChatMessage(sessionId = sessionId, text = prompt, role = Role.USER)
                chatDao.insertMessage(userMessage)

                val session = chatDao.getAllSessions().first().find { it.id == sessionId }
                if (session != null && session.title == "New Chat") {
                    session.title = prompt.take(30)
                    chatDao.updateSession(session)
                }
            }

            // --- THIS IS THE FIX ---
            // 2. Now on the Main thread, get the GUARANTEED FRESH list from the database
            val freshMessages = withContext(Dispatchers.IO) {
                chatDao.getMessagesForSession(sessionId).first()
            }

            // 3. Add the temporary AI placeholder to THIS fresh list
            val tempModelMessage = ChatMessage(sessionId = sessionId, text = "", role = Role.MODEL)
            _chatHistory.postValue(freshMessages + tempModelMessage)

            val responseTextBuilder = StringBuilder()
            val repository = RepositoryFactory.getRepository(selectedModel, chatDao)

            // --- The rest of the streaming logic can now proceed safely ---
            viewModelScope.launch(Dispatchers.IO) {
                repository.sendMessageStream(sessionId, prompt)
                    .catch { e ->
                        Log.e("ChatViewModel", "Flow exception: ${e.message}", e)
                        withContext(Dispatchers.Main) {
                            val errorMessage = ChatMessage(sessionId = sessionId, text = "Error: ${e.message}", role = Role.MODEL)
                            chatDao.insertMessage(errorMessage)
                            _isLoading.postValue(false)
                        }
                    }
                    .onCompletion {
                        withContext(Dispatchers.Main) {
                            val finalMessage = ChatMessage(
                                sessionId = sessionId,
                                text = responseTextBuilder.toString().trim(),
                                role = Role.MODEL
                            )
                            chatDao.insertMessage(finalMessage)
                            _isLoading.postValue(false)
                        }
                    }
                    .collect { chunk ->
                        // UI updates must be on the Main thread
                        withContext(Dispatchers.Main) {
                            responseTextBuilder.append(chunk)
                            val updatedList = _chatHistory.value?.toMutableList()
                            updatedList?.last()?.let {
                                val updatedMessage = it.copy(text = responseTextBuilder.toString())
                                updatedList[updatedList.size - 1] = updatedMessage
                                _chatHistory.postValue(updatedList)
                            }
                        }
                    }
            }
        }
    }
}