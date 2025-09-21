package com.example.geminichatapp

sealed interface DrawerAction {
    data class SessionClick(val sessionId: Long) : DrawerAction
    object NewProjectClick : DrawerAction
    object NewStandaloneChatClick : DrawerAction
    data class AddChatInProjectClick(val projectId: Long) : DrawerAction
    data class EditProjectClick(val project: Project) : DrawerAction
}