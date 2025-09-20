package com.example.geminichatapp

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.geminichatapp.databinding.ActivityMainBinding
import io.noties.markwon.Markwon
import io.noties.markwon.ext.tables.TablePlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var markwon: Markwon
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var isRecording = false

    private val modelOptions = listOf("gemini-1.5-flash-latest", "gemini-pro", "gemini-pro-vision")
    private var selectedModel = modelOptions[0]

    companion object {
        private const val RECORD_AUDIO_PERMISSION_CODE = 101
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        markwon = Markwon.builder(this).usePlugin(TablePlugin.create(this)).build()

        setupRecyclerView()
        setupSpinner()
        setupClickListeners()
        observeViewModel() // This function is crucial
    }

    private fun setupRecyclerView() {
        // Pass an empty list to the adapter initially
        chatAdapter = ChatAdapter(mutableListOf(), markwon)
        binding.chatRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = chatAdapter
        }
    }

    private fun setupClickListeners() {
        // This is the listener for sending a text prompt
        binding.sendButton.setOnClickListener {
            val prompt = binding.promptEditText.text.toString().trim()
            if (prompt.isNotEmpty()) {
                viewModel.sendMessage(prompt, selectedModel)
                binding.promptEditText.text.clear()
            }
        }

        // This is the listener for the microphone
        binding.micButton.setOnClickListener {
            if (isRecording) {
                stopRecordingAndTranscribe()
            } else {
                requestAudioPermissionAndStartRecording()
            }
        }
    }

    // THIS IS THE FUNCTION THAT WAS LIKELY BROKEN.
    // This block listens for changes from the ViewModel and updates the UI.
    private fun observeViewModel() {
        viewModel.chatHistory.observe(this) { history ->
            Log.d(TAG, "Chat history updated. New message count: ${history.size}")
            // This line is what puts the messages on the screen.
            chatAdapter.submitList(history)
            binding.chatRecyclerView.scrollToPosition(chatAdapter.itemCount - 1)
        }

        viewModel.isLoading.observe(this) { isLoading ->
            binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
        }
    }

    // --- All Whisper and MediaRecorder Functions Below ---

    private fun requestAudioPermissionAndStartRecording() {
        if (ContextCompat.checkSelfPermission(
                this, Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.RECORD_AUDIO),
                RECORD_AUDIO_PERMISSION_CODE
            )
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        try {
            audioFile = File(cacheDir, "audio.mp4")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            isRecording = true
            binding.promptEditText.hint = "Recording... Press stop to finish."
            binding.micButton.setImageResource(R.drawable.ic_stop)
        } catch (e: IOException) {
            Log.e(TAG, "startRecording failed", e)
        }
    }

    private fun stopRecordingAndTranscribe() {
        mediaRecorder?.apply { stop(); release() }
        mediaRecorder = null
        isRecording = false
        binding.promptEditText.hint = "Transcribing..."
        binding.micButton.setImageResource(android.R.drawable.ic_btn_speak_now)
        audioFile?.let { if (it.exists() && it.length() > 0) transcribeAudioWithWhisper(it) }
    }

    private fun transcribeAudioWithWhisper(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Main) { binding.progressBar.visibility = View.VISIBLE }
            try {
                val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val model = "whisper-1".toRequestBody("text/plain".toMediaTypeOrNull())
                val apiKey = "Bearer ${BuildConfig.OPENAI_API_KEY}"
                val response = RetrofitClient.instance.transcribeAudio(apiKey, body, model)
                withContext(Main) {
                    val existingText = binding.promptEditText.text.toString()
                    val newText = response.text
                    val combinedText = if (existingText.isBlank()) newText else "$existingText $newText"
                    binding.promptEditText.setText(combinedText)
                    binding.promptEditText.setSelection(combinedText.length)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Whisper transcription failed", e)
                withContext(Main) { Toast.makeText(this@MainActivity, "Transcription failed", Toast.LENGTH_LONG).show() }
            } finally {
                withContext(Main) {
                    binding.progressBar.visibility = View.GONE
                    binding.promptEditText.hint = "Type your prompt here..."
                }
                file.delete()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaRecorder?.release()
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, modelOptions)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.modelSpinner.adapter = adapter
        binding.modelSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { selectedModel = modelOptions[pos] }
            override fun onNothingSelected(p: AdapterView<*>?) {}
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (code == RECORD_AUDIO_PERMISSION_CODE) {
            if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) startRecording()
            else Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
        }
    }
}