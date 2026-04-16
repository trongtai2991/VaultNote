package com.simplifynowsoftware.voicenote.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import kotlinx.coroutines.launch

class NoteViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: NoteRepository
    val allNotes: LiveData<List<Note>>

    init {
        val noteDao = AppDatabase.getDatabase(application).noteDao()
        repository = NoteRepository(noteDao)
        allNotes = repository.allNotes.asLiveData()
    }

    fun getNoteById(id: Int): LiveData<Note?> {
        return repository.getNoteById(id).asLiveData()
    }

    fun insert(note: Note) = viewModelScope.launch { repository.insert(note) }
    fun update(note: Note) = viewModelScope.launch { repository.update(note) }
    fun delete(note: Note) = viewModelScope.launch { repository.delete(note) }
}
