package com.example.geminichatapp

data class ChatMessage(
    val text: String,
    val role: Role
)

enum class Role {
    USER, MODEL
}