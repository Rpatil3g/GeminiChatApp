package com.example.geminichatapp

import android.view.MenuItem
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.geminichatapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class NavigationDrawerManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val lifecycleScope: CoroutineScope,
    private val chatDao: ChatDao,
    private val onAction: (DrawerAction) -> Unit
) {
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private lateinit var drawerAdapter: DrawerAdapter
    private lateinit var drawerRecyclerView: RecyclerView

    fun setup() {
        activity.setSupportActionBar(binding.toolbar)
        drawerToggle = ActionBarDrawerToggle(activity, binding.drawerLayout, R.string.open, R.string.close)
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        drawerRecyclerView = binding.navigationView.findViewById(R.id.drawerRecyclerView)
        drawerRecyclerView.layoutManager = LinearLayoutManager(activity)
        drawerAdapter = DrawerAdapter(emptyList()) { action ->
            onAction(action)
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        }
        drawerRecyclerView.adapter = drawerAdapter

        observeDataForDrawer()
    }

    private fun observeDataForDrawer() {
        lifecycleScope.launch {
            chatDao.getProjectsWithSessions().combine(chatDao.getStandaloneSessions()) { projects, standalone ->
                val drawerItems = mutableListOf<DrawerItem>()
                drawerItems.add(DrawerItem.NewProjectAction)
                drawerItems.add(DrawerItem.NewStandaloneChatAction)

                projects.forEach { projectWithSessions ->
                    drawerItems.add(DrawerItem.ProjectHeader(projectWithSessions.project))
                    projectWithSessions.sessions.forEach { session ->
                        drawerItems.add(DrawerItem.SessionItem(session))
                    }
                }

                if (standalone.isNotEmpty()) {
                    drawerItems.add(DrawerItem.ProjectHeader(Project(id = -1, name = "General Chats", instructions = "")))
                    standalone.forEach { session ->
                        drawerItems.add(DrawerItem.SessionItem(session))
                    }
                }
                drawerItems
            }.collect { items ->
                drawerAdapter.updateItems(items)
            }
        }
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        return drawerToggle.onOptionsItemSelected(item)
    }
}