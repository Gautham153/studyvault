package com.example.ui.screens.subjects

import androidx.lifecycle.ViewModel
import com.example.data.model.Subject
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SubjectsViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val subjects: StateFlow<List<Subject>> = repository.subjects
    val errorMessage: StateFlow<String?> = repository.errorMessage

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun addSubject(name: String, code: String, iconKey: String) {
        if (name.isNotBlank()) {
            repository.addSubject(name.trim(), code.trim(), iconKey)
        }
    }
}
