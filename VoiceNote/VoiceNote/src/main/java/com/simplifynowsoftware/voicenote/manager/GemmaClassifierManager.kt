package com.simplifynowsoftware.voicenote.manager

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

data class GemmaClassificationResult(
    val folder: String = "Default",
    val tags: List<String> = emptyList()
)

/**
 * GemmaClassifierManager - Chuyên gia Zettelkasten On-device (MediaPipe Gemma)
 */
class GemmaClassifierManager(private val context: Context) {
    private val TAG = "GemmaAI"
    private var llmInference: LlmInference? = null

    /**
     * Khởi tạo LLM Inference từ đường dẫn file .task
     */
    fun initialize(modelPath: String) {
        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Log.e(TAG, "❌ Không tìm thấy file model Gemma tại: $modelPath")
                return
            }

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(modelPath)
                .setMaxTokens(512)
                .setTemperature(0.1f)
                .setTopK(40)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            Log.d(TAG, "🚀 Gemma On-device đã sẵn sàng (Model: ${modelFile.name})")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Lỗi khởi tạo Gemma: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Phân loại ghi chú (Chạy trên Background Thread)
     */
    suspend fun classifyNote(
        content: String,
        folders: List<String>,
        tags: List<String>
    ): GemmaClassificationResult = withContext(Dispatchers.Default) {
        val inference = llmInference ?: return@withContext GemmaClassificationResult()

        try {
            val prompt = """
                <start_of_turn>user
                Bạn là một thủ thư Zettelkasten thông minh. Nhiệm vụ của bạn là phân tích nội dung ghi chú và trả về JSON để phân loại.
                
                DANH SÁCH THƯ MỤC CHO PHÉP: [${folders.joinToString()}]
                DANH SÁCH THẺ CHO PHÉP: [${tags.joinToString()}]

                YÊU CẦU:
                1. Trả về DUY NHẤT một khối JSON.
                2. Không kèm theo lời giải thích.
                3. Nếu không chắc chắn, hãy chọn thư mục "Inbox".

                VÍ DỤ:
                Nội dung: "Nhắc tôi mua sữa và trứng vào chiều nay"
                JSON: {"folder": "Công việc", "tags": ["Mua sắm", "Ghi chú nhanh"]}

                NỘI DUNG CẦN XỬ LÝ:
                "$content"
                <end_of_turn>
                <start_of_turn>model
                JSON:
            """.trimIndent()

            Log.d(TAG, "📤 Gửi Prompt: $prompt")
            
            // Gọi inference (Lưu ý: MediaPipe GenAI hiện tại dùng API đồng bộ trong thread)
            val response = inference.generateResponse(prompt)
            
            Log.d(TAG, "📥 Phản hồi thô: $response")

            val jsonString = extractJsonString(response)
            Log.d(TAG, "📦 JSON sau lọc: $jsonString")

            return@withContext parseJson(jsonString)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Lỗi khi Gemma phân tích: ${e.message}")
            e.printStackTrace()
            GemmaClassificationResult()
        }
    }

    private fun extractJsonString(response: String): String {
        return try {
            val startIndex = response.indexOf("{")
            val endIndex = response.lastIndexOf("}")
            if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                response.substring(startIndex, endIndex + 1)
            } else {
                "{}"
            }
        } catch (e: Exception) {
            "{}"
        }
    }

    private fun parseJson(jsonString: String): GemmaClassificationResult {
        return try {
            val json = JSONObject(jsonString)
            val folder = json.optString("folder", "Default")
            val tagsArray = json.optJSONArray("tags")
            val tagsList = mutableListOf<String>()
            tagsArray?.let {
                for (i in 0 until it.length()) {
                    tagsList.add(it.getString(i))
                }
            }
            GemmaClassificationResult(folder, tagsList)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Lỗi parse JSON: $jsonString")
            GemmaClassificationResult()
        }
    }

    fun release() {
        llmInference?.close()
        llmInference = null
    }
}
