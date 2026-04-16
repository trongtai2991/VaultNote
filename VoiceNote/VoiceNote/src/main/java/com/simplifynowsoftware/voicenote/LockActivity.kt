package com.simplifynowsoftware.voicenote

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import java.util.concurrent.Executor

class LockActivity : AppCompatActivity() {

    private lateinit var executor: Executor
    private lateinit var biometricPrompt: BiometricPrompt
    private lateinit var promptInfo: BiometricPrompt.PromptInfo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Tránh vòng lặp nếu đã unlock
        if (SecurityManager.isUnlocked) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_lock)

        executor = ContextCompat.getMainExecutor(this)
        
        setupBiometric()

        findViewById<Button>(R.id.btn_unlock).setOnClickListener {
            showBiometricPrompt()
        }

        // Tự động hiện prompt khi vừa mở app
        showBiometricPrompt()
    }

    private fun setupBiometric() {
        biometricPrompt = BiometricPrompt(this, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // Lỗi 11: Chưa đăng ký vân tay/PIN trên máy
                if (errorCode != 13 && errorCode != 10) { // Không phải người dùng hủy
                    Toast.makeText(applicationContext, "Lỗi: $errString", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                SecurityManager.isUnlocked = true
                goToMain()
            }

            override fun onAuthenticationFailed() {
                super.onAuthenticationFailed()
                Toast.makeText(applicationContext, "Xác thực không thành công", Toast.LENGTH_SHORT).show()
            }
        })

        promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Bảo mật VaultNote")
            .setSubtitle("Sử dụng vân tay hoặc mã PIN của máy")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()
    }

    private fun showBiometricPrompt() {
        try {
            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Toast.makeText(this, "Không thể khởi tạo bảo mật", Toast.LENGTH_SHORT).show()
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
