package com.example.geminichatapp

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch

class ChatViewModel : ViewModel() {

    private val _chatHistory = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val chatHistory: LiveData<MutableList<ChatMessage>> = _chatHistory

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    // Replace with your actual API Key
    private val apiKey = "AIzaSyDAr5i-8VCfcWoNMo9aHtcrGLNRKexFogE"

    // In ChatViewModel.kt

    fun sendMessage(prompt: String, selectedModel: String) {
        if (prompt.isBlank()) return

        viewModelScope.launch {
            // --- Start of User Message Update ---
            val userMessage = ChatMessage(prompt, Role.USER)
            // Get the current list, or a new empty one if it's null
            val currentHistory = _chatHistory.value ?: mutableListOf()
            // Create a NEW list by adding the user message to the current history
            val updatedHistoryForUser = currentHistory + userMessage
            // Post the NEW list
            _chatHistory.postValue(updatedHistoryForUser.toMutableList())
            // --- End of User Message Update ---

            _isLoading.postValue(true)

            try {
                val generativeModel = GenerativeModel(
                    modelName = selectedModel,
                    apiKey = apiKey
                )

                val response = generativeModel.generateContent(prompt)

                // --- Start of Model Response Update ---
                response.text?.let {
                    val modelMessage = ChatMessage(it, Role.MODEL)
                    // Get the current list (which now includes the user's message)
                    val currentHistoryWithUser = _chatHistory.value ?: mutableListOf()
                    // Create a NEW list by adding the model message
                    val finalHistory = currentHistoryWithUser + modelMessage
                    // Post the NEW list
                    _chatHistory.postValue(finalHistory.toMutableList())
                }
                // --- End of Model Response Update ---

            } catch (e: Exception) {
                // Handle error in the same way
                val errorMessage = ChatMessage("Error: ${e.message}", Role.MODEL)
                val currentHistoryWithError = _chatHistory.value ?: mutableListOf()
                val finalHistoryWithError = currentHistoryWithError + errorMessage
                _chatHistory.postValue(finalHistoryWithError.toMutableList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
}