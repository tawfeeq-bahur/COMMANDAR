package com.example.data.remote

import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class Part(
    @Json(name = "text") val text: String? = null
)

@JsonClass(generateAdapter = true)
data class Content(
    @Json(name = "parts") val parts: List<Part>
)

@JsonClass(generateAdapter = true)
data class GenerateContentRequest(
    @Json(name = "contents") val contents: List<Content>,
    @Json(name = "systemInstruction") val systemInstruction: Content? = null
)

@JsonClass(generateAdapter = true)
data class Candidate(
    @Json(name = "content") val content: Content?
)

@JsonClass(generateAdapter = true)
data class GenerateContentResponse(
    @Json(name = "candidates") val candidates: List<Candidate>?
)

interface GeminiApiService {
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GenerateContentRequest
    ): GenerateContentResponse
}

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com/"

    private val moshi = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    val service: GeminiApiService = retrofit.create(GeminiApiService::class.java)

    /**
     * Generates a context-aware smart reply using Gemini 3.5 Flash.
     */
    suspend fun generateSmartReply(statusMessage: String, incomingMessage: String): String {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return "Auto-Reply: I am currently busy. (API Key not configured)"
        }

        val promptText = "The user is currently in this status: \"$statusMessage\". " +
                "They received this incoming text message: \"$incomingMessage\". " +
                "Generate a short, polite, direct, first-person auto-reply of 1-2 sentences. " +
                "Just output the reply text. Do not include any prefix, quotes, or explanations."

        val request = GenerateContentRequest(
            contents = listOf(
                Content(parts = listOf(Part(text = promptText)))
            ),
            systemInstruction = Content(
                parts = listOf(Part(text = "You are an automated auto-reply assistant. Generate only the reply message itself."))
            )
        )

        return try {
            val response = service.generateContent(apiKey, request)
            val generatedText = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
            generatedText?.trim() ?: "Hi! I am currently unavailable. I will get back to you soon."
        } catch (e: Exception) {
            e.printStackTrace()
            "Hi! I'm currently unavailable: $statusMessage"
        }
    }
}
