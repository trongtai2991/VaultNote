package com.simplifynowsoftware.voicenote.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
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
        val existingKey = keyStore.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        return existingKey?.secretKey ?: createKey()
    }

    private fun createKey(): SecretKey {
        return KeyGenerator.getInstance(ALGORITHM).apply {
            init(
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
        }.generateKey()
    }

    fun encrypt(text: String): EncryptedData {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val ciphertext = cipher.doFinal(text.toByteArray())
        return EncryptedData(
            ciphertext = Base64.encodeToString(ciphertext, Base64.DEFAULT),
            iv = Base64.encodeToString(cipher.iv, Base64.DEFAULT)
        )
    }

    fun decrypt(ciphertext: String, iv: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(128, Base64.decode(iv, Base64.DEFAULT))
        cipher.init(Cipher.DECRYPT_MODE, getKey(), spec)
        val decodedCiphertext = Base64.decode(ciphertext, Base64.DEFAULT)
        val decryptedText = cipher.doFinal(decodedCiphertext)
        return String(decryptedText)
    }

    companion object {
        private const val ALIAS = "vault_note_key"
        private const val ALGORITHM = KeyProperties.KEY_ALGORITHM_AES
        private const val BLOCK_MODE = KeyProperties.BLOCK_MODE_GCM
        private const val PADDING = KeyProperties.ENCRYPTION_PADDING_NONE
        private const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
    }

    data class EncryptedData(val ciphertext: String, val iv: String)
}
