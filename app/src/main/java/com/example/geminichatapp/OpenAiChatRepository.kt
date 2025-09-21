package com.example.geminichatapp

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import okio.IOException

class OpenAiChatRepository(
    private val modelName: String,
    private val chatDao: ChatDao
) : ChatRepository {

    override fun sendMessageStream(sessionId: Long, prompt: String): Flow<String> = channelFlow {
        val history = chatDao.getMessagesForSession(sessionId).first()
            .map {
                val role = if (it.role == Role.USER) "user" else "assistant"
                ChatMessageJson(role = role, content = it.text)
            }

        val request = OpenAiChatRequest(model = modelName, messages = history)
        val apiKey = "Bearer ${BuildConfig.OPENAI_API_KEY}"

        try {
            val responseBody = RetrofitClient.instance.getChatCompletionsStream(apiKey, request)

            // --- THIS IS THE KEY CHANGE ---
            // We now use the raw byte source from OkHttp (via Okio)
            // This is the most direct way to read a stream and avoids buffering.
            val source = responseBody.source()
            val gson = Gson()

            // Loop until the network stream is fully consumed
            while (!source.exhausted()) {
                // Read one line from the raw stream
                val line = source.readUtf8Line()

                if (line?.startsWith("data:") == true) {
                    val jsonString = line.substring(5).trim()

                    if (jsonString == "[DONE]") {
                        Log.d("OpenAiChatRepository", "Stream finished");
                        break // Stream finished
                    }

                    try {
                        val chunk = gson.fromJson(jsonString, OpenAiChatResponse::class.java)
                        chunk.choices.firstOrNull()?.delta?.content?.let {
                            Log.d("OpenAiChatRepository", "received res: "+it);
                            // Send the text chunk into our Flow
                            send(it)
                        }
                    } catch (e: Exception) {
                        Log.e("OpenAiChatRepository", "Error parsing stream chunk: $jsonString", e)
                    }
                }
            }
            // Close the source when we're done
            source.close()

        } catch (e: Exception) {
            Log.e("OpenAiChatRepository", "API Stream Error", e)
            if (e is IOException) {
                send("Error: Network issue. Please check your connection.")
            } else {
                send("Error: ${e.message}")
            }
        }
    }
}