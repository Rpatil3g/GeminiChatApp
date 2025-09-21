package com.example.geminichatapp

import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first

class GeminiChatRepository(
    private val modelName: String,
    private val chatDao: ChatDao,
    private val systemInstructions: String?
) : ChatRepository {

    override fun sendMessageStream(sessionId: Long, prompt: String): Flow<String> = channelFlow {
        val generativeModel = GenerativeModel(
            modelName = modelName,
            apiKey = BuildConfig.GEMINI_API_KEY,
            systemInstruction = systemInstructions?.let {
                content(role = "system") { text(it) }  // Create Content with system role
            }
        )
        val history = chatDao.getMessagesForSession(sessionId).first()
            .map { content(it.role.name.lowercase()) { text(it.text) } }
            .dropLast(1)
        val chat = generativeModel.startChat(history)

        chat.sendMessageStream(prompt).collect { chunk ->
            chunk.text?.let { send(it) }
        }
    }
}