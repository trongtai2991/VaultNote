package com.simplifynowsoftware.voicenote

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.snackbar.Snackbar
import androidx.lifecycle.lifecycleScope
import com.simplifynowsoftware.voicenote.data.NoteAdapter
import com.simplifynowsoftware.voicenote.data.NoteViewModel
import com.simplifynowsoftware.voicenote.export.ObsidianExporter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var viewModel: NoteViewModel
    private lateinit var adapter: NoteAdapter
    private lateinit var exporter: ObsidianExporter

    private val directoryPickerLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            exportAllNotesToObsidian(it)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (!SecurityManager.isUnlocked) {
            val intent = Intent(this, LockActivity::class.java)
            startActivity(intent)
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        
        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "" // Để dùng TextView custom cho tiêu đề

        exporter = ObsidianExporter(this)

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view_notes)
        adapter = NoteAdapter { note ->
            val intent = Intent(this, NoteEditorActivity::class.java)
            intent.putExtra("NOTE_ID", note.id)
            startActivity(intent)
        }
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        viewModel = ViewModelProvider(this).get(NoteViewModel::class.java)
        viewModel.allNotes.observe(this) { notes ->
            notes?.let { adapter.setNotes(it) }
        }

        // Quan sát trạng thái AI
        lifecycleScope.launch {
            viewModel.aiResultEvent.collectLatest { message ->
                message?.let {
                    Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                    viewModel.clearAiEvent()
                }
            }
        }

        // Swipe to Delete
        val itemTouchHelperCallback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.adapterPosition
                val noteToDelete = adapter.getNoteAt(position)
                
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Xóa ghi chú")
                    .setMessage("Bạn có chắc chắn muốn xóa không?")
                    .setPositiveButton("Xóa") { _, _ ->
                        viewModel.delete(noteToDelete)
                        Snackbar.make(recyclerView, "Đã xóa", Snackbar.LENGTH_LONG)
                            .setAction("HOÀN TÁC") { viewModel.insert(noteToDelete) }.show()
                    }
                    .setNegativeButton("Hủy") { _, _ -> adapter.notifyItemChanged(position) }
                    .setCancelable(false)
                    .show()
            }
        }
        ItemTouchHelper(itemTouchHelperCallback).attachToRecyclerView(recyclerView)

        findViewById<FloatingActionButton>(R.id.fab_add_note).setOnClickListener {
            startActivity(Intent(this, NoteEditorActivity::class.java))
        }

        findViewById<FloatingActionButton>(R.id.fab_ai_logs).setOnClickListener {
            startActivity(Intent(this, com.simplifynowsoftware.voicenote.ui.AiLogsActivity::class.java))
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        if (item.itemId == R.id.action_settings) {
            startActivity(Intent(this, SettingsActivity::class.java))
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun exportAllNotesToObsidian(uri: Uri) {
        val notes = viewModel.allNotes.value ?: return
        var successCount = 0
        
        // Giữ quyền truy cập thư mục lâu dài (nếu cần)
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

        notes.forEach { note ->
            if (exporter.exportNoteToMarkdown(uri, note)) {
                successCount++
            }
        }
        
        Toast.makeText(this, "Đã xuất $successCount/${notes.size} ghi chú sang Obsidian", Toast.LENGTH_LONG).show()
    }

    override fun onResume() {
        super.onResume()
        SecurityManager.onAppForegrounded()
        if (!SecurityManager.isUnlocked) {
            startActivity(Intent(this, LockActivity::class.java))
            finish()
        }
    }

    override fun onPause() {
        super.onPause()
        SecurityManager.onAppBackgrounded()
    }
}
