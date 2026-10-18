package com.example.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ExamItem
import com.example.data.model.MaterialItem
import com.example.data.model.OverallProgress
import com.example.data.model.RevisionEvent
import com.example.data.model.StudyItem
import com.example.data.model.Subject
import com.example.data.model.TopicStatus
import com.example.data.repository.StudyRecommendation
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
    val todayStudyPlan: StateFlow<List<StudyRecommendation>> = repository.todayStudyPlan
    val upcomingExams: StateFlow<List<ExamItem>> = repository.upcomingExams
    val recentRevisions: StateFlow<List<RevisionEvent>> = repository.recentRevisions
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val errorMessage: StateFlow<String?> = repository.errorMessage

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    fun getTodayStudyItems(): List<StudyItem> = repository.getTodayStudyItems()
    fun getRecentlyAddedMaterials(): List<MaterialItem> = repository.getRecentlyAddedMaterials()

    /** Marks a planned topic as actively in progress. */
    fun startTopic(topicId: String) = runAction { repository.updateTopicStatus(topicId, TopicStatus.LEARNING) }

    /** Marks a planned topic as completed. */
    fun completeTopic(topicId: String) = runAction { repository.updateTopicStatus(topicId, TopicStatus.COMPLETED) }

    /** Records a revision for a planned topic (clears NEEDS_REVISION and logs history). */
    fun reviseTopic(topicId: String) = runAction { repository.markTopicRevised(topicId) }

    fun clearActionError() {
        _actionError.value = null
    }

    private fun runAction(action: suspend () -> Result<*>) {
        viewModelScope.launch {
            action().onFailure { error -> _actionError.value = error.message ?: "Action failed." }
                .onSuccess { _actionError.value = null }
        }
    }
}
