package com.simplifynowsoftware.voicenote.data

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.simplifynowsoftware.voicenote.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class NoteAdapter(private val onNoteClick: (Note) -> Unit) : RecyclerView.Adapter<NoteAdapter.NoteViewHolder>() {

    private var notes = emptyList<Note>()
    private val cryptoManager = CryptoManager()

    class NoteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val id: TextView = itemView.findViewById(R.id.text_note_id)
        val title: TextView = itemView.findViewById(R.id.text_note_title)
        val content: TextView = itemView.findViewById(R.id.text_note_content)
        val timestamp: TextView = itemView.findViewById(R.id.text_note_timestamp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_note, parent, false)
        return NoteViewHolder(view)
    }

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        val currentNote = notes[position]
        holder.id.text = "#${currentNote.id}"
        holder.title.text = if (currentNote.title.isEmpty()) "Không tiêu đề" else currentNote.title
        
        // GIẢI MÃ PREVIEW NỘI DUNG (Sử dụng silent = true để tránh spam log)
        val displayContent = if (!currentNote.iv.isNullOrEmpty()) {
            try {
                val decrypted = cryptoManager.decrypt(currentNote.content, currentNote.iv, silent = true)
                if (decrypted.length > 60) decrypted.take(60) + "..." else decrypted
            } catch (e: Exception) {
                "Dữ liệu đã được mã hóa an toàn"
            }
        } else {
            currentNote.content
        }
        
        holder.content.text = displayContent
        
        val date = Date(currentNote.createdAt)
        val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        holder.timestamp.text = format.format(date)

        holder.itemView.setOnClickListener {
            onNoteClick(currentNote)
        }
    }

    override fun getItemCount() = notes.size

    fun setNotes(notes: List<Note>) {
        this.notes = notes
        notifyDataSetChanged()
    }

    fun getNoteAt(position: Int): Note {
        return notes[position]
    }
}
