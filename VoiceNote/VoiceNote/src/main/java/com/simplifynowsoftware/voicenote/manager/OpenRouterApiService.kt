package com.simplifynowsoftware.voicenote.manager

import retrofit2.Response
import retrofit2.http.*

/**
 * Interface Retrofit cho OpenRouter API
 */
interface OpenRouterApiService {

    @GET("api/v1/models")
    suspend fun getModels(): Response<ModelListResponse>

    @POST("api/v1/chat/completions")
    suspend fun getChatCompletions(
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String,
        @Header("X-Title") title: String,
        @Body request: ChatRequest
    ): Response<ChatResponse>

    companion object {
        const val BASE_URL = "https://openrouter.ai/"
        const val APP_REFERER = "https://github.com/simplifynowsoftware/voicenote"
        const val APP_TITLE = "VaultNote"
    }
}
