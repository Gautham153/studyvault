package com.example.ui.screens.study

import androidx.lifecycle.ViewModel
import com.example.data.model.StudyItem
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StudyViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    val priorityStudyItems: StateFlow<List<StudyItem>> = repository.priorityStudyItems

    private val _selectedFilter = MutableStateFlow("Priority")
    val selectedFilter: StateFlow<String> = _selectedFilter.asStateFlow()

    fun setFilter(filter: String) {
        _selectedFilter.value = filter
    }
}
