package com.example.geminichatapp

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "chat_sessions",
    foreignKeys = [ForeignKey(
        entity = Project::class,
        parentColumns = ["id"],
        childColumns = ["projectId"],
        onDelete = ForeignKey.CASCADE // If a project is deleted, delete its sessions
    )]
)
data class ChatSession(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val projectId: Long?, // <-- ADD THIS. Can be null for standalone chats
    var title: String,
    val timestamp: Long = System.currentTimeMillis()
)