package com.example.ui.screens.exams

import androidx.lifecycle.ViewModel
import com.example.data.model.ExamItem
import com.example.data.model.Subject
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import kotlinx.coroutines.flow.StateFlow

class ExamsViewModel(
    private val repository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    val upcomingExams: StateFlow<List<ExamItem>> = repository.upcomingExams
    val subjects: StateFlow<List<Subject>> = repository.subjects
    val errorMessage: StateFlow<String?> = repository.errorMessage

    fun addExam(subjectName: String, date: String, daysRemaining: Int) {
        if (subjectName.isNotBlank()) {
            repository.addExam(subjectName.trim(), date.trim(), daysRemaining)
        }
    }
}
