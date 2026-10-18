package com.example.ui.screens.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.StudyItem
import com.example.data.model.TopicStatus
import com.example.data.repository.StudyPlanning
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** Study screen filters, backed by real topic + revision data. */
enum class StudyFilter(val label: String) {
    PRIORITY("Priority"),
    ALL("All Topics"),
    NEEDS_REVISION("Needs Revision"),
    RECENTLY_REVISED("Recently Revised")
}

class StudyViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    val isLoading: StateFlow<Boolean> = repository.isLoading

    private val _selectedFilter = MutableStateFlow(StudyFilter.PRIORITY)
    val selectedFilter: StateFlow<StudyFilter> = _selectedFilter.asStateFlow()

    val studyItems: StateFlow<List<StudyItem>> = combine(
        _selectedFilter,
        repository.studyRecommendations,
        repository.subjects,
        repository.recentRevisions
    ) { filter, recommendations, subjects, recentRevisions ->
        val revisionsByTopic = recentRevisions.groupBy { it.topicId }
        when (filter) {
            StudyFilter.PRIORITY -> recommendations
                .filter { it.status != TopicStatus.COMPLETED }
                .map { StudyPlanning.recommendationToStudyItem(it) }
            StudyFilter.ALL -> recommendations
                .map { StudyPlanning.recommendationToStudyItem(it) }
            StudyFilter.NEEDS_REVISION -> recommendations
                .filter { it.status == TopicStatus.NEEDS_REVISION }
                .map { StudyPlanning.recommendationToStudyItem(it) }
            StudyFilter.RECENTLY_REVISED -> StudyPlanning.buildRevisionStudyItems(subjects, revisionsByTopic)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(filter: StudyFilter) {
        _selectedFilter.value = filter
    }
}
