package com.simplifynowsoftware.voicenote.ui

import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.simplifynowsoftware.voicenote.R
import com.simplifynowsoftware.voicenote.manager.AiLogManager
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AiLogsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_logs)

        val tvAiLogs: TextView = findViewById(R.id.tvAiLogs)
        val btnClearLogs: Button = findViewById(R.id.btnClearLogs)
        val btnBack: Button = findViewById(R.id.btnBack)
        
        // Thêm nút Chọn File Model một cách an toàn
        findViewById<android.widget.LinearLayout>(R.id.logContainer)?.let { container ->
            val btnPickModel = Button(this).apply {
                text = "CHỌN FILE MODEL"
                setOnClickListener {
                    pickModelLauncher.launch(arrayOf("*/*"))
                }
            }
            container.addView(btnPickModel, 1)
        }

        lifecycleScope.launch {
            AiLogManager.logs.collectLatest { logs ->
                tvAiLogs.text = if (logs.isEmpty()) {
                    "Chưa có log nào..."
                } else {
                    logs.joinToString("\n\n")
                }
            }
        }

        btnClearLogs.setOnClickListener {
            AiLogManager.clear()
        }

        btnBack.setOnClickListener {
            finish()
        }
    }

    private val pickModelLauncher = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.OpenDocument()) { uri ->
        // Vô hiệu hóa tính năng nhập model cho OpenRouter
        android.widget.Toast.makeText(this, "Tính năng này đã được thay thế bằng OpenRouter API", android.widget.Toast.LENGTH_SHORT).show()
    }
}
