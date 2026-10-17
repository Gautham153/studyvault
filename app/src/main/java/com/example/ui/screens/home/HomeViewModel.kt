package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import com.example.data.model.MaterialItem
import com.example.data.model.OverallProgress
import com.example.data.model.StudyItem
import com.example.data.model.Subject
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.StateFlow

data class HomeUiState(
    val overallProgress: OverallProgress,
    val studyTodayItems: List<StudyItem>,
    val topSubjects: List<Subject>,
    val recentMaterials: List<MaterialItem>
)

class HomeViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    val overallProgress: StateFlow<OverallProgress> = repository.overallProgress
    val subjects: StateFlow<List<Subject>> = repository.subjects
    val todayStudyItems: StateFlow<List<StudyItem>> = repository.priorityStudyItems
    val errorMessage: StateFlow<String?> = repository.errorMessage

    fun getTodayStudyItems(): List<StudyItem> = repository.getTodayStudyItems()
    fun getRecentlyAddedMaterials(): List<MaterialItem> = repository.getRecentlyAddedMaterials()
}
