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
        com.simplifynowsoftware.voicenote.manager.AiLogManager.log("📤 Bắt đầu Export: ID=${note.id}")
        try {
            val rootDoc = DocumentFile.fromTreeUri(context, treeUri)
            if (rootDoc == null) {
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log("❌ Lỗi: RootDoc null. URI không hợp lệ.")
                return false
            }

            // Kiểm tra quyền ghi của thư mục gốc
            if (!rootDoc.canWrite()) {
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log("❌ Lỗi: Thư mục gốc KHÔNG CÓ QUYỀN GHI. Hãy chọn lại Vault trong Cài đặt.")
                return false
            }
            
            // 1. Tạo folder theo YYYY-MM
            val monthFolder = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date(note.createdAt))
            var subFolder = rootDoc.findFile(monthFolder)
            
            if (subFolder == null) {
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log("📁 Đang tạo thư mục mới: $monthFolder")
                subFolder = rootDoc.createDirectory(monthFolder)
            } else if (!subFolder.isDirectory) {
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log("⚠️ Cảnh báo: Đã tồn tại 1 FILE tên '$monthFolder', không thể tạo thư mục.")
                subFolder = rootDoc // Fallback về gốc nếu trùng tên file
            }

            if (subFolder == null) {
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log("❌ Lỗi: Không thể tạo/truy cập thư mục '$monthFolder'. Dùng thư mục gốc.")
                subFolder = rootDoc
            }
            
            // 2. Giải mã nội dung
            val decryptedContent = if (!note.iv.isNullOrEmpty()) {
                cryptoManager.decrypt(note.content, note.iv!!)
            } else {
                note.content
            }

            // 3. Tên file CỐ ĐỊNH theo ID
            val fileName = "note_${note.id}.md"
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log("📝 Đang tạo/tìm file: $fileName")

            // 4. Tìm hoặc tạo file
            var file = subFolder.findFile(fileName)
            if (file == null) {
                file = subFolder.createFile("text/markdown", fileName)
            }
            
            if (file == null) {
                val canWriteSub = if(subFolder.canWrite()) "CÓ" else "KHÔNG"
                com.simplifynowsoftware.voicenote.manager.AiLogManager.log("❌ Lỗi: Không tạo được file '$fileName'. Quyền ghi thư mục con: $canWriteSub")
                return false
            }

            // 5. Nội dung Markdown
            val displayTitle = if (note.title.isEmpty()) "Untitled" else note.title
            val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(note.createdAt))
            val yamlTitle = displayTitle.replace("\"", "\\\"")
            
            // Xử lý tags từ Note (JSON string)
            val noteTags = try {
                val arr = org.json.JSONArray(note.tags ?: "[]")
                List(arr.length()) { i -> arr.getString(i) }
            } catch (e: Exception) {
                emptyList<String>()
            }
            val allTags = (listOf("VaultNote") + noteTags).distinct()
            val tagsYaml = allTags.joinToString(", ") { "\"$it\"" }
            
            val markdown = "---\n" +
                    "title: \"$yamlTitle\"\n" +
                    "date: $dateStr\n" +
                    "tags: [$tagsYaml]\n" +
                    "folder: \"${note.folder ?: "Default"}\"\n" +
                    "id: ${note.id}\n" +
                    "---\n\n" +
                    decryptedContent

            // 6. Ghi dữ liệu
            context.contentResolver.openOutputStream(file.uri, "wt")?.use {
                it.write(markdown.toByteArray())
            }
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log("✅ Thành công: Đã ghi nội dung vào $fileName")
            return true
        } catch (e: Exception) {
            val errorMsg = "❌ Lỗi hệ thống khi Export: ${e.message}"
            com.simplifynowsoftware.voicenote.manager.AiLogManager.log(errorMsg)
            e.printStackTrace()
            return false
        }
    }
}
