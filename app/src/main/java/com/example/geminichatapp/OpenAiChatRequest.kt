package com.example.geminichatapp

import com.google.gson.annotations.SerializedName

// 1. Added the "stream" parameter to the request
data class OpenAiChatRequest(
    val model: String,
    val messages: List<ChatMessageJson>,
    val stream: Boolean = true // <-- ADD THIS LINE
)

data class ChatMessageJson(
    val role: String,
    val content: String
)

// 2. Updated the Response classes to handle stream chunks
data class OpenAiChatResponse(
    val choices: List<Choice>
)

data class Choice(
    val message: ChatMessageJson,
    val delta: Delta?, // This will be used in streaming responses
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class Delta(
    val content: String?
)