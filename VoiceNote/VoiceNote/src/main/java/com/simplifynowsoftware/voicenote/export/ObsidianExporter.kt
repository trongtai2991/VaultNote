package com.simplifynowsoftware.voicenote.export

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.simplifynowsoftware.voicenote.data.CryptoManager
import com.simplifynowsoftware.voicenote.data.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ObsidianExporter(private val context: Context) {

    private val cryptoManager = CryptoManager()

    fun exportNoteToMarkdown(treeUri: Uri, note: Note): Boolean {
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            
            // 1. Tạo folder theo YYYY-MM
            val monthFolder = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(note.createdAt))
            val subFolder = rootDoc.findFile(monthFolder) ?: rootDoc.createDirectory(monthFolder) ?: rootDoc
            
            // 2. Giải mã nội dung
            val decryptedContent = if (!note.iv.isNullOrEmpty()) {
                cryptoManager.decrypt(note.content, note.iv!!)
            } else {
                note.content
            }

            // 3. Xử lý tiêu đề an toàn cho hiển thị
            val displayTitle = if (note.title.isEmpty()) "Untitled" else note.title
            
            // Tên file CỐ ĐỊNH theo ID: note_[ID].md
            val fileName = "note_${note.id}.md"

            // 4. Tìm và ghi đè file cũ nếu đã tồn tại (Chỉ dựa vào ID)
            val existingFile = subFolder.findFile(fileName)
            val file = existingFile ?: subFolder.createFile("text/markdown", fileName) ?: return false

            // 5. Tạo nội dung Markdown (Sử dụng cách nối chuỗi an toàn nhất để đảm bảo sát lề trái)
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(note.createdAt))
            val yamlTitle = displayTitle.replace("\"", "\\\"")
            
            val markdown = "---\n" +
                    "title: \"$yamlTitle\"\n" +
                    "date: $dateStr\n" +
                    "tags: [VaultNote]\n" +
                    "id: ${note.id}\n" +
                    "---\n\n" +
                    decryptedContent

            // 6. Ghi dữ liệu (Sử dụng chế độ ghi đè "wt")
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(markdown.toByteArray())
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
