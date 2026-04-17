package com.simplifynowsoftware.voicenote.manager

import android.content.Context
import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

data class AiClassificationResult(
    val folder: String = "Default",
    val tags: List<String> = emptyList(),
    val links: List<String> = emptyList()
)

/**
 * AiClassifierManager - Chuyển sang sử dụng Gemini Cloud API
 */
class AiClassifierManager(private val context: Context) {
    private val TAG = "VaultNoteAI"
    
    // API Key được quản lý qua Settings
    private val GEMINI_API_KEY = "REMOVED_FOR_SECURITY"

    enum class AiStatus { IDLE, READY, ERROR }
    private val _status = MutableStateFlow(AiStatus.IDLE)
    val status: StateFlow<AiStatus> = _status.asStateFlow()

    private var generativeModel: GenerativeModel? = null

    init {
        setupCloudAI()
    }

    private fun setupCloudAI() {
        if (GEMINI_API_KEY == "YOUR_API_KEY_HERE") {
            AiLogManager.log("⚠️ Chú ý: Chưa điền Gemini API Key.")
            _status.value = AiStatus.ERROR
            return
        }

        try {
            generativeModel = GenerativeModel(
                modelName = "gemini-2.0-flash",
                apiKey = GEMINI_API_KEY,
                generationConfig = generationConfig {
                    temperature = 0.1f
                    topK = 32
                    topP = 1f
                    responseMimeType = "application/json"
                }
            )
            _status.value = AiStatus.READY
            AiLogManager.log("🚀 Gemini Cloud đã sẵn sàng!")
        } catch (e: Exception) {
            Log.e(TAG, "❌ LỖI KHỞI TẠO CLOUD AI: ${e.message}")
            Log.e(TAG, "🔍 Loại lỗi: ${e.javaClass.simpleName}")
            Log.e(TAG, "Nguyên nhân gốc (Cause): ${e.cause?.message}")
            e.printStackTrace()
            
            AiLogManager.log("❌ Lỗi khởi tạo Cloud AI: ${e.message}")
            _status.value = AiStatus.ERROR
        }
    }

    suspend fun classifyNote(
        content: String,
        template: String,
        folders: List<String>,
        tags: List<String>
    ): AiClassificationResult = withContext(Dispatchers.IO) {
        AiLogManager.log("📝 Đang gửi yêu cầu phân loại lên Cloud...")
        
        if (generativeModel == null) setupCloudAI()
        val model = generativeModel ?: return@withContext AiClassificationResult()

        try {
            val prompt = """
                Bạn là một chuyên gia Zettelkasten. Nhiệm vụ của bạn là phân loại ghi chú.
                Hãy trả về kết quả dưới dạng JSON duy nhất, không có markdown.
                
                DANH SÁCH THƯ MỤC CHO PHÉP: [${folders.joinToString()}]
                DANH SÁCH THẺ CHO PHÉP: [${tags.joinToString()}]

                NỘI DUNG GHI CHÚ:
                "$content"

                JSON OUTPUT FORMAT:
                {"folder": "tên_thư_mục", "tags": ["thẻ1", "thẻ2"]}
            """.trimIndent()

            val response = model.generateContent(prompt)
            val resultText = response.text ?: ""
            
            return@withContext parseResponse(resultText)
        } catch (e: Exception) {
            Log.e(TAG, "❌ LỖI CLOUD INFERENCE: ${e.message}")
            Log.e(TAG, "🔍 Loại lỗi: ${e.javaClass.simpleName}")
            Log.e(TAG, "Nguyên nhân gốc (Cause): ${e.cause?.message}")
            e.printStackTrace()

            AiLogManager.log("❌ Lỗi Cloud Inference: ${e.message}")
            AiClassificationResult()
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

            AiLogManager.log("✅ Phân loại xong: $folder")
            AiClassificationResult(folder, tagsList)
        } catch (e: Exception) {
            Log.e(TAG, "Parse error: $rawResponse")
            AiClassificationResult()
        }
    }

    fun importModelFromUri(uri: android.net.Uri): Boolean = true
}
