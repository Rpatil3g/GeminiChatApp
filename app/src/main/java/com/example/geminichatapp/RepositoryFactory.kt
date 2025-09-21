package com.example.geminichatapp

object RepositoryFactory {
    fun getRepository(
        modelName: String,
        chatDao: ChatDao,
        systemInstructions: String? // <-- ADD THIS
    ): ChatRepository {
        return if (modelName.startsWith("gpt")) {
            OpenAiChatRepository(modelName, chatDao, systemInstructions)
        } else {
            GeminiChatRepository(modelName, chatDao, systemInstructions)
        }
    }
}