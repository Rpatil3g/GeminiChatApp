package com.example.geminichatapp

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface OpenAiApiService {
    @Multipart
    @POST("v1/audio/transcriptions")
    suspend fun transcribeAudio(
        @Header("Authorization") apiKey: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody
    ): WhisperResponse
}