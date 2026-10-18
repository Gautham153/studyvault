package com.example.ui.screens.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.ExamItem
import com.example.data.model.Subject
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import com.example.util.StudyDateUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ExamsViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    val upcomingExams: StateFlow<List<ExamItem>> = repository.upcomingExams
    val pastExams: StateFlow<List<ExamItem>> = repository.pastExams
    val subjects: StateFlow<List<Subject>> = repository.subjects
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val errorMessage: StateFlow<String?> = repository.errorMessage

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _formError = MutableStateFlow<String?>(null)
    val formError: StateFlow<String?> = _formError.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    fun createExam(
        subjectId: String,
        name: String,
        date: String,
        venue: String,
        description: String?,
        onSuccess: () -> Unit
    ) {
        validate(subjectId, name, date)?.let {
            _formError.value = it
            return
        }
        if (_isSaving.value) return

        _isSaving.value = true
        _formError.value = null
        viewModelScope.launch {
            val result = repository.createExam(
                subjectId = subjectId.trim(),
                name = name.trim(),
                examDate = date.trim(),
                venue = venue.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() }
            )
            result.fold(
                onSuccess = { exam ->
                    _formError.value = null
                    _successMessage.value = "Scheduled ${exam.subjectName} exam on ${exam.examDate}"
                    onSuccess()
                },
                onFailure = { err ->
                    _formError.value = err.message ?: "Failed to schedule exam."
                }
            )
            _isSaving.value = false
        }
    }

    fun updateExam(
        examId: String,
        subjectId: String,
        name: String,
        date: String,
        venue: String,
        description: String?,
        onSuccess: () -> Unit
    ) {
        validate(subjectId, name, date)?.let {
            _formError.value = it
            return
        }
        if (_isSaving.value) return

        _isSaving.value = true
        _formError.value = null
        viewModelScope.launch {
            val result = repository.updateExam(
                examId = examId,
                subjectId = subjectId.trim(),
                name = name.trim(),
                examDate = date.trim(),
                venue = venue.trim(),
                description = description?.trim()?.takeIf { it.isNotEmpty() }
            )
            result.fold(
                onSuccess = { exam ->
                    _formError.value = null
                    _successMessage.value = "Updated ${exam.subjectName} exam"
                    onSuccess()
                },
                onFailure = { err ->
                    _formError.value = err.message ?: "Failed to update exam."
                }
            )
            _isSaving.value = false
        }
    }

    fun deleteExam(examId: String, onSuccess: () -> Unit) {
        if (_isSaving.value) return
        _isSaving.value = true
        _formError.value = null
        viewModelScope.launch {
            val result = repository.deleteExam(examId)
            result.fold(
                onSuccess = {
                    _formError.value = null
                    _successMessage.value = "Exam deleted"
                    onSuccess()
                },
                onFailure = { err ->
                    _formError.value = err.message ?: "Failed to delete exam."
                }
            )
            _isSaving.value = false
        }
    }

    fun clearFormError() {
        _formError.value = null
    }

    fun clearSuccessMessage() {
        _successMessage.value = null
    }

    private fun validate(subjectId: String, name: String, date: String): String? = when {
        subjectId.isBlank() -> "Select a subject for this exam."
        name.isBlank() -> "Exam name cannot be blank."
        !StudyDateUtils.isValidExamDateInput(date) ->
            "Enter a valid exam date (for example 'Nov 15, 2026')."
        else -> null
    }
}
