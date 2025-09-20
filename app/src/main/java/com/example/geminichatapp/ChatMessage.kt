package com.example.geminichatapp

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters

@Entity(tableName = "chat_messages")
@TypeConverters(RoleConverter::class) // Add this
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long, // Link to a ChatSession
    val text: String,
    val role: Role
)

enum class Role {
    USER, MODEL
}

// Room needs this to know how to save the 'Role' enum
class RoleConverter {
    @TypeConverter
    fun toRole(value: String) = enumValueOf<Role>(value)

    @TypeConverter
    fun fromRole(value: Role) = value.name
}