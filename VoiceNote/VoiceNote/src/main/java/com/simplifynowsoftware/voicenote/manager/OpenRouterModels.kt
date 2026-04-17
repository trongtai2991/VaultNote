package com.simplifynowsoftware.voicenote.manager

import com.google.gson.annotations.SerializedName

/**
 * Data models cho OpenRouter API (Tương thích OpenAI)
 */

// --- Model List Objects ---
data class ModelListResponse(
    val data: List<OpenRouterModel>
)

data class OpenRouterModel(
    val id: String,
    val name: String,
    val description: String? = null,
    val created: Long = 0,
    val pricing: ModelPricing? = null
)

data class ModelPricing(
    val prompt: String,     // Giá input
    val completion: String  // Giá output
)

// Extension function để format giá tiền sang 1M tokens
fun String.toPricePerMillion(): String {
    return try {
        val price = this.toDouble()
        if (price == 0.0) return "Free"
        val perMillion = price * 1_000_000
        // Làm tròn 2 chữ số thập phân nếu cần
        if (perMillion < 0.01) {
            String.format("$%.4f/1M", perMillion)
        } else {
            String.format("$%.2f/1M", perMillion)
        }
    } catch (e: Exception) {
        "N/A"
    }
}

// --- Chat Completion Objects ---
data class ChatRequest(
    val model: String,
    val messages: List<ChatMessage>,
    @SerializedName("response_format") val responseFormat: ResponseFormat? = null
)

data class ChatMessage(
    val role: String,
    val content: String
)

data class ResponseFormat(
    val type: String // "json_object"
)

data class ChatResponse(
    val choices: List<Choice>,
    val error: OpenRouterError? = null
)

data class Choice(
    val message: ChatMessage
)

data class OpenRouterError(
    val message: String,
    val code: Int? = null
)
