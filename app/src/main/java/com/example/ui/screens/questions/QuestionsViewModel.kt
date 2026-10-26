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
        // Observe repository subjects updates to keep questions reactive.
        // Every refresh verifies the active request token and resource before publishing,
        // so a late result from a previous topic/subject can never overwrite current UI.
        viewModelScope.launch {
            repository.subjects.collect { _ ->
                val requestToken = loadRequestToken
                val tId = activeTopicId
                val sId = activeSubjectId
                try {
                    if (tId != null) {
                        val questions = repository.getQuestionsForTopic(tId)
                        if (requestToken == loadRequestToken && activeTopicId == tId) {
                            _uiState.update { current ->
                                current.copy(
                                    questions = questions,
                                    isEmpty = questions.isEmpty()
                                )
                            }
                        }
                    } else if (sId != null) {
                        val questions = repository.getQuestionsForSubject(sId)
                        if (requestToken == loadRequestToken && activeSubjectId == sId) {
                            _uiState.update { current ->
                                current.copy(
                                    questions = questions,
                                    isEmpty = questions.isEmpty()
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    if (requestToken == loadRequestToken) {
                        _uiState.update {
                            it.copy(errorMessage = e.message ?: "Failed to refresh questions.")
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
                // Clear the previous resource's questions so they are never displayed
                // for the newly selected topic while the load is in flight.
                questions = if (resourceChanged) emptyList() else it.questions,
                isEmpty = if (resourceChanged) true else it.isEmpty,
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
        val resourceChanged = activeSubjectId != subjectId || activeTopicId != null
        val requestToken = ++loadRequestToken
        activeSubjectId = subjectId
        activeTopicId = null

        _uiState.update {
            it.copy(
                isLoading = true,
                // Clear the previous resource's questions while the new subject loads.
                questions = if (resourceChanged) emptyList() else it.questions,
                isEmpty = if (resourceChanged) true else it.isEmpty,
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

    /**
     * True only when the resource the UI is currently showing (and its load generation) is
     * exactly the one captured before an asynchronous mutation started.
     */
    private fun isSameActiveResource(topicId: String?, subjectId: String?, token: Int): Boolean =
        token == loadRequestToken && activeTopicId == topicId && activeSubjectId == subjectId

    /**
     * Re-reads the currently active resource after a mutation persisted for a resource the user
     * has since navigated away from. Keeps the newly selected list correct without ever applying
     * the old resource's result to it.
     */
    private suspend fun refreshActiveResource() {
        val topicId = activeTopicId
        val subjectId = activeSubjectId
        val token = loadRequestToken
        try {
            val questions = when {
                topicId != null -> repository.getQuestionsForTopic(topicId)
                subjectId != null -> repository.getQuestionsForSubject(subjectId)
                else -> return
            }
            if (token == loadRequestToken && activeTopicId == topicId && activeSubjectId == subjectId) {
                _uiState.update { it.copy(questions = questions, isEmpty = questions.isEmpty()) }
            }
        } catch (e: Exception) {
            if (token == loadRequestToken) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Failed to refresh questions.") }
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
        if (_uiState.value.isSaving) return // guard against duplicate submissions
        if (questionText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Question text cannot be blank.") }
            return
        }
        if (marks <= 0) {
            _uiState.update { it.copy(errorMessage = "Marks must be a positive number.") }
            return
        }

        // Capture the resource currently shown before the async mutation starts, so a resource
        // switch while it is in flight can never cause the result to land on the new resource.
        val contextTopicId = activeTopicId
        val contextSubjectId = activeSubjectId
        val contextToken = loadRequestToken

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
                    if (isSameActiveResource(contextTopicId, contextSubjectId, contextToken)) {
                        _uiState.update { current ->
                            // Idempotent by ID: the repository's subjects flow may have already
                            // delivered this question via a refresh, so update in place
                            // instead of appending it again (duplicate-question regression).
                            val appended =
                                if (current.questions.any { it.id == created.id }) {
                                    current.questions.map { if (it.id == created.id) created else it }
                                } else {
                                    current.questions + created
                                }
                            current.copy(
                                isSaving = false,
                                questions = appended,
                                isEmpty = false,
                                successMessage = "Question added successfully."
                            )
                        }
                    } else {
                        // Persisted, but the user moved to another resource. Never apply it here;
                        // refresh the newly selected resource from the source of truth instead.
                        _uiState.update { it.copy(isSaving = false, successMessage = "Question added successfully.") }
                        refreshActiveResource()
                    }
                },
                onFailure = { error ->
                    // Always clear isSaving so a failed save can never leave the form stuck.
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
        if (_uiState.value.isSaving) return // guard against duplicate submissions
        if (question.questionText.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Question text cannot be blank.") }
            return
        }
        if (question.marks <= 0) {
            _uiState.update { it.copy(errorMessage = "Marks must be a positive number.") }
            return
        }
        // Capture the active resource before the async mutation starts.
        val contextTopicId = activeTopicId
        val contextSubjectId = activeSubjectId
        val contextToken = loadRequestToken

        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.updateQuestion(question)
            result.fold(
                onSuccess = { updated ->
                    if (isSameActiveResource(contextTopicId, contextSubjectId, contextToken)) {
                        _uiState.update { current ->
                            val updatedList = current.questions.map { if (it.id == updated.id) updated else it }
                            current.copy(
                                isSaving = false,
                                questions = updatedList,
                                successMessage = "Question updated successfully."
                            )
                        }
                    } else {
                        // Persisted for the old resource; refresh the newly selected one instead.
                        _uiState.update { it.copy(isSaving = false, successMessage = "Question updated successfully.") }
                        refreshActiveResource()
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
        if (_uiState.value.isSaving) return // guard against duplicate submissions
        // Capture the active resource before the async mutation starts.
        val contextTopicId = activeTopicId
        val contextSubjectId = activeSubjectId
        val contextToken = loadRequestToken

        _uiState.update { it.copy(isSaving = true, errorMessage = null, successMessage = null) }

        viewModelScope.launch {
            val result = repository.deleteQuestion(questionId)
            result.fold(
                onSuccess = {
                    if (isSameActiveResource(contextTopicId, contextSubjectId, contextToken)) {
                        _uiState.update { current ->
                            val updatedList = current.questions.filter { it.id != questionId }
                            current.copy(
                                isSaving = false,
                                questions = updatedList,
                                isEmpty = updatedList.isEmpty(),
                                successMessage = "Question deleted."
                            )
                        }
                    } else {
                        // Deleted for the old resource; refresh the newly selected one instead.
                        _uiState.update { it.copy(isSaving = false, successMessage = "Question deleted.") }
                        refreshActiveResource()
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
