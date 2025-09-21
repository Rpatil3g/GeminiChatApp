// In MainActivity.kt
package com.example.geminichatapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.MenuItem
import android.view.View

import android.widget.AdapterView
import android.widget.ArrayAdapter // <-- Add this line
import android.widget.Toast

import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat // <-- Added missing import
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.geminichatapp.databinding.ActivityMainBinding
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

    private val modelOptions = listOf( "gpt-4o", "gemini-1.5-flash-latest", "gemini-1.5-pro-latest", "gemini-pro", "gemini-pro-vision" )
    private var selectedModel = modelOptions[0]

    // This factory is essential for creating the ViewModel with its dependencies
    private val viewModel: ChatViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ChatViewModel(chatDao) as T
            }
        }
    }

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
            onSessionSelected = { sessionId -> loadChatSession(sessionId) },
            onNewChat = { createNewChatSession() }
        )
    }

    private fun setupUI() {
        drawerManager.setup()
        setupSpinner()

        chatAdapter = ChatAdapter(mutableListOf(), markwon)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }

        binding.sendButton.setOnClickListener {
            val prompt = binding.promptEditText.text.toString().trim()
            if (prompt.isNotEmpty() && currentSessionId != -1L) {
                // --- 5. USE the selectedModel variable here ---
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

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.modelSpinner.adapter = adapter
        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedModel = modelOptions[position]
                Toast.makeText(this@MainActivity, "Model set to: $selectedModel", Toast.LENGTH_SHORT).show()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
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

    private fun loadInitialChat() {
        lifecycleScope.launch {
            val sessions = chatDao.getAllSessions().first()
            if (sessions.isNotEmpty()) {
                loadChatSession(sessions.first().id)
            } else {
                createNewChatSession()
            }
        }
    }

    private fun createNewChatSession() {
        lifecycleScope.launch {
            val newSession = ChatSession(title = "New Chat")
            val newId = chatDao.insertSession(newSession)
            loadChatSession(newId)
        }
    }

    private fun loadChatSession(sessionId: Long) {
        if (currentSessionId == sessionId && chatAdapter.itemCount > 0) return
        currentSessionId = sessionId
        viewModel.loadMessagesForSession(sessionId)
        binding.toolbar.title = "Chat #${sessionId}"
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