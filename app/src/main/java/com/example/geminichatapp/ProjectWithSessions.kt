package com.example.geminichatapp

import androidx.room.Embedded
import androidx.room.Relation

data class ProjectWithSessions(
    @Embedded val project: Project,
    @Relation(
        parentColumn = "id",
        entityColumn = "projectId"
    )
    val sessions: List<ChatSession>
)