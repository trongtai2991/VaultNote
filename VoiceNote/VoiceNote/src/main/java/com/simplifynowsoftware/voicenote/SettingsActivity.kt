package com.simplifynowsoftware.voicenote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.switchmaterial.SwitchMaterial
import com.simplifynowsoftware.voicenote.manager.ModelSelectionAdapter
import com.simplifynowsoftware.voicenote.manager.OpenRouterModel
import com.google.android.material.textfield.TextInputEditText
import com.simplifynowsoftware.voicenote.data.NoteViewModel
import com.simplifynowsoftware.voicenote.manager.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {

    private lateinit var autoExportSwitch: SwitchMaterial
    private lateinit var vaultPathText: TextView
    private lateinit var commandsContainer: LinearLayout
    private lateinit var languageGroup: RadioGroup
    private lateinit var rbVi: RadioButton
    private lateinit var rbEn: RadioButton
    
    // AI Elements
    private lateinit var settingsManager: SettingsManager
    private val viewModel: NoteViewModel by viewModels()
    private lateinit var editApiKey: TextInputEditText
    private lateinit var textSelectedModel: TextView
    private lateinit var btnSelectModel: Button
    private lateinit var btnSaveAiSettings: Button
    
    private val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            saveVaultUri(it)
            updateUi()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        autoExportSwitch = findViewById(R.id.switch_auto_export)
        vaultPathText = findViewById(R.id.text_vault_path)
        commandsContainer = findViewById(R.id.container_commands)
        languageGroup = findViewById(R.id.rg_language)
        rbVi = findViewById(R.id.rb_vi)
        rbEn = findViewById(R.id.rb_en)

        // Initialize AI
        settingsManager = SettingsManager(this)
        editApiKey = findViewById(R.id.edit_api_key)
        textSelectedModel = findViewById(R.id.text_selected_model)
        btnSelectModel = findViewById(R.id.btn_select_model)
        btnSaveAiSettings = findViewById(R.id.btn_save_ai_settings)
        
        lifecycleScope.launch {
            editApiKey.setText(settingsManager.openRouterApiKeyFlow.first())
            textSelectedModel.text = settingsManager.selectedModelIdFlow.first()
        }

        btnSaveAiSettings.setOnClickListener {
            val apiKey = editApiKey.text.toString().trim()
            lifecycleScope.launch {
                settingsManager.updateOpenRouterApiKey(apiKey)
                Toast.makeText(this@SettingsActivity, "AI Config Saved", Toast.LENGTH_SHORT).show()
                viewModel.fetchAvailableModels() // Refresh models after saving API key
            }
        }

        btnSelectModel.setOnClickListener {
            showModelSelectionDialog()
        }

        val btnSelectVault = findViewById<Button>(R.id.btn_select_vault)
        val btnAddCommand = findViewById<Button>(R.id.btn_add_command)
        val btnApplyLanguage = findViewById<Button>(R.id.btn_apply_language)

        updateUi()

        autoExportSwitch.setOnCheckedChangeListener { _, isChecked ->
            getSharedPreferences("VaultSettings", Context.MODE_PRIVATE).edit()
                .putBoolean("auto_export_enabled", isChecked).apply()
        }

        btnApplyLanguage.setOnClickListener {
            val selectedLang = if (rbVi.isChecked) "vi" else "en"
            
            // 1. Lưu cấu hình mặc định cho Giọng nói (Full tag)
            val fullTag = if (selectedLang == "vi") "vi-VN" else "en-US"
            getSharedPreferences("VaultSettings", Context.MODE_PRIVATE).edit()
                .putString("default_language", fullTag).apply()

            // 2. Áp dụng ngôn ngữ cho Giao diện (Toàn app)
            val appLocale: LocaleListCompat = LocaleListCompat.forLanguageTags(selectedLang)
            AppCompatDelegate.setApplicationLocales(appLocale)
            
            Toast.makeText(this, if(selectedLang == "vi") "Đã áp dụng tiếng Việt" else "English applied", Toast.LENGTH_SHORT).show()
        }

        btnSelectVault.setOnClickListener {
            directoryPickerLauncher.launch(null)
        }

        btnAddCommand.setOnClickListener {
            showAddCommandDialog()
        }
    }

    private fun updateUi() {
        val prefs = getSharedPreferences("VaultSettings", Context.MODE_PRIVATE)
        autoExportSwitch.isChecked = prefs.getBoolean("auto_export_enabled", false)
        val uriString = prefs.getString("vault_uri", null)
        vaultPathText.text = if (uriString != null) Uri.parse(uriString).path else "Chưa chọn thư mục Vault"
        
        val currentLang = prefs.getString("default_language", "vi-VN")
        if (currentLang?.startsWith("vi") == true) rbVi.isChecked = true else rbEn.isChecked = true

        loadCommands()
    }

    private fun loadCommands() {
        commandsContainer.removeAllViews()
        val prefs = getSharedPreferences("VaultSettings", Context.MODE_PRIVATE)
        val commandsJson = prefs.getString("voice_commands", "[]")
        val commands = JSONArray(commandsJson)

        for (i in 0 until commands.length()) {
            val obj = commands.getJSONObject(i)
            addCommandView(obj.getString("phrase"), obj.getString("punctuation"), i)
        }
    }

    private fun addCommandView(phrase: String, punctuation: String, index: Int) {
        val view = LayoutInflater.from(this).inflate(android.R.layout.simple_list_item_2, null)
        view.findViewById<TextView>(android.R.id.text1).apply {
            text = phrase
            setTextColor(android.graphics.Color.WHITE)
        }
        view.findViewById<TextView>(android.R.id.text2).apply {
            text = "Thay bằng: $punctuation"
            setTextColor(android.graphics.Color.GRAY)
        }
        view.setOnClickListener {
            showDeleteConfirm(index)
        }
        commandsContainer.addView(view)
    }

    private fun showAddCommandDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(50, 20, 50, 20)
        }
        val editPhrase = EditText(this).apply { hint = "Từ khóa (VD: phẩy)" }
        val editPunc = EditText(this).apply { hint = "Dấu câu (VD: ,)" }
        layout.addView(editPhrase)
        layout.addView(editPunc)

        AlertDialog.Builder(this)
            .setTitle("Thêm lệnh giọng nói")
            .setView(layout)
            .setPositiveButton("Lưu") { _, _ ->
                saveNewCommand(editPhrase.text.toString(), editPunc.text.toString())
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun saveNewCommand(phrase: String, punctuation: String) {
        if (phrase.isEmpty() || punctuation.isEmpty()) return
        val prefs = getSharedPreferences("VaultSettings", Context.MODE_PRIVATE)
        val commandsJson = prefs.getString("voice_commands", "[]")
        val commands = JSONArray(commandsJson)
        commands.put(JSONObject().apply {
            put("phrase", phrase.lowercase())
            put("punctuation", punctuation)
        })
        prefs.edit().putString("voice_commands", commands.toString()).apply()
        loadCommands()
    }

    private fun showDeleteConfirm(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("Xóa lệnh này?")
            .setPositiveButton("Xóa") { _, _ ->
                deleteCommand(index)
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun deleteCommand(index: Int) {
        val prefs = getSharedPreferences("VaultSettings", Context.MODE_PRIVATE)
        val commandsJson = prefs.getString("voice_commands", "[]")
        val commands = JSONArray(commandsJson)
        val newList = JSONArray()
        for (i in 0 until commands.length()) {
            if (i != index) newList.put(commands.get(i))
        }
        prefs.edit().putString("voice_commands", newList.toString()).apply()
        loadCommands()
    }

    private fun saveVaultUri(uri: Uri) {
        getSharedPreferences("VaultSettings", Context.MODE_PRIVATE).edit()
            .putString("vault_uri", uri.toString()).apply()
    }

    private fun showModelSelectionDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        }

        // 1. ChipGroup for Providers
        val chipGroup = ChipGroup(this).apply {
            isSingleSelection = true
            isSelectionRequired = true
            setPadding(0, 0, 0, 16)
        }
        
        // 2. RecyclerView for Models
        val recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity)
            setPadding(0, 16, 0, 0)
        }

        var dialog: AlertDialog? = null
        
        val adapter = ModelSelectionAdapter { selectedModel: OpenRouterModel ->
            textSelectedModel.text = selectedModel.id
            lifecycleScope.launch {
                settingsManager.updateSelectedModelId(selectedModel.id)
            }
            dialog?.dismiss()
        }
        recyclerView.adapter = adapter

        container.addView(chipGroup)
        container.addView(recyclerView)

        dialog = AlertDialog.Builder(this)
            .setTitle("Select AI Model")
            .setView(container)
            .setNegativeButton("Close", null)
            .create()

        // Observe ViewModel
        lifecycleScope.launch {
            viewModel.availableProviders.collect { providers ->
                chipGroup.removeAllViews()
                providers.forEach { provider ->
                    val chip = Chip(this@SettingsActivity).apply {
                        text = provider
                        isCheckable = true
                        isChecked = provider == viewModel.selectedProvider.value
                        setOnCheckedChangeListener { _, isChecked ->
                            if (isChecked) viewModel.setSelectedProvider(provider)
                        }
                    }
                    chipGroup.addView(chip)
                }
            }
        }

        lifecycleScope.launch {
            viewModel.displayedModels.collect { models ->
                adapter.submitList(models)
                recyclerView.scrollToPosition(0)
            }
        }

        dialog?.show()
    }
}
