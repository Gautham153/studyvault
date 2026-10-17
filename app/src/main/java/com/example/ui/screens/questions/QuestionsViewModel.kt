package com.example.ui.screens.questions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.Importance
import com.example.data.model.QuestionItem
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class QuestionsUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val questions: List<QuestionItem> = emptyList(),
    val isEmpty: Boolean = true,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class QuestionsViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    private val _uiState = MutableStateFlow(QuestionsUiState())
    val uiState: StateFlow<QuestionsUiState> = _uiState.asStateFlow()

    private var activeTopicId: String? = null
    private var activeSubjectId: String? = null
    private var loadRequestToken = 0

    init {
        // Observe repository subjects updates to keep questions reactive
        viewModelScope.launch {
            repository.subjects.collect { _ ->
                val tId = activeTopicId
                val sId = activeSubjectId
                if (tId != null) {
                    val questions = repository.getQuestionsForTopic(tId)
                    _uiState.update { current ->
                        current.copy(
                            questions = questions,
                            isEmpty = questions.isEmpty()
                        )
                    }
                } else if (sId != null) {
                    val questions = repository.getQuestionsForSubject(sId)
                    _uiState.update { current ->
                        current.copy(
                            questions = questions,
                            isEmpty = questions.isEmpty()
                        )
                    }
                }
            }
        }
    }

    fun loadForTopic(topicId: String) {
        val requestToken = ++loadRequestToken
        activeTopicId = topicId
        activeSubjectId = null

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val questions = repository.getQuestionsForTopic(topicId)
                if (requestToken == loadRequestToken && activeTopicId == topicId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            questions = questions,
                            isEmpty = questions.isEmpty(),
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (requestToken == loadRequestToken && activeTopicId == topicId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load questions for topic."
                        )
                    }
                }
            }
        }
    }

    fun loadForSubject(subjectId: String) {
        val requestToken = ++loadRequestToken
        activeSubjectId = subjectId
        activeTopicId = null

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                successMessage = null
            )
        }

        viewModelScope.launch {
            try {
                val questions = repository.getQuestionsForSubject(subjectId)
                if (requestToken == loadRequestToken && activeSubjectId == subjectId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            questions = questions,
                            isEmpty = questions.isEmpty(),
                            errorMessage = null
                        )
                    }
                }
            } catch (e: Exception) {
                if (requestToken == loadRequestToken && activeSubjectId == subjectId) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = e.message ?: "Failed to load questions for subject."
                        )
                    }
                }
            }
        }
    }

    fun addQuestion(
        subjectId: String,
        unitId: String? = null,
        topicId: String? = null,
        questionText: String,
        marks: Int = 5,
        importance: Importance = Importance.MEDIUM,
        status: String = "NOT_PREPARED"
    ) {
        if (questionText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Question text cannot be blank.") }
            return
        }

        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.addQuestion(
                subjectId = subjectId,
                unitId = unitId,
                topicId = topicId,
                questionText = questionText,
                marks = marks,
                importance = importance,
                status = status
            )
            result.fold(
                onSuccess = { created ->
                    _uiState.update { current ->
                        val updatedList = current.questions + created
                        current.copy(
                            isSaving = false,
                            questions = updatedList,
                            isEmpty = false,
                            successMessage = "Question added successfully."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Failed to add question."
                        )
                    }
                }
            )
        }
    }

    fun updateQuestion(question: QuestionItem) {
        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.updateQuestion(question)
            result.fold(
                onSuccess = { updated ->
                    _uiState.update { current ->
                        val updatedList = current.questions.map { if (it.id == updated.id) updated else it }
                        current.copy(
                            isSaving = false,
                            questions = updatedList,
                            successMessage = "Question updated successfully."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Failed to update question."
                        )
                    }
                }
            )
        }
    }

    fun deleteQuestion(questionId: String) {
        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.deleteQuestion(questionId)
            result.fold(
                onSuccess = {
                    _uiState.update { current ->
                        val updatedList = current.questions.filter { it.id != questionId }
                        current.copy(
                            isSaving = false,
                            questions = updatedList,
                            isEmpty = updatedList.isEmpty(),
                            successMessage = "Question deleted."
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = error.message ?: "Failed to delete question."
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
