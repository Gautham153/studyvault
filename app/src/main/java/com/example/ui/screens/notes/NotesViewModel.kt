package com.example.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.NoteItem
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotesUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val notes: List<NoteItem> = emptyList(),
    val isEmpty: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class NotesViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    private var activeTopicId: String? = null
    private var activeSubjectId: String? = null
    private var loadRequestToken = 0

    init {
        // Observe repository subjects updates to keep notes reactive.
        // Every refresh verifies the active request token and resource before publishing,
        // so a late result from a previous topic/subject can never overwrite current UI.
        viewModelScope.launch {
            repository.subjects.collect { _ ->
                val requestToken = loadRequestToken
                val tId = activeTopicId
                val sId = activeSubjectId
                try {
                    if (tId != null) {
                        val notes = repository.getNotesForTopic(tId)
                        if (requestToken == loadRequestToken && activeTopicId == tId) {
                            _uiState.update { current ->
                                current.copy(
                                    notes = notes,
                                    isEmpty = notes.isEmpty()
                                )
                            }
                        }
                    } else if (sId != null) {
                        val notes = repository.getNotesForSubject(sId)
                        if (requestToken == loadRequestToken && activeSubjectId == sId) {
                            _uiState.update { current ->
                                current.copy(
                                    notes = notes,
                                    isEmpty = notes.isEmpty()
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (requestToken == loadRequestToken) {
                        _uiState.update {
                            it.copy(errorMessage = e.message ?: "Failed to refresh notes.")
                        }
                    }
                }
            }
        }
    }

    fun loadForTopic(topicId: String) {
        val resourceChanged = activeTopicId != topicId || activeSubjectId != null
        val requestToken = ++loadRequestToken
        activeTopicId = topicId
        activeSubjectId = null

        _uiState.update {
            it.copy(
                isLoading = true,
                // Clear the previous resource's notes so they are never displayed for
                // the newly selected topic while the load is in flight.
                notes = if (resourceChanged) emptyList() else it.notes,
                isEmpty = if (resourceChanged) true else it.isEmpty,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val notes = repository.getNotesForTopic(topicId)
                if (requestToken == loadRequestToken && activeTopicId == topicId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            notes = notes,
                            isEmpty = notes.isEmpty(),
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (requestToken == loadRequestToken && activeTopicId == topicId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load notes for topic."
                        )
                    }
                }
            }
        }
    }

    fun loadForSubject(subjectId: String) {
        val resourceChanged = activeSubjectId != subjectId || activeTopicId != null
        val requestToken = ++loadRequestToken
        activeSubjectId = subjectId
        activeTopicId = null

        _uiState.update {
            it.copy(
                isLoading = true,
                // Clear the previous resource's notes while the new subject loads.
                notes = if (resourceChanged) emptyList() else it.notes,
                isEmpty = if (resourceChanged) true else it.isEmpty,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val notes = repository.getNotesForSubject(subjectId)
                if (requestToken == loadRequestToken && activeSubjectId == subjectId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            notes = notes,
                            isEmpty = notes.isEmpty(),
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (requestToken == loadRequestToken && activeSubjectId == subjectId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load notes for subject."
                        )
                    }
                }
            }
        }
    }

    fun addNote(
        subjectId: String,
        topicId: String? = null,
        title: String,
        content: String = ""
    ) {
        if (_uiState.value.isSaving) return // guard against duplicate submissions
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Note title cannot be blank.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.addNote(
                subjectId = subjectId,
                topicId = topicId,
                title = trimmedTitle,
                content = content
            )
            result.fold(
                onSuccess = { created ->
                    _uiState.update { current ->
                        val updatedList = current.notes + created
                        current.copy(
                            isSaving = false,
                            notes = updatedList,
                            isEmpty = false,
                            successMessage = "Note saved successfully."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Failed to save note."
                        )
                    }
                }
            )
        }
    }

    fun updateNote(note: NoteItem) {
        if (_uiState.value.isSaving) return // guard against duplicate submissions
        val trimmedTitle = note.title.trim()
        if (trimmedTitle.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Note title cannot be blank.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.updateNote(note.copy(title = trimmedTitle))
            result.fold(
                onSuccess = { updated ->
                    _uiState.update { current ->
                        val updatedList = current.notes.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            isSaving = false,
                            notes = updatedList,
                            successMessage = "Note updated successfully."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Failed to update note."
                        )
                    }
                }
            )
        }
    }

    fun deleteNote(noteId: String) {
        if (_uiState.value.isSaving) return // guard against duplicate submissions
        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.deleteNote(noteId)
            result.fold(
                onSuccess = {
                    _uiState.update { current ->
                        val updatedList = current.notes.filter { it.id != noteId }
                        current.copy(
                            isSaving = false,
                            notes = updatedList,
                            isEmpty = updatedList.isEmpty(),
                            successMessage = "Note deleted."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Failed to delete note."
                        )
                    }
                }
            )
        }
    }

    fun clearMessages() {
        _uiState.update { it.copy(errorMessage = null, successMessage = null) }
    }
}
