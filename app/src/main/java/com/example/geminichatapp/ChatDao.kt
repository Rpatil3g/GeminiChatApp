package com.example.geminichatapp

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Insert
    suspend fun insertProject(project: Project): Long

    @Transaction
    @Query("SELECT * FROM projects ORDER BY id DESC")
    fun getProjectsWithSessions(): Flow<List<ProjectWithSessions>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: Long): Project?


    // Session operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSession): Long

    @Update
    suspend fun updateSession(session: ChatSession)

    // Get sessions that are NOT part of any project
    @Query("SELECT * FROM chat_sessions WHERE projectId IS NULL ORDER BY timestamp DESC")
    fun getStandaloneSessions(): Flow<List<ChatSession>>

    @Query("SELECT * FROM chat_sessions WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: Long): ChatSession?

    @Query("SELECT * FROM chat_sessions ORDER BY timestamp DESC")
    fun getAllSessions(): Flow<List<ChatSession>>

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    // Message operations
    @Insert
    suspend fun insertMessage(message: ChatMessage)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY id ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>>

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySessionId(sessionId: Long)

    @Update
    suspend fun updateProject(project: Project)
}