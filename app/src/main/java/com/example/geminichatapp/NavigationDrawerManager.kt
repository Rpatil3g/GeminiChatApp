// In NavigationDrawerManager.kt
package com.example.geminichatapp

import android.view.Menu
import android.view.MenuItem // <-- ADD THIS IMPORT
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.geminichatapp.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NavigationDrawerManager(
    private val activity: AppCompatActivity,
    private val binding: ActivityMainBinding,
    private val lifecycleScope: CoroutineScope,
    private val chatDao: ChatDao,
    private val onSessionSelected: (Long) -> Unit,
    private val onNewChat: () -> Unit
) {
    private lateinit var drawerToggle: ActionBarDrawerToggle

    fun setup() {
        activity.setSupportActionBar(binding.toolbar)
        drawerToggle = ActionBarDrawerToggle(activity, binding.drawerLayout, R.string.open, R.string.close) // <-- This will be fixed next
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
        activity.supportActionBar?.setDisplayHomeAsUpEnabled(true)

        binding.navigationView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_new_chat -> onNewChat()
                else -> {
                    if (menuItem.groupId == R.id.nav_history_group) {
                        onSessionSelected(menuItem.itemId.toLong())
                    }
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }

        observeSessions()
    }

    private fun observeSessions() {
        lifecycleScope.launch {
            chatDao.getAllSessions().collect { sessions ->
                updateNavDrawer(sessions)
            }
        }
    }

    private fun updateNavDrawer(sessions: List<ChatSession>) {
        val menu = binding.navigationView.menu
        val historyGroup = menu.findItem(R.id.nav_history_group).subMenu ?: return
        historyGroup.clear()
        sessions.forEach { session ->
            historyGroup.add(R.id.nav_history_group, session.id.toInt(), Menu.NONE, session.title)
        }
    }

    fun onOptionsItemSelected(item: MenuItem): Boolean {
        return drawerToggle.onOptionsItemSelected(item)
    }
}