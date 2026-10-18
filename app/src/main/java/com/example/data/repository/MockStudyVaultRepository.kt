package com.example.data.repository

import com.example.data.model.Difficulty
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
import com.example.util.StudyDateUtils
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

    private val mutableExams = mutableListOf(
        ExamItem("e1", "s_java", "Java", "coffee", "Oct 20, 2026", 0, 0, "CS201", "Main Hall B, Seat 32", name = "Mid Term Exam"),
        ExamItem("e2", "s_dbms", "DBMS", "database", "Oct 23, 2026", 0, 0, "CS202", "Engineering Block 2, Room 104", name = "Mid Term Exam"),
        ExamItem("e3", "s_ai", "Artificial Intelligence", "ai", "Nov 5, 2026", 0, 0, "CS305", "Auditorium Annex", name = "Semester Final")
    )

    private val mutableRevisionEvents = mutableListOf<RevisionEvent>()

    private val _upcomingExams = MutableStateFlow<List<ExamItem>>(emptyList())
    override val upcomingExams: StateFlow<List<ExamItem>> = _upcomingExams.asStateFlow()

    private val _pastExams = MutableStateFlow<List<ExamItem>>(emptyList())
    override val pastExams: StateFlow<List<ExamItem>> = _pastExams.asStateFlow()

    private val _recentRevisions = MutableStateFlow<List<RevisionEvent>>(emptyList())
    override val recentRevisions: StateFlow<List<RevisionEvent>> = _recentRevisions.asStateFlow()

    private val _priorityStudyItems = MutableStateFlow<List<StudyItem>>(emptyList())
    override val priorityStudyItems: StateFlow<List<StudyItem>> = _priorityStudyItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        seedRevisionHistory()
        recalculateDerivedState()
    }

    private fun seedRevisionHistory() {
        recordSeedRevision("t_java_u2_1", "2026-10-07T09:00:00Z")
        recordSeedRevision("t_java_u2_2", "2026-10-05T09:00:00Z")
        recordSeedRevision("t_java_u2_1", "2026-10-02T09:00:00Z")
    }

    private fun recordSeedRevision(topicId: String, revisedAt: String) {
        val topic = getTopicById(topicId) ?: return
        val subject = getSubjectById(topic.subjectId)
        val unit = subject?.units?.find { it.id == topic.unitId }
        val number = (mutableRevisionEvents.filter { it.topicId == topicId }.maxOfOrNull { it.revisionNumber } ?: 0) + 1
        mutableRevisionEvents.add(
            RevisionEvent(
                id = "r_${topicId}_$number",
                topicId = topicId,
                subjectId = topic.subjectId,
                unitId = topic.unitId,
                topicName = topic.title,
                subjectName = subject?.name ?: "",
                unitName = unit?.title ?: "",
                revisionNumber = number,
                previousStatus = topic.status,
                newStatus = topic.status,
                revisedAt = revisedAt,
                revisedAtDisplay = StudyDateUtils.toDisplayDate(revisedAt)
            )
        )
    }

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

    override suspend fun updateTopicStatus(topicId: String, status: TopicStatus): Result<Unit> {
        val topic = getTopicById(topicId)
            ?: return Result.failure(NoSuchElementException("Topic not found."))

        val now = StudyDateUtils.nowIsoTimestamp()
        _subjects.update { currentSubjects ->
            currentSubjects.map { subject ->
                subject.copy(
                    units = subject.units.map { unit ->
                        unit.copy(
                            topics = unit.topics.map { t ->
                                if (t.id == topicId) {
                                    t.copy(
                                        status = status,
                                        completedAt = if (status == TopicStatus.COMPLETED) t.completedAt ?: now else null,
                                        lastStudiedDate = if (status == TopicStatus.NOT_STARTED) {
                                            t.lastStudiedDate
                                        } else {
                                            StudyDateUtils.toDisplayDate(now)
                                        }
                                    )
                                } else t
                            }
                        )
                    }
                )
            }
        }
        recalculateDerivedState()
        return Result.success(Unit)
    }

    override suspend fun markTopicRevised(topicId: String, notes: String?): Result<RevisionEvent> {
        val topic = getTopicById(topicId)
            ?: return Result.failure(NoSuchElementException("Topic not found."))
        val subject = getSubjectById(topic.subjectId)
        val unit = subject?.units?.find { it.id == topic.unitId }

        val now = StudyDateUtils.nowIsoTimestamp()
        val number = (mutableRevisionEvents.filter { it.topicId == topicId }.maxOfOrNull { it.revisionNumber } ?: 0) + 1
        val nextStatus = if (topic.status == TopicStatus.NEEDS_REVISION) TopicStatus.COMPLETED else topic.status

        val event = RevisionEvent(
            id = "r_${System.currentTimeMillis()}",
            topicId = topicId,
            subjectId = topic.subjectId,
            unitId = topic.unitId,
            topicName = topic.title,
            subjectName = subject?.name ?: "",
            unitName = unit?.title ?: "",
            revisionNumber = number,
            previousStatus = topic.status,
            newStatus = nextStatus,
            revisedAt = now,
            revisedAtDisplay = StudyDateUtils.toDisplayDate(now),
            notes = notes?.trim()?.takeIf { it.isNotEmpty() }
        )
        mutableRevisionEvents.add(event)

        _subjects.update { currentSubjects ->
            currentSubjects.map { s ->
                s.copy(
                    units = s.units.map { u ->
                        u.copy(
                            topics = u.topics.map { t ->
                                if (t.id != topicId) {
                                    t
                                } else {
                                    t.copy(
                                        status = nextStatus,
                                        revisionCount = number,
                                        lastRevisedAt = now,
                                        lastStudiedDate = StudyDateUtils.toDisplayDate(now),
                                        completedAt = if (nextStatus == TopicStatus.COMPLETED) {
                                            t.completedAt ?: now
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        )
                    }
                )
            }
        }
        recalculateDerivedState()
        return Result.success(event)
    }

    override fun getRevisionEventsForTopic(topicId: String): List<RevisionEvent> =
        StudyPlanning.sortRevisionEventsNewestFirst(mutableRevisionEvents.filter { it.topicId == topicId })

    override fun getExamById(examId: String): ExamItem? = mutableExams.find { it.id == examId }

    override suspend fun createExam(
        subjectId: String,
        name: String,
        examDate: String,
        venue: String,
        description: String?
    ): Result<ExamItem> {
        val subject = resolveSubject(subjectId)
            ?: return Result.failure(IllegalArgumentException("Subject not found. Create the subject before scheduling an exam."))
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Exam name cannot be blank."))
        }
        val iso = StudyDateUtils.parseExamDateToIso(examDate)
            ?: return Result.failure(IllegalArgumentException("Invalid exam date '$examDate'. Use a format like 'Nov 15, 2026' or '2026-11-15'."))

        val exam = ExamItem(
            id = "e_${System.currentTimeMillis()}",
            subjectId = subject.id,
            subjectName = subject.name,
            iconKey = subject.iconKey,
            examDate = StudyDateUtils.toDisplayDate(iso),
            daysRemaining = StudyDateUtils.daysFromToday(iso) ?: 0,
            preparationPercent = subject.preparationPercent,
            code = subject.code,
            venue = venue.trim().ifBlank { "Venue not set" },
            name = trimmedName,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            isoDate = iso
        )
        mutableExams.add(exam)
        recalculateDerivedState()
        return Result.success(exam)
    }

    override suspend fun updateExam(
        examId: String,
        subjectId: String,
        name: String,
        examDate: String,
        venue: String,
        description: String?
    ): Result<ExamItem> {
        val index = mutableExams.indexOfFirst { it.id == examId }
        if (index == -1) {
            return Result.failure(NoSuchElementException("Exam not found."))
        }
        val subject = resolveSubject(subjectId)
            ?: return Result.failure(IllegalArgumentException("Subject not found."))
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return Result.failure(IllegalArgumentException("Exam name cannot be blank."))
        }
        val iso = StudyDateUtils.parseExamDateToIso(examDate)
            ?: return Result.failure(IllegalArgumentException("Invalid exam date '$examDate'. Use a format like 'Nov 15, 2026' or '2026-11-15'."))

        val updated = mutableExams[index].copy(
            subjectId = subject.id,
            subjectName = subject.name,
            iconKey = subject.iconKey,
            examDate = StudyDateUtils.toDisplayDate(iso),
            daysRemaining = StudyDateUtils.daysFromToday(iso) ?: 0,
            preparationPercent = subject.preparationPercent,
            code = subject.code,
            venue = venue.trim().ifBlank { "Venue not set" },
            name = trimmedName,
            description = description?.trim()?.takeIf { it.isNotEmpty() },
            isoDate = iso
        )
        mutableExams[index] = updated
        recalculateDerivedState()
        return Result.success(updated)
    }

    override suspend fun deleteExam(examId: String): Result<Unit> {
        val removed = mutableExams.removeAll { it.id == examId }
        if (!removed) return Result.failure(NoSuchElementException("Exam not found."))
        recalculateDerivedState()
        return Result.success(Unit)
    }

    private fun resolveSubject(subjectId: String): Subject? {
        val trimmed = subjectId.trim()
        if (trimmed.isEmpty()) return null
        return _subjects.value.find {
            it.id == trimmed ||
                it.name.equals(trimmed, ignoreCase = true) ||
                it.code.equals(trimmed, ignoreCase = true)
        }
    }

    private fun recalculateDerivedState() {
        val subjects = _subjects.value.map { subject ->
            val units = subject.units.map { unit ->
                if (unit.topics.isEmpty()) unit else unit.copy(preparationPercent = StudyPlanning.preparationPercent(unit.topics))
            }
            val trackedTopics = units.flatMap { it.topics }
            if (trackedTopics.isEmpty()) {
                subject.copy(units = units)
            } else {
                subject.copy(
                    units = units,
                    preparationPercent = StudyPlanning.preparationPercent(trackedTopics),
                    topicsCount = trackedTopics.size
                )
            }
        }
        _subjects.value = subjects

        val revisionEventsByTopic = mutableRevisionEvents.groupBy { it.topicId }
        _priorityStudyItems.value = StudyPlanning.buildPriorityStudyItems(subjects, revisionEventsByTopic)
        _recentRevisions.value = StudyPlanning.recentRevisions(mutableRevisionEvents)
        publishExams(subjects)

        val topics = StudyPlanning.allTopics(subjects)
        val counts = StudyPlanning.topicCounts(topics)
        _overallProgress.update { current ->
            current.copy(
                overallPercentage = if (topics.isEmpty()) current.overallPercentage else StudyPlanning.preparationPercent(topics),
                subjectsCount = subjects.size,
                upcomingExamsCount = _upcomingExams.value.size,
                totalTopics = counts.total,
                completedTopics = counts.completed,
                inProgressTopics = counts.inProgress,
                topicsNeedingRevision = counts.needsRevision
            )
        }
    }

    private fun publishExams(subjects: List<Subject>) {
        val derived = mutableExams.map { exam ->
            val subject = subjects.find { it.id == exam.subjectId }
                ?: subjects.find { it.name.equals(exam.subjectName, ignoreCase = true) }
            val iso = exam.isoDate.ifBlank { StudyDateUtils.parseExamDateToIso(exam.examDate).orEmpty() }
            exam.copy(
                subjectName = subject?.name ?: exam.subjectName,
                iconKey = subject?.iconKey ?: exam.iconKey,
                code = subject?.code ?: exam.code,
                preparationPercent = subject?.preparationPercent ?: exam.preparationPercent,
                examDate = if (iso.isBlank()) exam.examDate else StudyDateUtils.toDisplayDate(iso),
                isoDate = iso,
                daysRemaining = StudyDateUtils.daysFromToday(iso) ?: exam.daysRemaining
            )
        }
        val schedule = StudyPlanning.classifyExams(derived)
        _upcomingExams.value = schedule.upcoming
        _pastExams.value = schedule.past
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
        recalculateDerivedState()
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
        _isLoading.value = false
        recalculateDerivedState()
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
