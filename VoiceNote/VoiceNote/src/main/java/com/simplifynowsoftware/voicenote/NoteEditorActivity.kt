package com.simplifynowsoftware.voicenote

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.simplifynowsoftware.voicenote.data.CryptoManager
import com.simplifynowsoftware.voicenote.data.Note
import com.simplifynowsoftware.voicenote.data.NoteViewModel
import org.json.JSONArray

class NoteEditorActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private var currentNoteId: Int = 0
    private var isEditMode: Boolean = false
    private var originalNote: Note? = null
    
    private lateinit var editContent: EditText
    private lateinit var editTitle: EditText
    private lateinit var languageToggleGroup: MaterialButtonToggleGroup
    private lateinit var voiceButton: MaterialButton
    private lateinit var voiceProgress: ProgressBar
    
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private val cryptoManager = CryptoManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_note_editor)

        com.simplifynowsoftware.voicenote.manager.AiLogManager.log("📝 Mở trình soạn thảo ghi chú")

        viewModel = ViewModelProvider(this).get(NoteViewModel::class.java)

        editTitle = findViewById(R.id.edit_note_title)
        editContent = findViewById(R.id.edit_note_content)
        val saveButton = findViewById<Button>(R.id.button_save)
        voiceButton = findViewById(R.id.button_voice)
        voiceProgress = findViewById(R.id.voice_progress)
        languageToggleGroup = findViewById(R.id.language_toggle_group)

        // Thiết lập ngôn ngữ mặc định từ Settings
        val prefs = getSharedPreferences("VaultSettings", android.content.Context.MODE_PRIVATE)
        val defaultLang = prefs.getString("default_language", "vi-VN")
        if (defaultLang == "en-US") {
            languageToggleGroup.check(R.id.btn_lang_en)
        } else {
            languageToggleGroup.check(R.id.btn_lang_vi)
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
        speechRecognizer?.setRecognitionListener(createRecognitionListener())

        currentNoteId = intent.getIntExtra("NOTE_ID", 0)
        if (currentNoteId != 0) {
            isEditMode = true
            viewModel.getNoteById(currentNoteId).observe(this) { note ->
                note?.let {
                    originalNote = it
                    if (editTitle.text.isEmpty() && editContent.text.isEmpty()) {
                        editTitle.setText(it.title)
                        val decryptedContent = if (!it.iv.isNullOrEmpty()) {
                            try {
                                cryptoManager.decrypt(it.content, it.iv)
                            } catch (e: Exception) {
                                "Lỗi giải mã: Dữ liệu có thể bị hỏng"
                            }
                        } else {
                            it.content
                        }
                        editContent.setText(decryptedContent)
                    }
                }
            }
        }

        voiceButton.setOnClickListener {
            if (isListening) stopVoiceInput() else checkPermissionAndStart()
        }

        saveButton.setOnClickListener {
            saveNote(editTitle.text.toString(), editContent.text.toString())
        }
    }

    private fun saveNote(title: String, content: String) {
        if (title.isNotEmpty() || content.isNotEmpty()) {
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log("💾 Đang chuẩn bị lưu ghi chú: $title")
            val encryptedData = cryptoManager.encrypt(content)
            val noteToSave = if (isEditMode) {
                originalNote?.copy(
                    title = title,
                    content = encryptedData.ciphertext,
                    iv = encryptedData.iv,
                    updatedAt = System.currentTimeMillis()
                ) ?: Note(id = currentNoteId, title = title, content = encryptedData.ciphertext, iv = encryptedData.iv)
            } else {
                Note(title = title, content = encryptedData.ciphertext, iv = encryptedData.iv)
            }

            if (isEditMode) {
                viewModel.update(noteToSave)
                checkAndAutoExport(noteToSave)
                finish()
            } else {
                Log.i("OpenRouterAI", "📝 Đang gọi insertWithAi cho note mới...")
                // Sử dụng hàm insertWithAi và thực hiện Export sau khi AI xong
                viewModel.insertWithAi(noteToSave, content) { processedNote ->
                    Log.i("OpenRouterAI", "🎯 Callback onComplete nhận được Note ID: ${processedNote.id}")
                    checkAndAutoExport(processedNote)
                    runOnUiThread { finish() }
                }
            }
        }
    }

    private fun checkAndAutoExport(note: Note) {
        val prefs = getSharedPreferences("VaultSettings", android.content.Context.MODE_PRIVATE)
        val isEnabled = prefs.getBoolean("auto_export_enabled", false)
        val uriString = prefs.getString("vault_uri", null)
        
        val statusMsg = "📤 Kiểm tra Auto-Export: Bật=$isEnabled, Đã chọn Vault=${uriString != null}"
        Log.i("OpenRouterAI", statusMsg)
        com.simplifynowsoftware.voicenote.manager.AiLogManager.log(statusMsg)
        
        if (isEnabled && uriString != null) {
            val uri = android.net.Uri.parse(uriString)
            // Sử dụng applicationContext để tránh leak và đảm bảo ghi file khi Activity đóng
            val exporter = com.simplifynowsoftware.voicenote.export.ObsidianExporter(applicationContext)
            val success = exporter.exportNoteToMarkdown(uri, note)
            val resultMsg = "📤 Kết quả Export: ${if(success) "THÀNH CÔNG" else "THẤT BẠI"}"
            Log.i("OpenRouterAI", resultMsg)
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log(resultMsg)
        } else if (!isEnabled) {
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log("⚠️ Auto-Export đang TẮT trong Cài đặt")
        } else {
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log("⚠️ Chưa chọn thư mục Vault trong Cài đặt")
        }
    }

    private fun checkPermissionAndStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 101)
        } else {
            startVoiceInput()
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        val selectedLang = if (languageToggleGroup.checkedButtonId == R.id.btn_lang_en) "en-US" else "vi-VN"
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, selectedLang)
        speechRecognizer?.startListening(intent)
        setRecordingState(true)
        com.simplifynowsoftware.voicenote.manager.AiLogManager.log("🎤 Bắt đầu nhận diện giọng nói ($selectedLang)")
    }

    private fun stopVoiceInput() {
        speechRecognizer?.stopListening()
        setRecordingState(false)
    }

    private fun setRecordingState(isRecording: Boolean) {
        isListening = isRecording
        if (isRecording) {
            voiceButton.iconTint = ColorStateList.valueOf(Color.RED)
            voiceButton.setTextColor(Color.RED)
            voiceButton.text = getString(R.string.stop)
            voiceProgress.visibility = View.VISIBLE
        } else {
            val defaultColor = Color.parseColor("#007BFF")
            voiceButton.iconTint = ColorStateList.valueOf(defaultColor)
            voiceButton.setTextColor(defaultColor)
            voiceButton.text = getString(R.string.voice)
            voiceProgress.visibility = View.GONE
        }
    }

    private fun createRecognitionListener() = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() { setRecordingState(false) }

        override fun onError(error: Int) {
            setRecordingState(false)
            Toast.makeText(this@NoteEditorActivity, "Lỗi nhận diện", Toast.LENGTH_SHORT).show()
        }

        override fun onResults(results: Bundle?) {
            val data = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            var spokenText = data?.get(0) ?: ""
            if (spokenText.isNotEmpty()) {
                val selectedLang = if (languageToggleGroup.checkedButtonId == R.id.btn_lang_en) "en" else "vi"
                spokenText = processFormatting(spokenText, selectedLang)
                val currentText = editContent.text.toString()
                val separator = if (currentText.endsWith("\n") || currentText.isEmpty()) "" else " "
                val newText = "$currentText$separator$spokenText"
                editContent.setText(newText)
                editContent.setSelection(newText.length)
            }
            setRecordingState(false)
        }

        private fun processFormatting(text: String, lang: String): String {
            var result = text.lowercase()
            val allCommands = mutableListOf<Pair<String, String>>()

            // 1. Lệnh mặc định
            if (lang == "vi") {
                allCommands.add("xuống dòng" to "\n")
                allCommands.add("phẩy" to ",")
                allCommands.add("chấm hỏi" to "?")
                allCommands.add("chấm than" to "!")
                allCommands.add("chấm" to ".")
            } else {
                allCommands.add("new line" to "\n")
                allCommands.add("newline" to "\n")
                allCommands.add("comma" to ",")
                allCommands.add("question mark" to "?")
                allCommands.add("exclamation mark" to "!")
                allCommands.add("period" to ".")
            }

            // 2. Lệnh từ Settings
            val prefs = getSharedPreferences("VaultSettings", android.content.Context.MODE_PRIVATE)
            val commandsJson = prefs.getString("voice_commands", "[]")
            try {
                val commands = JSONArray(commandsJson)
                for (i in 0 until commands.length()) {
                    val obj = commands.getJSONObject(i)
                    allCommands.add(obj.getString("phrase").lowercase() to obj.getString("punctuation"))
                }
            } catch (e: Exception) { e.printStackTrace() }

            // 3. Thuật toán: Longest Match First
            allCommands.sortByDescending { it.first.length }

            // 4. Thay thế
            for (cmd in allCommands) {
                result = result.replace(" ${cmd.first}", cmd.second).replace(cmd.first, cmd.second)
            }
            return result
        }

        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    override fun onDestroy() {
        super.onDestroy()
        speechRecognizer?.destroy()
    }
}
