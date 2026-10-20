package com.example.ui.screens.subjects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Subject
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SubjectsViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val subjects: StateFlow<List<Subject>> = repository.subjects
    val errorMessage: StateFlow<String?> = repository.errorMessage

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun addSubject(name: String, code: String, iconKey: String) {
        if (name.isNotBlank()) {
            repository.addSubject(name.trim(), code.trim(), iconKey)
        }
    }

    fun updateSubject(subjectId: String, name: String, code: String, iconKey: String) {
        viewModelScope.launch {
            val result = repository.updateSubject(subjectId, name, code, iconKey)
            result.fold(
                onSuccess = { subject -> _actionMessage.value = "Renamed subject to ${subject.name}" },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Failed to update subject."
                }
            )
        }
    }

    fun deleteSubject(subjectId: String) {
        val subjectName = repository.getSubjectById(subjectId)?.name ?: "Subject"
        viewModelScope.launch {
            val result = repository.deleteSubject(subjectId)
            result.fold(
                onSuccess = { _actionMessage.value = "Deleted $subjectName" },
                onFailure = { error ->
                    _actionMessage.value = error.message ?: "Failed to delete subject."
                }
            )
        }
    }

    fun clearActionMessage() {
        _actionMessage.value = null
    }
}
