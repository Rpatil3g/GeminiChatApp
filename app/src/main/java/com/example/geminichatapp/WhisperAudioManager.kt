package com.example.geminichatapp

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException

class WhisperAudioManager(
    private val context: Context,
    private val lifecycleScope: LifecycleCoroutineScope,
    private val onResult: (String) -> Unit,
    private val onStateChange: (Boolean, String) -> Unit // isRecording, hintText
) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFile: File? = null
    private var _isRecording = false
    val isRecording: Boolean get() = _isRecording

    fun start() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(context, "Microphone permission is required.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            audioFile = File(context.cacheDir, "audio.mp4")
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()

            mediaRecorder?.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(audioFile?.absolutePath)
                prepare()
                start()
            }
            _isRecording = true
            onStateChange(true, "Recording... Press stop to finish.")
            Log.d("WhisperAudioManager", "Recording started.")
        } catch (e: IOException) {
            Log.e("WhisperAudioManager", "startRecording failed", e)
        }
    }

    fun stop() {
        if (!_isRecording) return
        mediaRecorder?.apply { stop(); release() }
        mediaRecorder = null
        _isRecording = false
        onStateChange(false, "Transcribing...")
        Log.d("WhisperAudioManager", "Recording stopped.")

        audioFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                transcribe(file)
            } else {
                onStateChange(false, "Type your prompt here...")
            }
        }
    }

    private fun transcribe(file: File) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestFile = file.asRequestBody("audio/mp4".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", file.name, requestFile)
                val model = "whisper-1".toRequestBody("text/plain".toMediaTypeOrNull())
                val apiKey = "Bearer ${BuildConfig.OPENAI_API_KEY}"
                val response = RetrofitClient.instance.transcribeAudio(apiKey, body, model)
                withContext(Dispatchers.Main) {
                    onResult(response.text)
                }
            } catch (e: Exception) {
                Log.e("WhisperAudioManager", "Whisper transcription failed", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Transcription failed", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    onStateChange(false, "Type your prompt here...")
                }
                file.delete()
            }
        }
    }

    fun release() {
        mediaRecorder?.release()
        mediaRecorder = null
    }
}