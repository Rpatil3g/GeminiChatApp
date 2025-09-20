package com.example.geminichatapp

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    var title: String,
    val timestamp: Long = System.currentTimeMillis()
)