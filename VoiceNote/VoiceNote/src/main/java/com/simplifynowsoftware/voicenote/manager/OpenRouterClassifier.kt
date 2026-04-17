package com.simplifynowsoftware.voicenote.manager

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * OpenRouterClassifier - Phân loại ghi chú bằng OpenRouter API
 */
class OpenRouterClassifier(private val settingsManager: SettingsManager) {
    private val TAG = "OpenRouterAI"

    private val apiService: OpenRouterApiService by lazy {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val client = OkHttpClient.Builder()
            .addInterceptor(logging)
            .build()

        Retrofit.Builder()
            .baseUrl(OpenRouterApiService.BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .client(client)
            .build()
            .create(OpenRouterApiService::class.java)
    }

    suspend fun classifyNote(content: String, folders: List<String>, tags: List<String>): AiClassificationResult = withContext(Dispatchers.IO) {
        val apiKey = settingsManager.openRouterApiKeyFlow.first()
        val modelId = settingsManager.selectedModelIdFlow.first()

        AiLogManager.log("🤖 Bắt đầu phân loại với model: $modelId")

        if (apiKey.isBlank()) {
            val errorMsg = "❌ API Key trống!"
            Log.e(TAG, errorMsg)
            AiLogManager.log(errorMsg)
            return@withContext AiClassificationResult()
        }

        try {
            val prompt = """
                Bạn là chuyên gia Zettelkasten. Phân loại ghi chú sau.
                DANH SÁCH THƯ MỤC: [${folders.joinToString()}]
                DANH SÁCH THẺ: [${tags.joinToString()}]
                
                Trả về DUY NHẤT JSON: {"folder": "...", "tags": ["...", "..."]}
                
                NỘI DUNG: "$content"
            """.trimIndent()

            val request = ChatRequest(
                model = modelId,
                messages = listOf(ChatMessage(role = "user", content = prompt)),
                responseFormat = ResponseFormat(type = "json_object")
            )

            val response = apiService.getChatCompletions(
                auth = "Bearer $apiKey",
                referer = OpenRouterApiService.APP_REFERER,
                title = OpenRouterApiService.APP_TITLE,
                request = request
            )

            if (response.isSuccessful) {
                val rawText = response.body()?.choices?.firstOrNull()?.message?.content ?: ""
                AiLogManager.log("✅ Phản hồi từ AI: $rawText")
                return@withContext parseResponse(rawText)
            } else {
                val errorBody = response.errorBody()?.string() ?: "No error body"
                val errorLog = "❌ Lỗi API: ${response.code()} - $errorBody"
                Log.e(TAG, errorLog)
                AiLogManager.log(errorLog)
                return@withContext AiClassificationResult()
            }
        } catch (e: Exception) {
            val exLog = "❌ Exception: ${e.message}"
            Log.e(TAG, exLog)
            AiLogManager.log(exLog)
            return@withContext AiClassificationResult()
        }
    }

    private fun parseResponse(rawResponse: String): AiClassificationResult {
        return try {
            val cleanJson = Regex("""\{.*\}""", RegexOption.DOT_MATCHES_ALL)
                .find(rawResponse)?.value ?: rawResponse
            val json = JSONObject(cleanJson)
            val folder = json.optString("folder", "Default")
            val tagsList = mutableListOf<String>()
            json.optJSONArray("tags")?.let { arr ->
                for (i in 0 until arr.length()) tagsList.add(arr.getString(i))
            }
            AiClassificationResult(folder, tagsList)
        } catch (e: Exception) {
            AiClassificationResult()
        }
    }
    
    suspend fun fetchModels(): List<OpenRouterModel> = withContext(Dispatchers.IO) {
        AiLogManager.log("🌐 Đang tải danh sách model từ OpenRouter...")
        try {
            val response = apiService.getModels()
            if (response.isSuccessful) {
                val allModels = response.body()?.data ?: emptyList()
                AiLogManager.log("✅ Đã tải ${allModels.size} models")
                Log.d(TAG, "Fetched ${allModels.size} models from OpenRouter")
                // Lọc theo nhà cung cấp yêu cầu
                allModels.filter { model ->
                    model.id.contains("deepseek/") || 
                    model.id.contains("google/") || 
                    model.id.contains("qwen/") || 
                    model.id.contains("minimax/")
                }
            } else {
                val errorLog = "❌ Lỗi fetchModels: ${response.code()}"
                Log.e(TAG, errorLog)
                AiLogManager.log(errorLog)
                emptyList()
            }
        } catch (e: Exception) {
            val exLog = "❌ Exception fetchModels: ${e.message}"
            Log.e(TAG, exLog)
            AiLogManager.log(exLog)
            emptyList()
        }
    }
}
