package com.example.geminichatapp

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch
import com.example.geminichatapp.BuildConfig

class ChatViewModel : ViewModel() {

    private val _chatHistory = MutableLiveData<MutableList<ChatMessage>>(mutableListOf())
    val chatHistory: LiveData<MutableList<ChatMessage>> = _chatHistory

    private val _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    fun sendMessage(prompt: String, selectedModel: String) {
        if (prompt.isBlank()) return

        viewModelScope.launch {
            val userMessage = ChatMessage(prompt, Role.USER)
            val currentHistory = _chatHistory.value ?: mutableListOf()
            _chatHistory.postValue((currentHistory + userMessage).toMutableList())
            _isLoading.postValue(true)

            try {
                // --- THIS IS THE CHANGE ---
                // Replaced the hardcoded string with the secure BuildConfig field
                val generativeModel = GenerativeModel(
                    modelName = selectedModel,
                    apiKey = BuildConfig.GEMINI_API_KEY
                )

                val response = generativeModel.generateContent(prompt)

                response.text?.let {
                    val modelMessage = ChatMessage(it, Role.MODEL)
                    val historyAfterUser = _chatHistory.value ?: mutableListOf()
                    _chatHistory.postValue((historyAfterUser + modelMessage).toMutableList())
                }
            } catch (e: Exception) {
                Log.e("ChatViewModel", "Gemini API Error: ${e.message}", e)
                val errorMessage = ChatMessage("Error: ${e.message}", Role.MODEL)
                val historyAfterError = _chatHistory.value ?: mutableListOf()
                _chatHistory.postValue((historyAfterError + errorMessage).toMutableList())
            } finally {
                _isLoading.postValue(false)
            }
        }
    }
}