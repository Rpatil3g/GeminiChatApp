package com.example.geminichatapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.geminichatapp.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import io.noties.markwon.Markwon
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var markwon: Markwon
    private lateinit var chatAdapter: ChatAdapter

    private lateinit var whisperManager: WhisperAudioManager
    private lateinit var drawerManager: NavigationDrawerManager

    private var currentSessionId: Long = -1L
    private val chatDao by lazy { AppDatabase.getDatabase(this).chatDao() }

    private val viewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(chatDao) as T
        }
    }

    private val modelOptions = listOf("gpt-4o", "gemini-1.5-flash-latest", "gemini-1.5-pro-latest")
    private var selectedModel = modelOptions[0]

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 101
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        markwon = Markwon.builder(this).build()

        setupManagers()
        setupUI()
        setupObservers()
        loadInitialChat()
    }

    private fun setupManagers() {
        whisperManager = WhisperAudioManager(
            context = this,
            lifecycleScope = lifecycleScope,
            onResult = { transcribedText ->
                val existingText = binding.promptEditText.text.toString()
                val combinedText = if (existingText.isBlank()) transcribedText else "$existingText $transcribedText"
                binding.promptEditText.setText(combinedText)
                binding.promptEditText.setSelection(combinedText.length)
            },
            onStateChange = { isRecording, hintText ->
                binding.promptEditText.hint = hintText
                binding.progressBar.visibility = if (isRecording) View.GONE else View.VISIBLE
            }
        )

        drawerManager = NavigationDrawerManager(
            activity = this,
            binding = binding,
            lifecycleScope = lifecycleScope,
            chatDao = chatDao,
            onAction = { action -> handleDrawerAction(action) }
        )
    }

    private fun setupUI() {
        drawerManager.setup()

        chatAdapter = ChatAdapter(mutableListOf(), markwon)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        binding.sendButton.setOnClickListener {
            val prompt = binding.promptEditText.text.toString().trim()
            if (prompt.isNotEmpty() && currentSessionId != -1L) {
                viewModel.sendMessage(currentSessionId, prompt, selectedModel)
                binding.promptEditText.text.clear()
            }
        }

        binding.micButton.setOnClickListener {
            if (whisperManager.isRecording) {
                whisperManager.stop()
                binding.micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
            } else {
                requestAudioPermission()
            }
        }
    }

    private fun setupObservers() {
        viewModel.chatHistory.observe(this) { messages ->
            chatAdapter.submitList(messages)
            binding.chatRecyclerView.post {
                binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
            }
        }
        viewModel.isLoading.observe(this) { isLoading ->
            if (!whisperManager.isRecording) {
                binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    private fun handleDrawerAction(action: DrawerAction) {
        when (action) {
            is DrawerAction.SessionClick -> loadChatSession(action.sessionId)
            is DrawerAction.NewProjectClick -> showCreateProjectDialog()
            is DrawerAction.NewStandaloneChatClick -> createNewChatSession(null)
            is DrawerAction.AddChatInProjectClick -> createNewChatSession(action.projectId)
            is DrawerAction.EditProjectClick -> showEditProjectDialog(action.project)
        }
    }

    private fun loadInitialChat() {
        lifecycleScope.launch {
            val sessions = chatDao.getStandaloneSessions().first()
            if (sessions.isNotEmpty()) {
                loadChatSession(sessions.first().id)
            } else {
                createNewChatSession(null)
            }
        }
    }

    private fun createNewChatSession(projectId: Long?) {
        lifecycleScope.launch {
            val newSession = ChatSession(projectId = projectId, title = "New Chat")
            val newId = chatDao.insertSession(newSession)
            loadChatSession(newId)
        }
    }

    private fun loadChatSession(sessionId: Long) {
        if (currentSessionId == sessionId) return
        currentSessionId = sessionId
        viewModel.loadMessagesForSession(sessionId)
        lifecycleScope.launch {
            val session = chatDao.getSessionById(sessionId)
            binding.toolbar.title = session?.title ?: "Chat"
        }
    }

    private fun showCreateProjectDialog() {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_project, null)
        val nameEditText = dialogView.findViewById<TextInputEditText>(R.id.projectNameEditText)
        val instructionsEditText = dialogView.findViewById<TextInputEditText>(R.id.projectInstructionsEditText)

        MaterialAlertDialogBuilder(this)
            .setTitle("Create New Project")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = nameEditText.text.toString().trim()
                val instructions = instructionsEditText.text.toString().trim()
                if (name.isNotEmpty()) {
                    lifecycleScope.launch {
                        val newProject = Project(name = name, instructions = instructions)
                        val projectId = chatDao.insertProject(newProject)
                        createNewChatSession(projectId)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showEditProjectDialog(project: Project) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_create_project, null)
        val nameEditText = dialogView.findViewById<TextInputEditText>(R.id.projectNameEditText)
        val instructionsEditText = dialogView.findViewById<TextInputEditText>(R.id.projectInstructionsEditText)

        nameEditText.setText(project.name)
        instructionsEditText.setText(project.instructions)

        MaterialAlertDialogBuilder(this)
            .setTitle("Edit Project")
            .setView(dialogView)
            .setPositiveButton("Save") { _, _ ->
                val newName = nameEditText.text.toString().trim()
                val newInstructions = instructionsEditText.text.toString().trim()
                if (newName.isNotEmpty()) {
                    lifecycleScope.launch {
                        val updatedProject = project.copy(name = newName, instructions = newInstructions)
                        chatDao.updateProject(updatedProject)
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (drawerManager.onOptionsItemSelected(item)) true else super.onOptionsItemSelected(item)
    }

    private fun requestAudioPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), RECORD_AUDIO_PERMISSION_CODE)
        } else {
            whisperManager.start()
            binding.micButton.setImageResource(R.drawable.ic_stop)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == RECORD_AUDIO_PERMISSION_CODE && res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) {
            whisperManager.start()
            binding.micButton.setImageResource(R.drawable.ic_stop)
        } else {
            Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        whisperManager.release()
    }
}