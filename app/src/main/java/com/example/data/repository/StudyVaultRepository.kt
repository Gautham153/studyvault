package com.example.data.repository

import com.example.data.model.ExamItem
import com.example.data.model.Importance
import com.example.data.model.MaterialItem
import com.example.data.model.NoteItem
import com.example.data.model.OverallProgress
import com.example.data.model.QuestionItem
import com.example.data.model.RevisionEvent
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
    val pastExams: StateFlow<List<ExamItem>>
    val recentRevisions: StateFlow<List<RevisionEvent>>
    val priorityStudyItems: StateFlow<List<StudyItem>>
    val studyRecommendations: StateFlow<List<StudyRecommendation>>
    val allStudyRecommendations: StateFlow<List<StudyRecommendation>>
    val todayStudyPlan: StateFlow<List<StudyRecommendation>>
    val isLoading: StateFlow<Boolean>
    val errorMessage: StateFlow<String?>

    fun getTodayStudyItems(): List<StudyItem>
    fun setDailyStudyMinutesTarget(minutes: Int?)
    fun getRecentlyAddedMaterials(): List<MaterialItem>
    fun getSubjectById(subjectId: String): Subject?
    fun getUnitById(subjectId: String, unitId: String): UnitModel?
    fun getTopicById(topicId: String): TopicModel?
    fun getExamById(examId: String): ExamItem?
    fun getRevisionEventsForTopic(topicId: String): List<RevisionEvent>
    fun addSubject(subjectName: String, code: String, iconKey: String)
    suspend fun updateSubject(subjectId: String, subjectName: String, code: String, iconKey: String): Result<Subject>
    suspend fun deleteSubject(subjectId: String): Result<Unit>

    // Unit management
    suspend fun createUnit(subjectId: String, name: String, orderIndex: Int? = null): Result<UnitModel>
    suspend fun updateUnit(subjectId: String, unitId: String, name: String, orderIndex: Int? = null): Result<UnitModel>
    suspend fun deleteUnit(subjectId: String, unitId: String): Result<Unit>

    // Topic management
    suspend fun createTopic(subjectId: String, unitId: String, title: String): Result<TopicModel>

    fun setAuthenticatedUser(email: String, displayName: String)
    fun clearData()

    // Topic preparation + revision tracking
    suspend fun updateTopicStatus(topicId: String, status: TopicStatus): Result<Unit>
    suspend fun markTopicRevised(topicId: String, notes: String? = null): Result<RevisionEvent>

    // Exam planning
    suspend fun createExam(
        subjectId: String,
        name: String,
        examDate: String,
        venue: String = "",
        description: String? = null
    ): Result<ExamItem>

    suspend fun updateExam(
        examId: String,
        subjectId: String,
        name: String,
        examDate: String,
        venue: String = "",
        description: String? = null
    ): Result<ExamItem>

    suspend fun deleteExam(examId: String): Result<Unit>

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
