package com.example.geminichatapp

// This file should ONLY contain the DrawerItem interface.
sealed interface DrawerItem {
    data class ProjectHeader(val project: Project) : DrawerItem
    data class SessionItem(val session: ChatSession) : DrawerItem
    object NewProjectAction : DrawerItem
    object NewStandaloneChatAction : DrawerItem
}

// DELETE the DrawerAction interface that was previously here.