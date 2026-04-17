package com.simplifynowsoftware.voicenote.manager

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AiLogManager {
    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs

    private val dateFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun log(message: String) {
        val timestamp = dateFormat.format(Date())
        val newLog = "[$timestamp] $message"
        
        // Giữ tối đa 100 dòng log gần nhất
        val currentList = _logs.value.toMutableList()
        currentList.add(0, newLog) // Thêm lên đầu
        if (currentList.size > 100) {
            currentList.removeAt(currentList.size - 1)
        }
        _logs.value = currentList
    }

    fun clear() {
        _logs.value = emptyList()
    }
}
