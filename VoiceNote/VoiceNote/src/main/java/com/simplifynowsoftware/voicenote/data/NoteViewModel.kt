package com.simplifynowsoftware.voicenote.data

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.simplifynowsoftware.voicenote.manager.OpenRouterClassifier
import com.simplifynowsoftware.voicenote.manager.OpenRouterModel
import com.simplifynowsoftware.voicenote.manager.SettingsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    val allNotes: LiveData<List<Note>>
    
    private val settingsManager = SettingsManager(application)
    // Chuyển sang OpenRouter API
    private val openRouterClassifier = OpenRouterClassifier(settingsManager)

    // State báo hiệu AI đang xử lý
    private val _isAiProcessing = MutableStateFlow(false)
    val isAiProcessing: StateFlow<Boolean> = _isAiProcessing.asStateFlow()

    // Sự kiện kết quả AI để hiển thị Toast ở UI
    private val _aiResultEvent = MutableStateFlow<String?>(null)
    val aiResultEvent: StateFlow<String?> = _aiResultEvent.asStateFlow()

    // Danh sách model khả dụng
    private val _availableModels = MutableStateFlow<List<OpenRouterModel>>(emptyList())
    val availableModels: StateFlow<List<OpenRouterModel>> = _availableModels.asStateFlow()

    // Provider Management
    private val _selectedProvider = MutableStateFlow("All")
    val selectedProvider: StateFlow<String> = _selectedProvider.asStateFlow()

    private val _availableProviders = MutableStateFlow<List<String>>(listOf("All"))
    val availableProviders: StateFlow<List<String>> = _availableProviders.asStateFlow()

    // Danh sách model sau khi lọc và sắp xếp
    val displayedModels: StateFlow<List<OpenRouterModel>> = combine(
        _availableModels,
        _selectedProvider
    ) { models, provider ->
        val filtered = if (provider == "All") {
            models
        } else {
            models.filter { it.id.startsWith("${provider.lowercase()}/") }
        }
        filtered.sortedByDescending { it.created }
    }.asStateFlow(viewModelScope, emptyList())

    init {
        val noteDao = AppDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(noteDao)
        allNotes = repository.allNotes.asLiveData()
        
        // Tự động tải danh sách model khi khởi tạo
        fetchAvailableModels()
    }

    /**
     * Tải danh sách các model từ OpenRouter
     */
    fun fetchAvailableModels() = viewModelScope.launch {
        val models = openRouterClassifier.fetchModels()
        _availableModels.value = models
        
        // Cập nhật danh sách Provider
        val providers = models.map { it.id.substringBefore("/") }
            .distinct()
            .map { it.replaceFirstChar { char -> if (char.isLowerCase()) char.titlecase(Locale.getDefault()) else char.toString() } }
            .sorted()
        _availableProviders.value = listOf("All") + providers
    }

    fun setSelectedProvider(provider: String) {
        _selectedProvider.value = provider
    }

    private fun <T> kotlinx.coroutines.flow.Flow<T>.asStateFlow(
        scope: kotlinx.coroutines.CoroutineScope,
        initialValue: T
    ): StateFlow<T> = this.stateIn(
        scope,
        SharingStarted.WhileSubscribed(5000),
        initialValue
    )

    fun getNoteById(id: Int): LiveData<Note?> {
        return repository.getNoteById(id).asLiveData()
    }

    /**
     * Hàm Insert mới tích hợp AI Classification
     */
    /**
     * Hàm Insert mới tích hợp OpenRouter AI
     */
    fun insertWithAi(note: Note, decryptedContent: String, onComplete: (Note) -> Unit = {}) = viewModelScope.launch {
        _isAiProcessing.value = true
        val startMsg = "🚀 Bắt đầu quy trình AI cho ghi chú: ${note.title}"
        Log.i("OpenRouterAI", startMsg)
        com.simplifynowsoftware.voicenote.manager.AiLogManager.log(startMsg)
        
        try {
            // 1. Lấy dữ liệu ngữ cảnh
            val existingNotes = allNotes.value ?: emptyList()
            val folders = existingNotes.mapNotNull { it.folder }.distinct()
            val tags = existingNotes.flatMap { 
                try { org.json.JSONArray(it.tags ?: "[]").let { arr -> 
                    List(arr.length()) { i -> arr.getString(i) } 
                } } catch (e: Exception) { emptyList<String>() }
            }.distinct().take(30)

            // 2. Gọi OpenRouter phân loại
            val aiResult = openRouterClassifier.classifyNote(decryptedContent, folders, tags)

            // 3. Cập nhật Note với thông tin AI
            val processedNote = note.copy(
                folder = aiResult.folder,
                tags = org.json.JSONArray(aiResult.tags).toString()
            )

            // 4. Lưu vào Database (Sử dụng NonCancellable để đảm bảo không bị dừng giữa chừng)
            val finalNote = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val newId = repository.insert(processedNote)
                processedNote.copy(id = newId.toInt())
            }
            
            val saveMsg = "💾 Đã lưu vào DB với ID: ${finalNote.id}"
            Log.i("OpenRouterAI", saveMsg)
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log(saveMsg)
            
            _aiResultEvent.value = "AI (${aiResult.folder}) đã được áp dụng"
            onComplete(finalNote)
            
        } catch (e: Exception) {
            val errorMsg = "❌ Lỗi insertWithAi: ${e.localizedMessage}"
            Log.e("OpenRouterAI", errorMsg)
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log(errorMsg)
            
            // Lưu note gốc bằng NonCancellable
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                val newId = repository.insert(note)
                onComplete(note.copy(id = newId.toInt()))
            }
        } finally {
            _isAiProcessing.value = false
        }
    }

    fun insert(note: Note) = viewModelScope.launch { repository.insert(note) }
    fun update(note: Note) = viewModelScope.launch { repository.update(note) }
    fun delete(note: Note) = viewModelScope.launch { repository.delete(note) }
    
    fun clearAiEvent() { _aiResultEvent.value = null }
}
