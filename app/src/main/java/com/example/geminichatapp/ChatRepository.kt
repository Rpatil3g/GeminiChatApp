package com.example.geminichatapp

import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    // Both repositories will return a simple Flow of text chunks
    fun sendMessageStream(sessionId: Long, prompt: String): Flow<String>
}