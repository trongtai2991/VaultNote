package com.simplifynowsoftware.voicenote.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.util.Log
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class CryptoManager {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply {
        load(null)
    }

    private fun getKey(): SecretKey {
        return try {
            val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
            if (existingKey != null) {
                Log.d(TAG, "🔑 Đã lấy khóa hiện có từ KeyStore")
                existingKey.secretKey
            } else {
                Log.i(TAG, "🔑 Không tìm thấy khóa, đang tạo khóa mới...")
                createKey()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Lỗi khi truy cập KeyStore: ${e.message}")
            throw e
        }
    }

    private fun createKey(): SecretKey {
        return try {
            val keyGenerator = KeyGenerator.getInstance(ALGORITHM, "AndroidKeyStore")
            keyGenerator.init(
                KeyGenParameterSpec.Builder(
                    ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(BLOCK_MODE)
                    .setEncryptionPaddings(PADDING)
                    .setUserAuthenticationRequired(false)
                    .setRandomizedEncryptionRequired(true)
                    .build()
            )
            val key = keyGenerator.generateKey()
            Log.i(TAG, "✅ Đã tạo khóa AES thành công trong AndroidKeyStore")
            key
        } catch (e: Exception) {
            Log.e(TAG, "❌ Lỗi khi tạo khóa: ${e.message}")
            throw e
        }
    }

    fun encrypt(text: String): EncryptedData {
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, getKey())
            val ciphertext = cipher.doFinal(text.toByteArray())
            
            val encryptedData = EncryptedData(
                ciphertext = Base64.encodeToString(ciphertext, Base64.DEFAULT),
                iv = Base64.encodeToString(cipher.iv, Base64.DEFAULT)
            )
            
            // Log thông tin đã mã hóa (Không log text gốc)
            val logMsg = "🔒 Mã hóa thành công. Ciphertext: ${encryptedData.ciphertext.take(15)}..."
            Log.i(TAG, logMsg)
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log(logMsg)
            encryptedData
        } catch (e: Exception) {
            val errorMsg = "❌ Lỗi mã hóa dữ liệu: ${e.message}"
            Log.e(TAG, errorMsg)
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log(errorMsg)
            throw e
        }
    }

    fun decrypt(ciphertext: String, iv: String, silent: Boolean = false): String {
        return try {
            if (!silent) Log.d(TAG, "🔓 Bắt đầu giải mã dữ liệu...")
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
            cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)
            
            val decodedCiphertext = Base64.decode(ciphertext, Base64.DEFAULT)
            val decryptedText = String(cipher.doFinal(decodedCiphertext))
            
            if (!silent) {
                val logMsg = "✅ Giải mã thành công note"
                Log.i(TAG, logMsg)
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log(logMsg)
            }
            decryptedText
        } catch (e: Exception) {
            if (!silent) {
                val errorMsg = "❌ Lỗi giải mã dữ liệu: ${e.message}"
                Log.e(TAG, errorMsg)
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log(errorMsg)
            }
            throw e
        }
    }

    companion object {
        private const val TAG = "CryptoManager"
        private const val ALIAS = "vault_note_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    }

    data class EncryptedData(val ciphertext: String, val iv: String)
}
