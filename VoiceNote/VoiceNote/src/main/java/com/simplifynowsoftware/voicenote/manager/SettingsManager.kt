package com.simplifynowsoftware.voicenote.manager

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsManager(private val context: Context) {

    /**
     * MasterKey: Là "khóa mẹ" dùng để mã hóa các khóa con khác.
     * Khóa này được lưu trữ trong Android Keystore - một vùng an toàn phần cứng (TEE/SE)
     * mà ngay cả hệ điều hành Android đã root cũng cực kỳ khó trích xuất được.
     */
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    /**
     * EncryptedSharedPreferences: Tự động mã hóa tên Key (AES256_SIV) 
     * và giá trị Value (AES256_GCM) trước khi lưu vào tệp XML trên đĩa.
     */
    private val encryptedPrefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    // StateFlow để thông báo thay đổi API Key cho UI mà không cần đọc lại SharedPreferences liên tục
    private val _apiKeyUpdateSignal = MutableStateFlow(System.currentTimeMillis())

    companion object {
        val AI_PROMPT_TEMPLATE = stringPreferencesKey("ai_prompt_template")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        private const val KEY_API_KEY = "open_router_api_key"
        
        const val DEFAULT_PROMPT_TEMPLATE = """
Nhiệm vụ: Bạn là hệ thống phân loại nội dung. Trả về DUY NHẤT một chuỗi JSON hợp lệ, không có markdown (như ```json), không giải thích, không chat.
Nếu không có dữ liệu phù hợp, hãy để mảng rỗng.

Danh mục Folder cho phép: {{FOLDER_LIST}}
Danh mục Tag cho phép: {{TAG_LIST}}

Xử lý Input sau và chỉ trả về JSON:
Input: "{{NOTE_CONTENT}}"
Output:
        """
    }

    // --- DATASTORE (Thông tin không nhạy cảm) ---
    val promptTemplateFlow: Flow<String> = context.dataStore.data
        .map { it[AI_PROMPT_TEMPLATE] ?: DEFAULT_PROMPT_TEMPLATE }

    val selectedModelIdFlow: Flow<String> = context.dataStore.data
        .map { it[SELECTED_MODEL_ID] ?: "deepseek/deepseek-chat" }

    suspend fun updatePromptTemplate(template: String) {
        context.dataStore.edit { it[AI_PROMPT_TEMPLATE] = template }
    }

    suspend fun updateSelectedModelId(modelId: String) {
        context.dataStore.edit { it[SELECTED_MODEL_ID] = modelId }
    }

    // --- ENCRYPTED SHARED PREFERENCES (API Key - Tuyệt mật) ---
    
    /**
     * Trả về API Key trực tiếp (Synchronous). 
     * Sử dụng khi cần lấy Key ngay lập tức để gọi API.
     */
    fun getOpenRouterApiKey(): String? {
        return encryptedPrefs.getString(KEY_API_KEY, null)
    }

    /**
     * Cung cấp Flow để quan sát sự thay đổi của API Key (Tương thích với UI/ViewModel).
     */
    val openRouterApiKeyFlow: Flow<String> = _apiKeyUpdateSignal.map {
        getOpenRouterApiKey() ?: ""
    }

    suspend fun updateOpenRouterApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        encryptedPrefs.edit().putString(KEY_API_KEY, apiKey).apply()
        _apiKeyUpdateSignal.value = System.currentTimeMillis() // Kích hoạt Flow cập nhật
    }
}
