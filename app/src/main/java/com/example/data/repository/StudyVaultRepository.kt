package com.example.data.repository

import com.example.data.model.ExamItem
import com.example.data.model.Importance
import com.example.data.model.MaterialItem
import com.example.data.model.NoteItem
import com.example.data.model.OverallProgress
import com.example.data.model.QuestionItem
import com.example.data.model.StudyItem
import com.example.data.model.Subject
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.data.model.UnitModel
import kotlinx.coroutines.flow.StateFlow

interface StudyVaultRepository {
    val overallProgress: StateFlow<OverallProgress>
    val subjects: StateFlow<List<Subject>>
    val upcomingExams: StateFlow<List<ExamItem>>
    val priorityStudyItems: StateFlow<List<StudyItem>>
    val errorMessage: StateFlow<String?>

    fun getTodayStudyItems(): List<StudyItem>
    fun getRecentlyAddedMaterials(): List<MaterialItem>
    fun getSubjectById(subjectId: String): Subject?
    fun getUnitById(subjectId: String, unitId: String): UnitModel?
    fun getTopicById(topicId: String): TopicModel?
    fun updateTopicStatus(topicId: String, status: TopicStatus)
    fun addSubject(subjectName: String, code: String, iconKey: String)
    fun addExam(subjectName: String, date: String, daysRemaining: Int)
    fun setAuthenticatedUser(email: String, displayName: String)
    fun clearData()

    // Questions CRUD
    suspend fun getQuestionsForSubject(subjectId: String): List<QuestionItem>
    suspend fun getQuestionsForTopic(topicId: String): List<QuestionItem>
    suspend fun addQuestion(
        subjectId: String,
        unitId: String? = null,
        topicId: String? = null,
        questionText: String,
        marks: Int = 5,
        importance: Importance = Importance.MEDIUM,
        status: String = "NOT_PREPARED"
    ): Result<QuestionItem>
    suspend fun updateQuestion(question: QuestionItem): Result<QuestionItem>
    suspend fun deleteQuestion(questionId: String): Result<Unit>

    // Notes CRUD
    suspend fun getNotesForSubject(subjectId: String): List<NoteItem>
    suspend fun getNotesForTopic(topicId: String): List<NoteItem>
    suspend fun addNote(
        subjectId: String,
        topicId: String? = null,
        title: String,
        content: String = ""
    ): Result<NoteItem>
    suspend fun updateNote(note: NoteItem): Result<NoteItem>
    suspend fun deleteNote(noteId: String): Result<Unit>
}
