package com.example.data.repository

import com.example.data.model.Difficulty
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class MockStudyVaultRepository private constructor() : StudyVaultRepository {

    private val _overallProgress = MutableStateFlow(
        OverallProgress(
            semesterTitle = "Semester Preparation",
            overallPercentage = 78,
            subjectsCount = 6,
            upcomingExamsCount = 3,
            studentName = "Gautham",
            studentEmail = "gautham153@gmail.com",
            semester = "3rd Semester",
            cloudStorageUsed = "Usage unavailable"
        )
    )
    override val overallProgress: StateFlow<OverallProgress> = _overallProgress.asStateFlow()

    private val sampleMaterialsJava = listOf(
        MaterialItem("m1", "Unit 2 Notes.pdf", "PDF", "2.4 MB", "Oct 6, 2026"),
        MaterialItem("m2", "Exception Handling PPT.pptx", "PPT", "5.8 MB", "Oct 5, 2026"),
        MaterialItem("m3", "Important Questions.pdf", "PDF", "1.2 MB", "Oct 4, 2026"),
        MaterialItem("m4", "Example Programs.zip", "ZIP", "0.8 MB", "Oct 3, 2026"),
        MaterialItem("m5", "Reference Material.pdf", "PDF", "1.6 MB", "Oct 1, 2026")
    )

    private val sampleQuestionsJava = listOf(
        QuestionItem("q1", 1, "Explain exception hierarchy with runtime vs checked exceptions.", 10, "Unit 2", "Java", "Not Prepared"),
        QuestionItem("q2", 2, "Explain inheritance, polymorphism and method overriding.", 10, "Unit 2", "Java", "Learning"),
        QuestionItem("q3", 3, "Write a program demonstrating custom checked exception handling.", 5, "Unit 2", "Java", "Not Prepared"),
        QuestionItem("q4", 4, "Differentiate between final, finally, and finalize in Java.", 5, "Unit 2", "Java", "Prepared"),
        QuestionItem("q5", 5, "What is the use of packages and access specifiers in Java?", 5, "Unit 2", "Java", "Not Prepared")
    )

    private val unit2JavaTopics = listOf(
        TopicModel(
            id = "t_java_u2_1",
            unitId = "u_java_2",
            subjectId = "s_java",
            title = "Exception Hierarchy",
            status = TopicStatus.COMPLETED,
            difficulty = Difficulty.MEDIUM,
            importance = Importance.HIGH,
            lastStudiedDate = "Oct 7, 2026",
            nextRevisionDate = "Oct 10, 2026",
            priorityScore = 95,
            materialsCount = 2,
            questionsCount = 4,
            notesCount = 1,
            relatedMaterials = listOf(
                MaterialItem("m1", "Unit 2 Notes.pdf", "PDF", "2.4 MB"),
                MaterialItem("m2", "Exception Handling PPT.pptx", "PPT", "5.8 MB"),
                MaterialItem("m3", "Important Questions.pdf", "PDF", "1.2 MB")
            ),
            questions = sampleQuestionsJava.take(3)
        ),
        TopicModel(
            id = "t_java_u2_2",
            unitId = "u_java_2",
            subjectId = "s_java",
            title = "Inheritance",
            status = TopicStatus.COMPLETED,
            difficulty = Difficulty.EASY,
            importance = Importance.HIGH,
            lastStudiedDate = "Oct 5, 2026",
            nextRevisionDate = "Oct 12, 2026",
            priorityScore = 75,
            materialsCount = 2,
            questionsCount = 3,
            notesCount = 1
        ),
        TopicModel(
            id = "t_java_u2_3",
            unitId = "u_java_2",
            subjectId = "s_java",
            title = "Arrays",
            status = TopicStatus.LEARNING,
            difficulty = Difficulty.EASY,
            importance = Importance.MEDIUM,
            lastStudiedDate = "Oct 4, 2026",
            nextRevisionDate = "Oct 9, 2026",
            priorityScore = 65,
            materialsCount = 1,
            questionsCount = 2,
            notesCount = 1
        ),
        TopicModel(
            id = "t_java_u2_4",
            unitId = "u_java_2",
            subjectId = "s_java",
            title = "Strings",
            status = TopicStatus.LEARNING,
            difficulty = Difficulty.MEDIUM,
            importance = Importance.MEDIUM,
            lastStudiedDate = "Oct 3, 2026",
            nextRevisionDate = "Oct 9, 2026",
            priorityScore = 68,
            materialsCount = 2,
            questionsCount = 3,
            notesCount = 0
        ),
        TopicModel(
            id = "t_java_u2_5",
            unitId = "u_java_2",
            subjectId = "s_java",
            title = "IO Streams",
            status = TopicStatus.NOT_STARTED,
            difficulty = Difficulty.HARD,
            importance = Importance.HIGH,
            lastStudiedDate = "Not yet",
            nextRevisionDate = "Oct 11, 2026",
            priorityScore = 80,
            materialsCount = 1,
            questionsCount = 4,
            notesCount = 0
        ),
        TopicModel(
            id = "t_java_u2_6",
            unitId = "u_java_2",
            subjectId = "s_java",
            title = "Reflection",
            status = TopicStatus.NOT_STARTED,
            difficulty = Difficulty.HARD,
            importance = Importance.HIGH,
            lastStudiedDate = "Not yet",
            nextRevisionDate = "Oct 12, 2026",
            priorityScore = 73,
            materialsCount = 1,
            questionsCount = 2,
            notesCount = 0
        ),
        TopicModel(
            id = "t_java_u2_7",
            unitId = "u_java_2",
            subjectId = "s_java",
            title = "Java Packages",
            status = TopicStatus.COMPLETED,
            difficulty = Difficulty.EASY,
            importance = Importance.MEDIUM,
            lastStudiedDate = "Oct 6, 2026",
            nextRevisionDate = "Oct 14, 2026",
            priorityScore = 60,
            materialsCount = 1,
            questionsCount = 1,
            notesCount = 1
        )
    )

    private val javaUnits = listOf(
        UnitModel("u_java_1", "s_java", 1, "Object-Oriented Programming Fundamentals", 100, materialsCount = 8, questionsCount = 6),
        UnitModel("u_java_2", "s_java", 2, "Inheritance, Exceptions & Core Libraries", 80, topics = unit2JavaTopics, materialsCount = 7, questionsCount = 5),
        UnitModel("u_java_3", "s_java", 3, "Multithreading & Concurrency Management", 70, materialsCount = 9, questionsCount = 4),
        UnitModel("u_java_4", "s_java", 4, "Generics, Reflection & Annotations", 50, materialsCount = 11, questionsCount = 5),
        UnitModel("u_java_5", "s_java", 5, "JavaFX, Event Handling & GUI Components", 100, materialsCount = 12, questionsCount = 3)
    )

    private val dbmsUnits = listOf(
        UnitModel("u_dbms_1", "s_dbms", 1, "Relational Model & Algebra", 90, materialsCount = 6, questionsCount = 5),
        UnitModel("u_dbms_2", "s_dbms", 2, "SQL Queries & Normalization", 75, materialsCount = 8, questionsCount = 6),
        UnitModel("u_dbms_3", "s_dbms", 3, "Transaction Management & ACID", 65, materialsCount = 7, questionsCount = 4),
        UnitModel("u_dbms_4", "s_dbms", 4, "Concurrency Control & Recovery", 55, materialsCount = 9, questionsCount = 5),
        UnitModel("u_dbms_5", "s_dbms", 5, "Indexing, B-Trees & Query Optimization", 70, materialsCount = 7, questionsCount = 4)
    )

    private val aiUnits = listOf(
        UnitModel("u_ai_1", "s_ai", 1, "Search Algorithms & Problem Formulation", 95, materialsCount = 7, questionsCount = 5),
        UnitModel("u_ai_2", "s_ai", 2, "Informed Search & Heuristics", 90, materialsCount = 8, questionsCount = 6),
        UnitModel("u_ai_3", "s_ai", 3, "Adversarial Search & Game Playing", 92, materialsCount = 9, questionsCount = 5),
        UnitModel("u_ai_4", "s_ai", 4, "Knowledge Representation & Logic", 88, materialsCount = 8, questionsCount = 4),
        UnitModel("u_ai_5", "s_ai", 5, "Machine Learning Fundamentals", 90, materialsCount = 9, questionsCount = 5)
    )

    private val defaultSubjects = listOf(
        Subject(
            id = "s_java",
            name = "Java",
            code = "CS201",
            iconKey = "coffee",
            preparationPercent = 82,
            unitsCount = 5,
            topicsCount = 43,
            accentColorHex = 0xFF3B82F6,
            units = javaUnits,
            materialsCount = 47,
            questionsCount = 23,
            needsAttentionTopics = listOf("Exception Handling", "Reflection", "Java Packages")
        ),
        Subject(
            id = "s_dbms",
            name = "DBMS",
            code = "CS202",
            iconKey = "database",
            preparationPercent = 71,
            unitsCount = 5,
            topicsCount = 37,
            accentColorHex = 0xFF10B981,
            units = dbmsUnits,
            materialsCount = 37,
            questionsCount = 24,
            needsAttentionTopics = listOf("Normalization", "B-Trees", "Deadlock Detection")
        ),
        Subject(
            id = "s_ai",
            name = "Artificial Intelligence",
            code = "CS305",
            iconKey = "ai",
            preparationPercent = 91,
            unitsCount = 5,
            topicsCount = 41,
            accentColorHex = 0xFF8B5CF6,
            units = aiUnits,
            materialsCount = 41,
            questionsCount = 25,
            needsAttentionTopics = listOf("A* Search", "Alpha-Beta Pruning", "Constraint Satisfaction")
        ),
        Subject(
            id = "s_english",
            name = "English",
            code = "HU101",
            iconKey = "book",
            preparationPercent = 68,
            unitsCount = 3,
            topicsCount = 22,
            accentColorHex = 0xFFF59E0B,
            units = listOf(
                UnitModel("u_eng_1", "s_english", 1, "Technical Writing & Reports", 70),
                UnitModel("u_eng_2", "s_english", 2, "Professional Communication", 65),
                UnitModel("u_eng_3", "s_english", 3, "Grammar & Vocabulary", 70)
            ),
            materialsCount = 18,
            questionsCount = 12,
            needsAttentionTopics = listOf("Report Writing", "Executive Summaries")
        ),
        Subject(
            id = "s_math",
            name = "Mathematics",
            code = "MA201",
            iconKey = "math",
            preparationPercent = 41,
            unitsCount = 4,
            topicsCount = 28,
            accentColorHex = 0xFFEF4444,
            units = listOf(
                UnitModel("u_math_1", "s_math", 1, "Linear Algebra & Matrices", 60),
                UnitModel("u_math_2", "s_math", 2, "Multivariable Calculus", 40),
                UnitModel("u_math_3", "s_math", 3, "Probability & Statistics", 35),
                UnitModel("u_math_4", "s_math", 4, "Differential Equations", 30)
            ),
            materialsCount = 25,
            questionsCount = 32,
            needsAttentionTopics = listOf("Eigenvalues", "Bayes Theorem", "Differential Equations")
        ),
        Subject(
            id = "s_os",
            name = "Operating Systems",
            code = "CS204",
            iconKey = "os",
            preparationPercent = 56,
            unitsCount = 4,
            topicsCount = 26,
            accentColorHex = 0xFF06B6D4,
            units = listOf(
                UnitModel("u_os_1", "s_os", 1, "Processes & Scheduling", 75),
                UnitModel("u_os_2", "s_os", 2, "Memory Management & Paging", 50),
                UnitModel("u_os_3", "s_os", 3, "Storage & File Systems", 60),
                UnitModel("u_os_4", "s_os", 4, "Deadlocks & Synchronization", 40)
            ),
            materialsCount = 22,
            questionsCount = 18,
            needsAttentionTopics = listOf("Semaphore Synchronization", "Page Replacement Algorithms")
        )
    )

    private val _subjects = MutableStateFlow(defaultSubjects)
    override val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    private val defaultExams = listOf(
        ExamItem("e1", "s_java", "Java", "coffee", "Oct 20, 2026", 12, 74, "CS201", "Main Hall B, Seat 32"),
        ExamItem("e2", "s_dbms", "DBMS", "database", "Oct 23, 2026", 15, 61, "CS202", "Engineering Block 2, Room 104"),
        ExamItem("e3", "s_ai", "Artificial Intelligence", "ai", "Nov 5, 2026", 28, 48, "CS305", "Auditorium Annex")
    )
    private val _upcomingExams = MutableStateFlow(defaultExams)
    override val upcomingExams: StateFlow<List<ExamItem>> = _upcomingExams.asStateFlow()

    private val defaultPriorityItems = listOf(
        StudyItem("t_java_u2_1", "s_java", "u_java_2", "Exception Handling", "Java", "Unit 2", 95, "High Priority", "Revision due today"),
        StudyItem("t_dbms_u2_1", "s_dbms", "u_dbms_2", "DBMS Normalization", "DBMS", "Unit 2", 88, "High Priority", "Not completed"),
        StudyItem("t_ai_u3_1", "s_ai", "u_ai_3", "A* Search", "AI", "Unit 3", 81, "High Priority", "Revision due tomorrow"),
        StudyItem("t_java_u2_6", "s_java", "u_java_2", "Java Reflection", "Java", "Unit 4", 73, "Medium Priority", "Learning in progress"),
        StudyItem("t_dbms_u2_2", "s_dbms", "u_dbms_2", "SQL Joins", "DBMS", "Unit 2", 69, "Medium Priority", "Review suggested")
    )
    private val _priorityStudyItems = MutableStateFlow(defaultPriorityItems)
    override val priorityStudyItems: StateFlow<List<StudyItem>> = _priorityStudyItems.asStateFlow()

    override fun getTodayStudyItems(): List<StudyItem> {
        return _priorityStudyItems.value.take(3)
    }

    override fun getRecentlyAddedMaterials(): List<MaterialItem> {
        return listOf(
            MaterialItem("rm1", "Unit 2 Notes.pdf", "PDF", "2.4 MB", "Today"),
            MaterialItem("rm2", "DBMS Important Questions.pdf", "PDF", "1.8 MB", "Yesterday"),
            MaterialItem("rm3", "Java Programs.docx", "DOCX", "1.1 MB", "2 days ago")
        )
    }

    override fun getSubjectById(subjectId: String): Subject? {
        return _subjects.value.find { it.id == subjectId || it.name.equals(subjectId, ignoreCase = true) }
    }

    override fun getUnitById(subjectId: String, unitId: String): UnitModel? {
        val subject = getSubjectById(subjectId) ?: return null
        return subject.units.find { it.id == unitId || it.unitNumber.toString() == unitId }
    }

    override fun getTopicById(topicId: String): TopicModel? {
        for (subject in _subjects.value) {
            for (unit in subject.units) {
                val topic = unit.topics.find { it.id == topicId }
                if (topic != null) return topic
            }
        }
        // No fallback: an unknown topic id must resolve to null so callers never read or
        // display an unrelated topic.
        return null
    }

    override fun updateTopicStatus(topicId: String, status: TopicStatus) {
        _subjects.update { currentSubjects ->
            currentSubjects.map { subject ->
                subject.copy(
                    units = subject.units.map { unit ->
                        unit.copy(
                            topics = unit.topics.map { topic ->
                                if (topic.id == topicId) topic.copy(status = status) else topic
                            }
                        )
                    }
                )
            }
        }
    }

    override fun addSubject(subjectName: String, code: String, iconKey: String) {
        val newSub = Subject(
            id = "s_${System.currentTimeMillis()}",
            name = subjectName,
            code = code.ifBlank { "SUB" },
            iconKey = iconKey,
            preparationPercent = 0,
            unitsCount = 3,
            topicsCount = 15,
            accentColorHex = 0xFF6366F1,
            units = listOf(
                UnitModel("u_new_1", "new", 1, "Fundamentals", 0),
                UnitModel("u_new_2", "new", 2, "Core Concepts", 0),
                UnitModel("u_new_3", "new", 3, "Advanced Applications", 0)
            )
        )
        _subjects.update { it + newSub }
        _overallProgress.update { it.copy(subjectsCount = it.subjectsCount + 1) }
    }

    override fun addExam(subjectName: String, date: String, daysRemaining: Int) {
        val newExam = ExamItem(
            id = "e_${System.currentTimeMillis()}",
            subjectId = "s_custom",
            subjectName = subjectName,
            iconKey = "book",
            examDate = date,
            daysRemaining = daysRemaining,
            preparationPercent = 40
        )
        _upcomingExams.update { it + newExam }
        _overallProgress.update { it.copy(upcomingExamsCount = it.upcomingExamsCount + 1) }
    }

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    override fun setAuthenticatedUser(email: String, displayName: String) {
        _overallProgress.update {
            it.copy(
                studentName = displayName.ifBlank { email.substringBefore("@").replaceFirstChar { c -> c.uppercase() } },
                studentEmail = email
            )
        }
    }

    override fun clearData() {
        _errorMessage.value = null
    }

    // In-memory questions and notes storage for tests and mock mode
    private val mutableQuestions = sampleQuestionsJava.map {
        it.copy(subjectId = "s_java", unitId = "u_java_2", topicId = "t_java_u2_1")
    }.toMutableList()

    private val mutableNotes = mutableListOf(
        NoteItem(
            id = "n_java_1",
            subjectId = "s_java",
            topicId = "t_java_u2_1",
            title = "Exception Hierarchy Core Summary",
            content = "Throwable is root class. Exception is for conditions that a reasonable application might want to catch. Error indicates serious problems.",
            createdAt = "Oct 7, 2026",
            updatedAt = "Oct 7, 2026",
            subjectName = "Java",
            topicName = "Exception Hierarchy"
        ),
        NoteItem(
            id = "n_java_2",
            subjectId = "s_java",
            topicId = "t_java_u2_2",
            title = "Polymorphism & Dynamic Dispatch",
            content = "Runtime polymorphism achieved through method overriding. Resolved at runtime using dynamic method dispatch.",
            createdAt = "Oct 5, 2026",
            updatedAt = "Oct 5, 2026",
            subjectName = "Java",
            topicName = "Inheritance"
        )
    )

    override suspend fun getQuestionsForSubject(subjectId: String): List<QuestionItem> {
        return mutableQuestions.filter { it.subjectId == subjectId }
    }

    override suspend fun getQuestionsForTopic(topicId: String): List<QuestionItem> {
        return mutableQuestions.filter { it.topicId == topicId }
    }

    override suspend fun addQuestion(
        subjectId: String,
        unitId: String?,
        topicId: String?,
        questionText: String,
        marks: Int,
        importance: Importance,
        status: String
    ): Result<QuestionItem> {
        if (questionText.isBlank()) {
            return Result.failure(IllegalArgumentException("Question text cannot be blank."))
        }
        if (marks <= 0) {
            return Result.failure(IllegalArgumentException("Marks must be a positive number."))
        }
        val sub = getSubjectById(subjectId)
        val unit = if (unitId != null && sub != null) getUnitById(subjectId, unitId) else null
        val newQ = QuestionItem(
            id = "q_${System.currentTimeMillis()}",
            questionNumber = mutableQuestions.count { it.subjectId == subjectId } + 1,
            questionText = questionText.trim(),
            marks = marks,
            unitName = unit?.title ?: "Unit 1",
            subjectName = sub?.name ?: "Subject",
            status = status,
            subjectId = subjectId,
            unitId = unitId,
            topicId = topicId,
            importance = importance,
            createdAt = "Today",
            updatedAt = "Today"
        )
        mutableQuestions.add(newQ)
        return Result.success(newQ)
    }

    override suspend fun updateQuestion(question: QuestionItem): Result<QuestionItem> {
        val idx = mutableQuestions.indexOfFirst { it.id == question.id }
        if (idx == -1) {
            return Result.failure(NoSuchElementException("Question not found."))
        }
        mutableQuestions[idx] = question
        return Result.success(question)
    }

    override suspend fun deleteQuestion(questionId: String): Result<Unit> {
        val removed = mutableQuestions.removeAll { it.id == questionId }
        return if (removed) Result.success(Unit) else Result.failure(NoSuchElementException("Question not found."))
    }

    override suspend fun getNotesForSubject(subjectId: String): List<NoteItem> {
        return mutableNotes.filter { it.subjectId == subjectId }
    }

    override suspend fun getNotesForTopic(topicId: String): List<NoteItem> {
        return mutableNotes.filter { it.topicId == topicId }
    }

    override suspend fun addNote(
        subjectId: String,
        topicId: String?,
        title: String,
        content: String
    ): Result<NoteItem> {
        if (title.isBlank()) {
            return Result.failure(IllegalArgumentException("Note title cannot be blank."))
        }
        val sub = getSubjectById(subjectId)
        val top = if (topicId != null) getTopicById(topicId) else null
        val newNote = NoteItem(
            id = "n_${System.currentTimeMillis()}",
            subjectId = subjectId,
            topicId = topicId,
            title = title.trim(),
            content = content.trim(),
            createdAt = "Today",
            updatedAt = "Today",
            subjectName = sub?.name ?: "",
            topicName = top?.title ?: ""
        )
        mutableNotes.add(newNote)
        return Result.success(newNote)
    }

    override suspend fun updateNote(note: NoteItem): Result<NoteItem> {
        val idx = mutableNotes.indexOfFirst { it.id == note.id }
        if (idx == -1) {
            return Result.failure(NoSuchElementException("Note not found."))
        }
        mutableNotes[idx] = note
        return Result.success(note)
    }

    override suspend fun deleteNote(noteId: String): Result<Unit> {
        val removed = mutableNotes.removeAll { it.id == noteId }
        return if (removed) Result.success(Unit) else Result.failure(NoSuchElementException("Note not found."))
    }

    companion object {
        val instance: MockStudyVaultRepository by lazy { MockStudyVaultRepository() }

        /** Creates an isolated instance for unit tests so tests never share mutable singleton state. */
        fun createIsolated(): MockStudyVaultRepository = MockStudyVaultRepository()
    }
}
