package com.example.geminichatapp

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://api.openai.com/"

    val instance: OpenAiApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        val httpClient = OkHttpClient.Builder()
           // .addInterceptor(logging)
            // --- ADD THESE THREE LINES ---
            // Increase the time to establish a connection to 60 seconds
            .connectTimeout(60, TimeUnit.SECONDS)
            // Increase the time to read data from the server to 60 seconds
            .readTimeout(60, TimeUnit.SECONDS)
            // Increase the time to write data to the server to 60 seconds
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()

        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(OpenAiApiService::class.java)
    }
}