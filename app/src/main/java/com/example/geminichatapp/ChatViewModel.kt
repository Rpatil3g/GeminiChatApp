package com.example.geminichatapp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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

    // --- THIS IS THE FIX (Part 1) ---
    // A reference to the coroutine job that collects messages.
    private var messageCollectorJob: Job? = null

    // --- THIS IS THE FIX (Part 2) ---
    // The load function is now updated to cancel any previous job.
    fun loadMessagesForSession(sessionId: Long) {
        // Cancel any existing data collection before starting a new one.
        // This is the key to preventing the race condition.
        messageCollectorJob?.cancel()

        messageCollectorJob = viewModelScope.launch {
            chatDao.getMessagesForSession(sessionId).collect { messages ->
                _chatHistory.postValue(messages)
            }
        }
    }

    fun sendMessage(sessionId: Long, prompt: String, selectedModel: String) {
        if (prompt.isBlank() || sessionId == -1L) return

        viewModelScope.launch {
            _isLoading.postValue(true)

            // Perform all DB writes on a background thread
            withContext(Dispatchers.IO) {
                val userMessage = ChatMessage(sessionId = sessionId, text = prompt, role = Role.USER)
                chatDao.insertMessage(userMessage)

                val session = chatDao.getSessionById(sessionId)
                if (session != null && session.title == "New Chat") {
                    session.title = prompt.take(30)
                    chatDao.updateSession(session)
                }
            }

            // This logic is now safe because the messageCollectorJob ensures
            // that _chatHistory will be up-to-date.
            val currentMessages = _chatHistory.value ?: emptyList()
            val tempModelMessage = ChatMessage(sessionId = sessionId, text = "", role = Role.MODEL)
            _chatHistory.postValue(currentMessages + tempModelMessage)

            val responseTextBuilder = StringBuilder()

            val systemInstructions = withContext(Dispatchers.IO) {
                val session = chatDao.getSessionById(sessionId)
                session?.projectId?.let { chatDao.getProjectById(it)?.instructions }
            }
            val repository = RepositoryFactory.getRepository(selectedModel, chatDao, systemInstructions)

            viewModelScope.launch(Dispatchers.IO) {
                repository.sendMessageStream(sessionId, prompt)
                    .catch { e ->
                        Log.e("ChatViewModel", "Flow exception: ${e.message}", e)
                        val errorMessage = ChatMessage(sessionId = sessionId, text = "Error: ${e.message}", role = Role.MODEL)
                        chatDao.insertMessage(errorMessage)
                        withContext(Dispatchers.Main) { _isLoading.postValue(false) }
                    }
                    .onCompletion {
                        // When the stream is complete, we must fetch the real list from the DB
                        // to remove our temporary placeholder message before saving the final one.
                        val historyFromDb = chatDao.getMessagesForSession(sessionId).first()
                        _chatHistory.postValue(historyFromDb)

                        val finalMessage = ChatMessage(
                            sessionId = sessionId,
                            text = responseTextBuilder.toString().trim(),
                            role = Role.MODEL
                        )
                        chatDao.insertMessage(finalMessage)
                        withContext(Dispatchers.Main) { _isLoading.postValue(false) }
                    }
                    .collect { chunk ->
                        withContext(Dispatchers.Main) {
                            responseTextBuilder.append(chunk)
                            // --- THIS IS THE FINAL FIX ---
                            // We use a null-safe '.let' block. The code inside will ONLY run
                            // if _chatHistory.value is not null.
                            _chatHistory.value?.let { currentList ->
                                val updatedList = currentList.toMutableList()
                                if (updatedList.isNotEmpty()) {
                                    val lastMessage = updatedList.last()
                                    val updatedMessage = lastMessage.copy(text = responseTextBuilder.toString())
                                    updatedList[updatedList.size - 1] = updatedMessage

                                    // This is now 100% safe from nulls.
                                    _chatHistory.postValue(updatedList)
                                }
                            }
                        }
                    }
            }
        }
    }
}