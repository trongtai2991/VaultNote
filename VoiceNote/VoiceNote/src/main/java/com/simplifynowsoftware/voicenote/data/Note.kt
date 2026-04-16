package com.simplifynowsoftware.voicenote.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val content: String, // Đây sẽ là nội dung đã mã hóa (Base64)
    val iv: String? = null, // Initialization Vector để giải mã
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
