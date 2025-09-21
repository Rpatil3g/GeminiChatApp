package com.example.geminichatapp

object RepositoryFactory {
    fun getRepository(modelName: String, chatDao: ChatDao): ChatRepository {
        return if (modelName.startsWith("gpt")) {
            OpenAiChatRepository(modelName, chatDao)
        } else {
            GeminiChatRepository(modelName, chatDao)
        }
    }
}