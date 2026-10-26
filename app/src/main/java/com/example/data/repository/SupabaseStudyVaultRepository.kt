package com.example.data.repository

import android.util.Log
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
import com.example.data.model.extractFileType
import com.example.data.model.formatByteSize
import com.example.data.model.formatSemesterLabel
import com.example.data.model.mapQuestionStatusToDisplay
import com.example.data.model.parseSemesterNumber
import com.example.data.remote.ExamDto
import com.example.data.remote.NoteDto
import com.example.data.remote.QuestionDto
import com.example.data.remote.RevisionHistoryDto
import com.example.data.remote.StudyMaterialDto
import com.example.data.remote.SubjectDto
import com.example.data.remote.SupabaseClientProvider
import com.example.data.remote.TopicDto
import com.example.data.remote.UnitDto
import com.example.util.StudyDateUtils
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.storage.storage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

internal const val DEFAULT_QUESTION_MARKS = 5

internal fun QuestionDto.marksOrDefault(): Int = marks ?: DEFAULT_QUESTION_MARKS

class SupabaseStudyVaultRepository private constructor() : StudyVaultRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val topicMutex = Mutex()
    private val questionMutex = Mutex()
    private val noteMutex = Mutex()

    private val defaultProgress = OverallProgress(
        semesterTitle = "Semester Preparation",
        overallPercentage = 0,
        subjectsCount = 0,
        upcomingExamsCount = 0,
        studentName = "Student",
        studentEmail = "",
        semester = "3rd Semester",
        cloudStorageUsed = "Usage unavailable"
    )

    private val _overallProgress = MutableStateFlow(defaultProgress)
    override val overallProgress: StateFlow<OverallProgress> = _overallProgress.asStateFlow()

    private val _subjects = MutableStateFlow<List<Subject>>(emptyList())
    override val subjects: StateFlow<List<Subject>> = _subjects.asStateFlow()

    private val _upcomingExams = MutableStateFlow<List<ExamItem>>(emptyList())
    override val upcomingExams: StateFlow<List<ExamItem>> = _upcomingExams.asStateFlow()

    private val _pastExams = MutableStateFlow<List<ExamItem>>(emptyList())
    override val pastExams: StateFlow<List<ExamItem>> = _pastExams.asStateFlow()

    private val _recentRevisions = MutableStateFlow<List<RevisionEvent>>(emptyList())
    override val recentRevisions: StateFlow<List<RevisionEvent>> = _recentRevisions.asStateFlow()

    private val _priorityStudyItems = MutableStateFlow<List<StudyItem>>(emptyList())
    override val priorityStudyItems: StateFlow<List<StudyItem>> = _priorityStudyItems.asStateFlow()

    private val _studyRecommendations = MutableStateFlow<List<StudyRecommendation>>(emptyList())
    override val studyRecommendations: StateFlow<List<StudyRecommendation>> = _studyRecommendations.asStateFlow()

    private val _allStudyRecommendations = MutableStateFlow<List<StudyRecommendation>>(emptyList())
    override val allStudyRecommendations: StateFlow<List<StudyRecommendation>> = _allStudyRecommendations.asStateFlow()

    private val _todayStudyPlan = MutableStateFlow<List<StudyRecommendation>>(emptyList())
    override val todayStudyPlan: StateFlow<List<StudyRecommendation>> = _todayStudyPlan.asStateFlow()

    private var dailyMinutesTarget: Int? = null

    private val _isLoading = MutableStateFlow(false)
    override val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var loadRequestToken = 0

    // Cached raw collections for fast entity composition
    private var allUnits: List<UnitDto> = emptyList()
    private var allTopics: List<TopicDto> = emptyList()
    private var allQuestions: List<QuestionDto> = emptyList()
    private var allNotes: List<NoteDto> = emptyList()
    private var allMaterials: List<StudyMaterialDto> = emptyList()
    private var allExams: List<ExamDto> = emptyList()
    private var allRevisionHistory: List<RevisionHistoryDto> = emptyList()

    private val displayDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

    init {
        if (!SupabaseClientProvider.isConfigured || SupabaseClientProvider.client == null) {
            _errorMessage.value = "Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence."
        } else {
            refreshData()
        }
    }

    private fun getUserId(): String? {
        val client = SupabaseClientProvider.client ?: return null
        return client.auth.currentUserOrNull()?.id
    }

    override fun clearData() {
        loadRequestToken++
        _subjects.value = emptyList()
        _upcomingExams.value = emptyList()
        _pastExams.value = emptyList()
        _recentRevisions.value = emptyList()
        _priorityStudyItems.value = emptyList()
        _studyRecommendations.value = emptyList()
        _allStudyRecommendations.value = emptyList()
        _todayStudyPlan.value = emptyList()
        allUnits = emptyList()
        allTopics = emptyList()
        allQuestions = emptyList()
        allNotes = emptyList()
        allMaterials = emptyList()
        allExams = emptyList()
        allRevisionHistory = emptyList()
        _overallProgress.value = defaultProgress
        _isLoading.value = false
        _errorMessage.value = null
    }

    fun refreshData() {
        scope.launch {
            loadAllData()
        }
    }

    suspend fun loadAllData() = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
        if (client == null) {
            clearData()
            val msg = "Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence."
            Log.w(TAG, msg)
            _errorMessage.value = msg
            return@withContext
        }
        val startingUserId = client.auth.currentUserOrNull()?.id
        if (startingUserId == null) {
            clearData()
            Log.i(TAG, "No authenticated user. Cleared cached repository state.")
            return@withContext
        }

        // Clear prior user data immediately so it is NOT exposed while loading
        clearData()
        val requestToken = loadRequestToken
        if (getUserId() != startingUserId) return@withContext
        _isLoading.value = true
        _errorMessage.value = null

        try {
            // 1. Fetch subjects
            val subjectDtos: List<SubjectDto> = client.from("subjects")
                .select {
                    filter { eq("user_id", startingUserId) }
                }
                .decodeList()

            if (requestToken != loadRequestToken || getUserId() != startingUserId) {
                Log.i(TAG, "Stale loadAllData request discarded after fetching subjects.")
                return@withContext
            }

            // 2. Fetch units
            val unitDtos: List<UnitDto> = client.from("units")
                .select {
                    filter { eq("user_id", startingUserId) }
                }
                .decodeList()

            if (requestToken != loadRequestToken || getUserId() != startingUserId) return@withContext
            allUnits = unitDtos

            // 3. Fetch topics
            val topicDtos: List<TopicDto> = client.from("topics")
                .select {
                    filter { eq("user_id", startingUserId) }
                }
                .decodeList()

            if (requestToken != loadRequestToken || getUserId() != startingUserId) return@withContext
            allTopics = topicDtos

            // 4. Fetch questions
            val questionDtos: List<QuestionDto> = client.from("questions")
                .select {
                    filter { eq("user_id", startingUserId) }
                }
                .decodeList()

            if (requestToken != loadRequestToken || getUserId() != startingUserId) return@withContext
            allQuestions = questionDtos

            // 5. Fetch notes
            val noteDtos: List<NoteDto> = client.from("notes")
                .select {
                    filter { eq("user_id", startingUserId) }
                }
                .decodeList()

            if (requestToken != loadRequestToken || getUserId() != startingUserId) return@withContext
            allNotes = noteDtos

            // 6. Fetch exams
            val examDtos: List<ExamDto> = client.from("exams")
                .select {
                    filter { eq("user_id", startingUserId) }
                }
                .decodeList()

            if (requestToken != loadRequestToken || getUserId() != startingUserId) return@withContext
            allExams = examDtos

            // 6b. Fetch revision history (Phase 6 revision tracking).
            // A failed query (e.g. 06_study_planning.sql not applied) is surfaced as an error and
            // is NOT treated as a confirmed empty history; a successful empty list means the user
            // genuinely has no revisions yet.
            val revisionLoad = RevisionHistoryLoader.load {
                client.from("revision_history")
                    .select {
                        filter { eq("user_id", startingUserId) }
                    }
                    .decodeList()
            }
            val revisionDtos: List<RevisionHistoryDto> = revisionLoad.getOrElse { emptyList() }
            if (revisionLoad.isFailure) {
                Log.w(
                    TAG,
                    "revision_history load failed (ensure 06_study_planning.sql is applied): " +
                        (revisionLoad.exceptionOrNull()?.message ?: "unknown error")
                )
            }

            // 7. Fetch study materials (Phase 4 Storage Integration)
            val materialDtos: List<StudyMaterialDto> = try {
                client.from("study_materials")
                    .select {
                        filter { eq("user_id", startingUserId) }
                    }
                    .decodeList()
            } catch (e: Exception) {
                Log.w(TAG, "Notice: study_materials table query exception (ensure 04_study_materials.sql is applied)", e)
                emptyList()
            }

            if (requestToken != loadRequestToken || getUserId() != startingUserId) {
                Log.i(TAG, "Stale loadAllData request discarded before publishing.")
                return@withContext
            }
            allMaterials = materialDtos
            allRevisionHistory = revisionDtos

            // Construct Domain Subjects
            val domainSubjects = assembleSubjects(
                subjectDtos, unitDtos, topicDtos, questionDtos, noteDtos, materialDtos, revisionDtos
            )
            _subjects.value = domainSubjects

            // Construct Domain Exams (upcoming + past) with real subject preparation
            publishExams(assembleExamItems(examDtos, domainSubjects))

            // Construct revision timeline + priority study items from real data
            val revisionEvents = buildRevisionEvents(revisionDtos, domainSubjects)
            _recentRevisions.value = StudyPlanning.recentRevisions(revisionEvents)
            _priorityStudyItems.value = StudyPlanning.buildPriorityStudyItems(
                subjects = domainSubjects,
                revisionEventsByTopic = revisionEvents.groupBy { it.topicId }
            )
            recomputeRecommendations(domainSubjects)

            // Calculate overall progress from real topic data
            val topicCounts = StudyPlanning.topicCounts(StudyPlanning.allTopics(domainSubjects))
            val overallPct = StudyPlanning.subjectsPreparationPercent(domainSubjects)

            val activeSemester = subjectDtos.firstOrNull()?.semester?.let { formatSemesterLabel(it) }
                ?: _overallProgress.value.semester

            _overallProgress.update { current ->
                current.copy(
                    overallPercentage = overallPct,
                    subjectsCount = domainSubjects.size,
                    upcomingExamsCount = _upcomingExams.value.size,
                    semester = activeSemester,
                    totalTopics = topicCounts.total,
                    completedTopics = topicCounts.completed,
                    inProgressTopics = topicCounts.inProgress,
                    topicsNeedingRevision = topicCounts.needsRevision
                )
            }

            // A revision-history failure degrades only the revision sections. The rest of the
            // dashboard (subjects, topics, exams, progress) stays available, and the failure is
            // surfaced as a distinct, non-fatal error state rather than a silent empty history.
            if (revisionLoad.isFailure) {
                _errorMessage.value = "Revision history could not be loaded: " +
                    "${revisionLoad.exceptionOrNull()?.message ?: "unknown error"}. " +
                    "Other study data is unaffected."
            }

            Log.i(TAG, "Successfully loaded data from Supabase: ${domainSubjects.size} subjects, ${_upcomingExams.value.size} upcoming exams, ${revisionEvents.size} revisions.")
        } catch (e: Exception) {
            if (requestToken == loadRequestToken && getUserId() == startingUserId) {
                Log.e(TAG, "Error loading data from Supabase", e)
                clearData()
                val detail = e.message ?: e::class.java.simpleName
                _errorMessage.value = "Failed to load study data from the cloud: $detail. " +
                    "Dashboard sections may appear empty until a reload succeeds; " +
                    "restart the app to retry the load."
            }
        } finally {
            if (requestToken == loadRequestToken) {
                _isLoading.value = false
            }
        }
    }

    private fun dtoToMaterialItem(dto: StudyMaterialDto): MaterialItem {
        val sizeFormatted = formatByteSize(dto.fileSizeBytes)
        val fileType = extractFileType(dto.originalFilename, dto.mimeType)
        val uploadDateFormatted = formatDisplayDate(dto.createdAt, "Recent")

        return MaterialItem(
            id = dto.id ?: UUID.randomUUID().toString(),
            title = dto.title,
            fileType = fileType,
            fileSize = sizeFormatted,
            uploadDate = uploadDateFormatted,
            storagePath = dto.storagePath,
            mimeType = dto.mimeType,
            fileSizeBytes = dto.fileSizeBytes,
            subjectId = dto.subjectId,
            unitId = dto.unitId,
            topicId = dto.topicId
        )
    }

    private fun assembleSubjects(
        subjectDtos: List<SubjectDto>,
        unitDtos: List<UnitDto>,
        topicDtos: List<TopicDto>,
        questionDtos: List<QuestionDto>,
        noteDtos: List<NoteDto>,
        materialDtos: List<StudyMaterialDto> = emptyList(),
        revisionDtos: List<RevisionHistoryDto> = emptyList()
    ): List<Subject> {
        val domainMaterials = materialDtos.map { dtoToMaterialItem(it) }
        val revisionsByTopic = revisionDtos.groupBy { it.topicId }

        return subjectDtos.map { sDto ->
            val sId = sDto.id ?: ""
            val materialsForSubject = domainMaterials.filter { it.subjectId == sId }
            val unitsForSubject = unitDtos
                .filter { it.subjectId == sId }
                .sortedBy { it.orderIndex }

            val domainUnits = unitsForSubject.map { uDto ->
                val uId = uDto.id ?: ""
                val materialsForUnit = materialsForSubject.filter { it.unitId == uId }
                val topicsForUnit = topicDtos.filter { it.unitId == uId }
                val completedInUnit = topicsForUnit.count { it.status.equals("COMPLETED", ignoreCase = true) }
                val unitPrepPct = if (topicsForUnit.isNotEmpty()) (completedInUnit * 100) / topicsForUnit.size else 0

                val questionsForUnit = questionDtos.filter { it.unitId == uId }

                val domainTopics = topicsForUnit.map { tDto ->
                    val tId = tDto.id ?: ""
                    val materialsForTopic = materialsForSubject.filter { it.topicId == tId }
                    val questionsForTopic = questionDtos.filter { it.topicId == tId }
                    val notesForTopic = noteDtos.filter { it.topicId == tId }

                    val domainTopicNotes = notesForTopic.map { nDto ->
                        NoteItem(
                            id = nDto.id ?: UUID.randomUUID().toString(),
                            subjectId = sId,
                            topicId = tId,
                            title = nDto.title,
                            content = nDto.content,
                            createdAt = nDto.createdAt,
                            updatedAt = nDto.updatedAt,
                            subjectName = sDto.name,
                            topicName = tDto.name
                        )
                    }

                    TopicModel(
                        id = tId,
                        unitId = uId,
                        subjectId = sId,
                        title = tDto.name,
                        status = parseTopicStatus(tDto.status),
                        difficulty = parseDifficulty(tDto.difficulty),
                        importance = parseImportance(tDto.importance),
                        lastStudiedDate = formatDisplayDate(tDto.lastStudiedAt, "Not studied"),
                        nextRevisionDate = formatDisplayDate(tDto.nextRevisionAt, "None scheduled"),
                        priorityScore = calculatePriorityScore(tDto),
                        materialsCount = materialsForTopic.size,
                        questionsCount = questionsForTopic.size,
                        notesCount = notesForTopic.size,
                        completedAt = tDto.completedAt,
                        revisionCount = maxOf(tDto.revisionCount, revisionsByTopic[tId]?.size ?: 0),
                        lastRevisedAt = revisionsByTopic[tId]
                            ?.mapNotNull { it.studiedAt ?: it.createdAt }
                            ?.maxOrNull()
                            ?: tDto.lastStudiedAt,
                        relatedMaterials = materialsForTopic,
                        questions = questionsForTopic.mapIndexed { idx, qDto ->
                            QuestionItem(
                                id = qDto.id ?: "q_$idx",
                                questionNumber = idx + 1,
                                questionText = qDto.question,
                                marks = qDto.marksOrDefault(),
                                unitName = uDto.name,
                                subjectName = sDto.name,
                                status = mapQuestionStatusToDisplay(qDto.status),
                                subjectId = sId,
                                unitId = uId,
                                topicId = tId,
                                importance = parseImportance(qDto.importance),
                                createdAt = qDto.createdAt,
                                updatedAt = qDto.updatedAt
                            )
                        },
                        notes = domainTopicNotes
                    )
                }

                UnitModel(
                    id = uId,
                    subjectId = sId,
                    unitNumber = uDto.orderIndex,
                    title = uDto.name,
                    preparationPercent = unitPrepPct,
                    topics = domainTopics,
                    materialsCount = materialsForUnit.size,
                    questionsCount = questionsForUnit.size,
                    materials = materialsForUnit
                )
            }

            val totalTopicsInSubject = domainUnits.flatMap { it.topics }
            val completedTopics = totalTopicsInSubject.count { it.status == TopicStatus.COMPLETED }
            val prepPct = if (totalTopicsInSubject.isNotEmpty()) (completedTopics * 100) / totalTopicsInSubject.size else 0

            val needsAttention = totalTopicsInSubject
                .filter { it.status != TopicStatus.COMPLETED && it.importance == Importance.HIGH }
                .take(3)
                .map { it.title }

            val totalQuestionsForSubject = questionDtos.count { it.subjectId == sId }

            val allSubjectQuestions = questionDtos
                .filter { it.subjectId == sId }
                .mapIndexed { idx, qDto ->
                    val unitName = unitDtos.find { it.id == qDto.unitId }?.name ?: "General"
                    QuestionItem(
                        id = qDto.id ?: "sq_$idx",
                        questionNumber = idx + 1,
                        questionText = qDto.question,
                        marks = qDto.marksOrDefault(),
                        unitName = unitName,
                        subjectName = sDto.name,
                        status = mapQuestionStatusToDisplay(qDto.status),
                        subjectId = sId,
                        unitId = qDto.unitId,
                        topicId = qDto.topicId,
                        importance = parseImportance(qDto.importance),
                        createdAt = qDto.createdAt,
                        updatedAt = qDto.updatedAt
                    )
                }

            val allSubjectNotes = noteDtos
                .filter { it.subjectId == sId }
                .map { nDto ->
                    val topicName = topicDtos.find { it.id == nDto.topicId }?.name ?: ""
                    NoteItem(
                        id = nDto.id ?: UUID.randomUUID().toString(),
                        subjectId = sId,
                        topicId = nDto.topicId,
                        title = nDto.title,
                        content = nDto.content,
                        createdAt = nDto.createdAt,
                        updatedAt = nDto.updatedAt,
                        subjectName = sDto.name,
                        topicName = topicName
                    )
                }

            val semInt = sDto.semester ?: parseSemesterNumber(_overallProgress.value.semester)
            val semLabel = formatSemesterLabel(semInt)

            Subject(
                id = sId,
                name = sDto.name,
                code = sDto.description?.takeIf { it.isNotBlank() } ?: "SUB",
                iconKey = sDto.iconKey ?: "book",
                preparationPercent = prepPct,
                unitsCount = domainUnits.size,
                topicsCount = totalTopicsInSubject.size,
                accentColorHex = getAccentColorForIcon(sDto.iconKey ?: "book"),
                units = domainUnits,
                materialsCount = materialsForSubject.size,
                questionsCount = totalQuestionsForSubject,
                needsAttentionTopics = needsAttention,
                semester = semLabel,
                semesterNumber = semInt,
                questions = allSubjectQuestions,
                materials = materialsForSubject,
                notes = allSubjectNotes
            )
        }
    }

    private fun assembleExamItems(
        examDtos: List<ExamDto>,
        subjects: List<Subject>
    ): List<ExamItem> {
        val subjectMap = subjects.associateBy { it.id }
        return examDtos.map { eDto ->
            val subject = subjectMap[eDto.subjectId]
            val isoDate = StudyDateUtils.isoDatePortion(eDto.examDate) ?: ""
            ExamItem(
                id = eDto.id ?: "",
                subjectId = eDto.subjectId,
                subjectName = subject?.name ?: "Exam",
                iconKey = subject?.iconKey ?: "book",
                examDate = formatDisplayDate(eDto.examDate, eDto.examDate),
                daysRemaining = StudyDateUtils.daysFromToday(isoDate) ?: 0,
                preparationPercent = subject?.preparationPercent ?: 0,
                code = subject?.code ?: "EXAM",
                venue = eDto.venue?.takeIf { it.isNotBlank() } ?: "Venue not set",
                name = eDto.name,
                description = eDto.description,
                isoDate = isoDate
            )
        }
    }

    private fun publishExams(exams: List<ExamItem>) {
        val schedule = StudyPlanning.classifyExams(exams)
        _upcomingExams.value = schedule.upcoming
        _pastExams.value = schedule.past
    }

    private fun buildRevisionEvents(
        revisionDtos: List<RevisionHistoryDto>,
        subjects: List<Subject>
    ): List<RevisionEvent> {
        return revisionDtos.mapNotNull { dto ->
            val subject = subjects.find { s -> s.units.any { u -> u.topics.any { t -> t.id == dto.topicId } } }
                ?: return@mapNotNull null
            val unit = subject.units.find { it.topics.any { t -> t.id == dto.topicId } }
                ?: return@mapNotNull null
            val topic = unit.topics.find { it.id == dto.topicId }
                ?: return@mapNotNull null

            val revisedAt = dto.studiedAt ?: dto.createdAt ?: ""
            RevisionEvent(
                id = dto.id ?: UUID.randomUUID().toString(),
                topicId = dto.topicId,
                subjectId = subject.id,
                unitId = unit.id,
                topicName = topic.title,
                subjectName = subject.name,
                unitName = unit.title,
                revisionNumber = dto.revisionNumber,
                previousStatus = dto.previousStatus?.let { parseTopicStatus(it) },
                newStatus = parseTopicStatus(dto.newStatus),
                revisedAt = revisedAt,
                revisedAtDisplay = formatDisplayDate(revisedAt, "Recently"),
                notes = dto.notes
            )
        }
    }

    // --- StudyVaultRepository Interface Implementation ---

    override fun getTodayStudyItems(): List<StudyItem> =
        _todayStudyPlan.value.filter { !it.isQuestion }.map { StudyPlanning.recommendationToStudyItem(it) }

    override fun setDailyStudyMinutesTarget(minutes: Int?) {
        dailyMinutesTarget = minutes?.takeIf { it > 0 }
        recomputeRecommendations()
    }

    override fun getRecentlyAddedMaterials(): List<MaterialItem> {
        return allMaterials.map { dtoToMaterialItem(it) }.take(5)
    }

    override fun getSubjectById(subjectId: String): Subject? {
        return _subjects.value.find {
            it.id == subjectId || it.name.equals(subjectId, ignoreCase = true)
        }
    }

    override fun getUnitById(subjectId: String, unitId: String): UnitModel? {
        val subject = getSubjectById(subjectId) ?: return null
        return subject.units.find {
            it.id == unitId || it.unitNumber.toString() == unitId
        }
    }

    override fun getTopicById(topicId: String): TopicModel? {
        for (subject in _subjects.value) {
            for (unit in subject.units) {
                val topic = unit.topics.find { it.id == topicId }
                if (topic != null) return topic
            }
        }
        return null
    }

    override suspend fun updateTopicStatus(topicId: String, status: TopicStatus): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.client
                ?: return@withContext Result.failure(
                    IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
                )
            val userId = getUserId()
                ?: return@withContext Result.failure(IllegalStateException("Cannot update topic status: User is not authenticated."))

            getTopicById(topicId)
                ?: return@withContext Result.failure(NoSuchElementException("Topic not found."))

            topicMutex.withLock {
                val nowStr = StudyDateUtils.nowIsoTimestamp()
                val statusStr = status.name
                val completedAt = if (status == TopicStatus.COMPLETED) nowStr else null
                try {
                    client.from("topics").update(
                        {
                            set("status", statusStr)
                            set("last_studied_at", nowStr)
                            set("completed_at", completedAt)
                            set("updated_at", nowStr)
                        }
                    ) {
                        filter {
                            eq("id", topicId)
                            eq("user_id", userId)
                        }
                    }

                    if (getUserId() != userId) {
                        return@withLock Result.failure(StaleRequestException("Topic update discarded: session changed."))
                    }

                    allTopics = allTopics.map {
                        if (it.id == topicId) it.copy(status = statusStr, lastStudiedAt = nowStr, completedAt = completedAt) else it
                    }
                    applyLocalTopicState(topicId, status, nowStr, completedAt)
                    _errorMessage.value = null
                    Log.i(TAG, "Successfully updated topic $topicId status to $status")
                    Result.success(Unit)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist topic status update to Supabase", e)
                    _errorMessage.value = "Failed to update topic: ${e.message}"
                    Result.failure(e)
                }
            }
        }

    override suspend fun markTopicRevised(topicId: String, notes: String?): Result<RevisionEvent> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.client
                ?: return@withContext Result.failure(
                    IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
                )
            val userId = getUserId()
                ?: return@withContext Result.failure(IllegalStateException("Cannot record revision: User is not authenticated."))

            val existing = getTopicById(topicId)
                ?: return@withContext Result.failure(NoSuchElementException("Topic not found."))
            val subject = getSubjectById(existing.subjectId)
            val unit = subject?.units?.find { it.id == existing.unitId }

            topicMutex.withLock {
                val nowStr = StudyDateUtils.nowIsoTimestamp()
                val nextStatus = if (existing.status == TopicStatus.NEEDS_REVISION) TopicStatus.COMPLETED else existing.status
                val existingCompletedAt = existing.completedAt
                val newCompletedAt = if (nextStatus == TopicStatus.COMPLETED) (existingCompletedAt ?: nowStr) else null
                val trimmedNotes = notes?.trim()?.takeIf { it.isNotEmpty() }
                try {
                    val historyList: List<RevisionHistoryDto> = client.from("revision_history")
                        .select {
                            filter {
                                eq("topic_id", topicId)
                                eq("user_id", userId)
                            }
                        }
                        .decodeList()

                    val nextRevisionNumber = maxOf(existing.revisionCount, historyList.maxOfOrNull { it.revisionNumber } ?: 0) + 1

                    val dto = RevisionHistoryDto(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        topicId = topicId,
                        studiedAt = nowStr,
                        revisionNumber = nextRevisionNumber,
                        previousStatus = existing.status.name,
                        newStatus = nextStatus.name,
                        notes = trimmedNotes,
                        createdAt = nowStr
                    )
                    client.from("revision_history").insert(dto)

                    client.from("topics").update(
                        {
                            set("revision_count", nextRevisionNumber)
                            set("status", nextStatus.name)
                            set("last_studied_at", nowStr)
                            set("completed_at", newCompletedAt)
                            set("updated_at", nowStr)
                        }
                    ) {
                        filter {
                            eq("id", topicId)
                            eq("user_id", userId)
                        }
                    }

                    if (getUserId() != userId) {
                        return@withLock Result.failure(StaleRequestException("Revision discarded: session changed."))
                    }

                    allRevisionHistory = allRevisionHistory + dto
                    allTopics = allTopics.map {
                        if (it.id == topicId) {
                            it.copy(
                                status = nextStatus.name,
                                revisionCount = nextRevisionNumber,
                                lastStudiedAt = nowStr,
                                completedAt = newCompletedAt
                            )
                        } else it
                    }

                    val event = RevisionEvent(
                        id = dto.id ?: UUID.randomUUID().toString(),
                        topicId = topicId,
                        subjectId = existing.subjectId,
                        unitId = existing.unitId,
                        topicName = existing.title,
                        subjectName = subject?.name ?: "",
                        unitName = unit?.title ?: "",
                        revisionNumber = nextRevisionNumber,
                        previousStatus = existing.status,
                        newStatus = nextStatus,
                        revisedAt = nowStr,
                        revisedAtDisplay = StudyDateUtils.toDisplayDate(nowStr),
                        notes = trimmedNotes
                    )

                    applyLocalTopicState(
                        topicId = topicId,
                        status = nextStatus,
                        lastStudiedIso = nowStr,
                        completedAt = newCompletedAt,
                        revisionCount = nextRevisionNumber,
                        lastRevisedIso = nowStr
                    )
                    rebuildRevisionSummaries()
                    _errorMessage.value = null
                    Log.i(TAG, "Recorded revision #$nextRevisionNumber for topic $topicId")
                    Result.success(event)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to record revision for topic $topicId", e)
                    _errorMessage.value = "Failed to record revision: ${e.message}"
                    Result.failure(e)
                }
            }
        }

    override fun getRevisionEventsForTopic(topicId: String): List<RevisionEvent> {
        val subjects = _subjects.value
        return StudyPlanning.sortRevisionEventsNewestFirst(
            allRevisionHistory
                .filter { it.topicId == topicId }
                .mapNotNull { dto -> buildRevisionEvents(listOf(dto), subjects).firstOrNull() }
        )
    }

    private fun applyLocalTopicState(
        topicId: String,
        status: TopicStatus,
        lastStudiedIso: String,
        completedAt: String?,
        revisionCount: Int? = null,
        lastRevisedIso: String? = null
    ) {
        val displayDate = formatDisplayDate(lastStudiedIso, "Just now")
        _subjects.update { currentSubjects ->
            currentSubjects.map { subject ->
                val updatedUnits = subject.units.map { unit ->
                    val updatedTopics = unit.topics.map { topic ->
                        if (topic.id == topicId) {
                            topic.copy(
                                status = status,
                                lastStudiedDate = displayDate,
                                completedAt = completedAt,
                                revisionCount = revisionCount ?: topic.revisionCount,
                                lastRevisedAt = lastRevisedIso ?: topic.lastRevisedAt
                            )
                        } else topic
                    }
                    val unitPercent = if (updatedTopics.isEmpty()) unit.preparationPercent else StudyPlanning.preparationPercent(updatedTopics)
                    unit.copy(topics = updatedTopics, preparationPercent = unitPercent)
                }
                val trackedTopics = updatedUnits.flatMap { it.topics }
                if (trackedTopics.isEmpty()) {
                    subject.copy(units = updatedUnits)
                } else {
                    subject.copy(
                        units = updatedUnits,
                        preparationPercent = StudyPlanning.preparationPercent(trackedTopics)
                    )
                }
            }
        }

        _priorityStudyItems.value = StudyPlanning.buildPriorityStudyItems(
            subjects = _subjects.value,
            revisionEventsByTopic = currentRevisionEvents().groupBy { it.topicId }
        )
        recalculateOverallProgress()
        recomputeRecommendations()
    }

    private fun currentRevisionEvents(): List<RevisionEvent> =
        buildRevisionEvents(allRevisionHistory, _subjects.value)

    private fun rebuildRevisionSummaries() {
        val events = currentRevisionEvents()
        _recentRevisions.value = StudyPlanning.recentRevisions(events)
        _priorityStudyItems.value = StudyPlanning.buildPriorityStudyItems(
            subjects = _subjects.value,
            revisionEventsByTopic = events.groupBy { it.topicId }
        )
        recomputeRecommendations()
    }

    private fun recomputeRecommendations(subjects: List<Subject> = _subjects.value) {
        val todayIso = StudyDateUtils.todayIso()
        val recommendations = StudyPriorityEngine.buildRecommendations(
            subjects = subjects,
            upcomingExams = _upcomingExams.value,
            revisionEventsByTopic = currentRevisionEvents().groupBy { it.topicId },
            todayIso = todayIso
        )
        _studyRecommendations.value = recommendations
        val questionRecommendations = StudyPriorityEngine.buildQuestionRecommendations(
            subjects = subjects,
            upcomingExams = _upcomingExams.value,
            todayIso = todayIso
        )
        val allRecommendations = (recommendations + questionRecommendations)
            .sortedWith(StudyPriorityEngine.recommendationOrder())
        _allStudyRecommendations.value = allRecommendations
        _todayStudyPlan.value = StudyPriorityEngine.selectTodayPlan(
            recommendations = allRecommendations,
            config = TodayPlanConfig(dailyMinutesTarget = dailyMinutesTarget)
        )
    }

    private fun recalculateOverallProgress() {
        val subjects = _subjects.value
        val counts = StudyPlanning.topicCounts(StudyPlanning.allTopics(subjects))
        _overallProgress.update {
            it.copy(
                overallPercentage = StudyPlanning.subjectsPreparationPercent(subjects),
                subjectsCount = subjects.size,
                upcomingExamsCount = _upcomingExams.value.size,
                totalTopics = counts.total,
                completedTopics = counts.completed,
                inProgressTopics = counts.inProgress,
                topicsNeedingRevision = counts.needsRevision
            )
        }
    }

    override fun addSubject(subjectName: String, code: String, iconKey: String) {
        val client = SupabaseClientProvider.client
        val userId = getUserId()
        if (client == null || userId == null) {
            val err = if (client == null) {
                "Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence."
            } else {
                "Cannot add subject: User is not authenticated."
            }
            Log.e(TAG, err)
            _errorMessage.value = err
            return
        }

        val newId = UUID.randomUUID().toString()
        val resolvedIcon = if (iconKey.isNotBlank()) iconKey else "book"
        val semInt = parseSemesterNumber(_overallProgress.value.semester)
        val semLabel = formatSemesterLabel(semInt)

        val newSubject = Subject(
            id = newId,
            name = subjectName,
            code = code.ifBlank { "SUB" },
            iconKey = resolvedIcon,
            preparationPercent = 0,
            unitsCount = 0,
            topicsCount = 0,
            accentColorHex = getAccentColorForIcon(resolvedIcon),
            units = emptyList(),
            materialsCount = 0,
            questionsCount = 0,
            semester = semLabel,
            semesterNumber = semInt,
            questions = emptyList()
        )

        // Optimistically update
        _subjects.update { it + newSubject }
        _overallProgress.update { it.copy(subjectsCount = it.subjectsCount + 1) }

        scope.launch {
            try {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val dto = SubjectDto(
                    id = newId,
                    userId = userId,
                    name = subjectName,
                    description = code,
                    semester = semInt, // Stored as integer in PostgreSQL subjects table
                    iconKey = resolvedIcon,
                    createdAt = now,
                    updatedAt = now
                )
                client.from("subjects").insert(dto)
                _errorMessage.value = null
                Log.i(TAG, "Successfully inserted subject $subjectName to Supabase.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert subject to Supabase", e)
                // Revert optimistic update so no phantom record remains
                _subjects.update { currentList -> currentList.filter { it.id != newId } }
                _overallProgress.update { it.copy(subjectsCount = (it.subjectsCount - 1).coerceAtLeast(0)) }
                _errorMessage.value = "Failed to add subject: ${e.message}"
            }
        }
    }

    /**
     * Rebuilds domain subjects from the cached raw DTOs after a mutation. The subject DTO list is
     * reconstructed from the currently published subjects so name/code/icon edits are preserved.
     */
    private fun reassembleSubjectsFromCache(): List<Subject> {
        val userId = getUserId() ?: return _subjects.value
        val domainSubjects = assembleSubjects(
            subjectDtos = _subjects.value.map { s ->
                SubjectDto(
                    id = s.id,
                    userId = userId,
                    name = s.name,
                    description = s.code,
                    semester = s.semesterNumber,
                    iconKey = s.iconKey
                )
            },
            unitDtos = allUnits,
            topicDtos = allTopics,
            questionDtos = allQuestions,
            noteDtos = allNotes,
            materialDtos = allMaterials,
            revisionDtos = allRevisionHistory
        )
        _subjects.value = domainSubjects
        recomputeRecommendations(domainSubjects)
        return domainSubjects
    }

    override suspend fun updateSubject(
        subjectId: String,
        subjectName: String,
        code: String,
        iconKey: String
    ): Result<Subject> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
            )
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("Cannot update subject: User is not authenticated."))

        val existing = _subjects.value.find { it.id == subjectId }
            ?: return@withContext Result.failure(NoSuchElementException("Subject not found."))
        val trimmedName = subjectName.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Subject name cannot be blank."))
        }
        val resolvedIcon = if (iconKey.isNotBlank()) iconKey else existing.iconKey
        val resolvedCode = code.trim().ifBlank { existing.code }

        try {
            val nowStr = StudyDateUtils.nowIsoTimestamp()
            client.from("subjects").update(
                {
                    set("name", trimmedName)
                    set("description", resolvedCode)
                    set("icon_key", resolvedIcon)
                    set("updated_at", nowStr)
                }
            ) {
                filter {
                    eq("id", subjectId)
                    eq("user_id", userId)
                }
            }

            if (getUserId() != userId) {
                return@withContext Result.failure(StaleRequestException("Subject update discarded: session changed."))
            }

            val domainSubjects = reassembleSubjectsFromCache()
            publishExams(assembleExamItems(allExams, domainSubjects))
            recalculateOverallProgress()
            _errorMessage.value = null
            Log.i(TAG, "Successfully updated subject $subjectId")
            domainSubjects.find { it.id == subjectId }?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Subject was updated but could not be read back."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update subject $subjectId", e)
            _errorMessage.value = "Failed to update subject: ${e.message}"
            Result.failure(e)
        }
    }

    override suspend fun deleteSubject(subjectId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
            )
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("Cannot delete subject: User is not authenticated."))

        _subjects.value.find { it.id == subjectId }
            ?: return@withContext Result.failure(NoSuchElementException("Subject not found."))

        val unitIds = allUnits.filter { it.subjectId == subjectId }.mapNotNull { it.id }
        val topicIds = allTopics.filter { it.unitId in unitIds }.mapNotNull { it.id }
        val materialPaths = allMaterials
            .filter { it.subjectId == subjectId }
            .map { it.storagePath }
            .filter { it.isNotBlank() }

        try {
            // Explicit ordered deletes scoped to this subject and user. Notes, questions, revision
            // history, topics, units, exams, and materials are deleted here so the operation does
            // not depend on unverified ON DELETE rules; the subject row goes last.
            client.from("notes").delete {
                filter {
                    eq("user_id", userId)
                    eq("subject_id", subjectId)
                }
            }
            client.from("questions").delete {
                filter {
                    eq("user_id", userId)
                    eq("subject_id", subjectId)
                }
            }
            for (topicId in topicIds) {
                client.from("revision_history").delete {
                    filter {
                        eq("user_id", userId)
                        eq("topic_id", topicId)
                    }
                }
            }
            for (unitId in unitIds) {
                client.from("topics").delete {
                    filter {
                        eq("user_id", userId)
                        eq("unit_id", unitId)
                    }
                }
            }
            client.from("units").delete {
                filter {
                    eq("user_id", userId)
                    eq("subject_id", subjectId)
                }
            }
            client.from("exams").delete {
                filter {
                    eq("user_id", userId)
                    eq("subject_id", subjectId)
                }
            }
            client.from("study_materials").delete {
                filter {
                    eq("user_id", userId)
                    eq("subject_id", subjectId)
                }
            }
            client.from("subjects").delete {
                filter {
                    eq("id", subjectId)
                    eq("user_id", userId)
                }
            }

            if (getUserId() != userId) {
                return@withContext Result.failure(StaleRequestException("Subject deletion discarded: session changed."))
            }

            allUnits = allUnits.filter { it.subjectId != subjectId }
            allTopics = allTopics.filterNot { it.unitId in unitIds }
            allQuestions = allQuestions.filter { it.subjectId != subjectId }
            allNotes = allNotes.filter { it.subjectId != subjectId }
            allExams = allExams.filter { it.subjectId != subjectId }
            allMaterials = allMaterials.filter { it.subjectId != subjectId }
            allRevisionHistory = allRevisionHistory.filterNot { it.topicId in topicIds }

            _subjects.value = _subjects.value.filterNot { it.id == subjectId }
            publishExams(assembleExamItems(allExams, _subjects.value))
            recalculateOverallProgress()
            recomputeRecommendations()
            _errorMessage.value = null
            Log.i(TAG, "Successfully deleted subject $subjectId")

            if (materialPaths.isNotEmpty()) {
                try {
                    client.storage.from(SupabaseStudyMaterialRepository.BUCKET_NAME).delete(materialPaths)
                } catch (storageError: Exception) {
                    Log.w(TAG, "Subject rows deleted, but some storage files could not be removed", storageError)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete subject $subjectId", e)
            _errorMessage.value = "Failed to delete subject: ${e.message}"
            Result.failure(e)
        }
    }

    override suspend fun createUnit(subjectId: String, name: String, orderIndex: Int?): Result<UnitModel> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.client
                ?: return@withContext Result.failure(
                    IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
                )
            val userId = getUserId()
                ?: return@withContext Result.failure(IllegalStateException("Cannot create unit: User is not authenticated."))

            val subject = _subjects.value.find { it.id == subjectId }
                ?: return@withContext Result.failure(NoSuchElementException("Subject not found."))
            val trimmed = name.trim()
            if (trimmed.isBlank()) {
                return@withContext Result.failure(IllegalArgumentException("Unit name cannot be blank."))
            }

            try {
                val newId = UUID.randomUUID().toString()
                val nowStr = StudyDateUtils.nowIsoTimestamp()
                val maxOrder = allUnits
                    .filter { it.subjectId == subject.id }
                    .map { it.orderIndex }
                    .maxOrNull() ?: 0
                val dto = UnitDto(
                    id = newId,
                    userId = userId,
                    subjectId = subject.id,
                    name = trimmed,
                    orderIndex = orderIndex ?: (maxOrder + 1),
                    createdAt = nowStr,
                    updatedAt = nowStr
                )
                client.from("units").insert(dto)

                if (getUserId() != userId) {
                    return@withContext Result.failure(StaleRequestException("Unit creation discarded: session changed."))
                }

                allUnits = allUnits + dto
                val domainSubjects = reassembleSubjectsFromCache()
                publishExams(assembleExamItems(allExams, domainSubjects))
                recalculateOverallProgress()
                _errorMessage.value = null
                Log.i(TAG, "Successfully created unit '$trimmed' ($newId) for subject ${subject.id}")
                getUnitById(subject.id, newId)?.let { Result.success(it) }
                    ?: Result.failure(IllegalStateException("Unit was saved but could not be read back."))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create unit for subject $subjectId", e)
                _errorMessage.value = "Failed to create unit: ${e.message}"
                Result.failure(e)
            }
        }

    override suspend fun updateUnit(
        subjectId: String,
        unitId: String,
        name: String,
        orderIndex: Int?
    ): Result<UnitModel> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
            )
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("Cannot update unit: User is not authenticated."))

        allUnits.find { it.id == unitId && it.subjectId == subjectId }
            ?: return@withContext Result.failure(NoSuchElementException("Unit not found."))
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Unit name cannot be blank."))
        }

        try {
            val nowStr = StudyDateUtils.nowIsoTimestamp()
            client.from("units").update(
                {
                    set("name", trimmed)
                    orderIndex?.let { set("order_index", it) }
                    set("updated_at", nowStr)
                }
            ) {
                filter {
                    eq("id", unitId)
                    eq("user_id", userId)
                }
            }

            if (getUserId() != userId) {
                return@withContext Result.failure(StaleRequestException("Unit update discarded: session changed."))
            }

            allUnits = allUnits.map {
                if (it.id == unitId) {
                    it.copy(
                        name = trimmed,
                        orderIndex = orderIndex ?: it.orderIndex,
                        updatedAt = nowStr
                    )
                } else it
            }
            val domainSubjects = reassembleSubjectsFromCache()
            publishExams(assembleExamItems(allExams, domainSubjects))
            recalculateOverallProgress()
            _errorMessage.value = null
            Log.i(TAG, "Successfully updated unit $unitId")
            getUnitById(subjectId, unitId)?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Unit was updated but could not be read back."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update unit $unitId", e)
            _errorMessage.value = "Failed to update unit: ${e.message}"
            Result.failure(e)
        }
    }

    override suspend fun deleteUnit(subjectId: String, unitId: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.client
                ?: return@withContext Result.failure(
                    IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
                )
            val userId = getUserId()
                ?: return@withContext Result.failure(IllegalStateException("Cannot delete unit: User is not authenticated."))

            allUnits.find { it.id == unitId && it.subjectId == subjectId }
                ?: return@withContext Result.failure(NoSuchElementException("Unit not found."))

            val topicCount = allTopics.count { it.unitId == unitId }
            val questionCount = allQuestions.count { it.unitId == unitId }
            if (topicCount > 0 || questionCount > 0) {
                val parts = buildList {
                    if (topicCount > 0) add("$topicCount topic(s)")
                    if (questionCount > 0) add("$questionCount question(s)")
                }
                return@withContext Result.failure(
                    IllegalStateException(
                        "This unit still has ${parts.joinToString(" and ")}. " +
                            "Reassign or delete them before deleting the unit."
                    )
                )
            }

            try {
                // study_materials.unit_id is set to NULL by the schema's ON DELETE SET NULL rule,
                // so linked materials survive the unit deletion with a cleared unit link.
                client.from("units").delete {
                    filter {
                        eq("id", unitId)
                        eq("user_id", userId)
                    }
                }

                if (getUserId() != userId) {
                    return@withContext Result.failure(StaleRequestException("Unit deletion discarded: session changed."))
                }

                allUnits = allUnits.filterNot { it.id == unitId }
                allMaterials = allMaterials.map {
                    if (it.unitId == unitId) it.copy(unitId = null) else it
                }
                val domainSubjects = reassembleSubjectsFromCache()
                publishExams(assembleExamItems(allExams, domainSubjects))
                recalculateOverallProgress()
                _errorMessage.value = null
                Log.i(TAG, "Successfully deleted unit $unitId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete unit $unitId", e)
                _errorMessage.value = "Failed to delete unit: ${e.message}"
                Result.failure(e)
            }
        }

    override suspend fun createTopic(
        subjectId: String,
        unitId: String,
        title: String
    ): Result<TopicModel> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
            )
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("Cannot create topic: User is not authenticated."))

        _subjects.value.find { it.id == subjectId }
            ?: return@withContext Result.failure(NoSuchElementException("Subject not found."))
        allUnits.find { it.id == unitId && it.subjectId == subjectId }
            ?: return@withContext Result.failure(NoSuchElementException("Unit not found."))
        val trimmed = title.trim()
        if (trimmed.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Topic title cannot be blank."))
        }

        try {
            val newId = UUID.randomUUID().toString()
            val nowStr = StudyDateUtils.nowIsoTimestamp()
            val dto = TopicDto(
                id = newId,
                userId = userId,
                unitId = unitId,
                name = trimmed,
                status = TopicStatus.NOT_STARTED.name,
                importance = "MEDIUM",
                difficulty = "MEDIUM",
                createdAt = nowStr,
                updatedAt = nowStr
            )
            client.from("topics").insert(dto)

            if (getUserId() != userId) {
                return@withContext Result.failure(StaleRequestException("Topic creation discarded: session changed."))
            }

            allTopics = allTopics + dto
            val domainSubjects = reassembleSubjectsFromCache()
            publishExams(assembleExamItems(allExams, domainSubjects))
            recalculateOverallProgress()
            _errorMessage.value = null
            Log.i(TAG, "Successfully created topic '$trimmed' ($newId) for unit $unitId")
            getTopicById(newId)?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Topic was saved but could not be read back."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create topic for unit $unitId", e)
            _errorMessage.value = "Failed to create topic: ${e.message}"
            Result.failure(e)
        }
    }

    override fun getExamById(examId: String): ExamItem? =
        (_upcomingExams.value + _pastExams.value).find { it.id == examId }

    override suspend fun createExam(
        subjectId: String,
        name: String,
        examDate: String,
        venue: String,
        description: String?
    ): Result<ExamItem> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
            )
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("Cannot schedule exam: User is not authenticated."))

        val matchedSubject = _subjects.value.find {
            it.id == subjectId ||
                it.name.equals(subjectId, ignoreCase = true) ||
                it.code.equals(subjectId, ignoreCase = true)
        } ?: return@withContext Result.failure(
            IllegalArgumentException("Subject '$subjectId' not found. Please create the subject before scheduling an exam.")
        )

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Exam name cannot be blank."))
        }

        val isoDate = StudyDateUtils.parseExamDateToIso(examDate)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid exam date '$examDate'. Use a format like 'Nov 15, 2026' or '2026-11-15'.")
            )

        val newId = UUID.randomUUID().toString()
        val nowStr = StudyDateUtils.nowIsoTimestamp()
        val trimmedVenue = venue.trim()
        try {
            val dto = ExamDto(
                id = newId,
                userId = userId,
                subjectId = matchedSubject.id,
                name = trimmedName,
                examDate = isoDate,
                venue = trimmedVenue.ifBlank { null },
                description = description?.trim()?.takeIf { it.isNotEmpty() },
                createdAt = nowStr,
                updatedAt = nowStr
            )
            client.from("exams").insert(dto)

            if (getUserId() != userId) {
                return@withContext Result.failure(StaleRequestException("Exam creation discarded: session changed."))
            }

            allExams = allExams + dto
            publishExams(assembleExamItems(allExams, _subjects.value))
            recalculateOverallProgress()
            recomputeRecommendations()
            _errorMessage.value = null
            Log.i(TAG, "Successfully inserted exam '$trimmedName' ($isoDate) to Supabase.")
            getExamById(newId)?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Exam was saved but could not be read back."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to insert exam to Supabase", e)
            _errorMessage.value = "Failed to save exam: ${e.message}"
            Result.failure(e)
        }
    }

    override suspend fun updateExam(
        examId: String,
        subjectId: String,
        name: String,
        examDate: String,
        venue: String,
        description: String?
    ): Result<ExamItem> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
            )
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("Cannot update exam: User is not authenticated."))

        allExams.find { it.id == examId }
            ?: return@withContext Result.failure(NoSuchElementException("Exam not found."))

        val matchedSubject = _subjects.value.find {
            it.id == subjectId ||
                it.name.equals(subjectId, ignoreCase = true) ||
                it.code.equals(subjectId, ignoreCase = true)
        } ?: return@withContext Result.failure(IllegalArgumentException("Subject '$subjectId' not found."))

        val trimmedName = name.trim()
        if (trimmedName.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Exam name cannot be blank."))
        }

        val isoDate = StudyDateUtils.parseExamDateToIso(examDate)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Invalid exam date '$examDate'. Use a format like 'Nov 15, 2026' or '2026-11-15'.")
            )

        val nowStr = StudyDateUtils.nowIsoTimestamp()
        val trimmedVenue = venue.trim()
        try {
            client.from("exams").update(
                {
                    set("subject_id", matchedSubject.id)
                    set("name", trimmedName)
                    set("exam_date", isoDate)
                    set("venue", trimmedVenue.ifBlank { null })
                    set("description", description?.trim()?.takeIf { it.isNotEmpty() })
                    set("updated_at", nowStr)
                }
            ) {
                filter {
                    eq("id", examId)
                    eq("user_id", userId)
                }
            }

            if (getUserId() != userId) {
                return@withContext Result.failure(StaleRequestException("Exam update discarded: session changed."))
            }

            allExams = allExams.map {
                if (it.id == examId) {
                    it.copy(
                        subjectId = matchedSubject.id,
                        name = trimmedName,
                        examDate = isoDate,
                        venue = trimmedVenue.ifBlank { null },
                        description = description?.trim()?.takeIf { v -> v.isNotEmpty() },
                        updatedAt = nowStr
                    )
                } else it
            }
            publishExams(assembleExamItems(allExams, _subjects.value))
            recalculateOverallProgress()
            recomputeRecommendations()
            _errorMessage.value = null
            Log.i(TAG, "Successfully updated exam $examId")
            getExamById(examId)?.let { Result.success(it) }
                ?: Result.failure(IllegalStateException("Exam was updated but could not be read back."))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update exam $examId", e)
            _errorMessage.value = "Failed to update exam: ${e.message}"
            Result.failure(e)
        }
    }

    override suspend fun deleteExam(examId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence.")
            )
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("Cannot delete exam: User is not authenticated."))

        allExams.find { it.id == examId }
            ?: return@withContext Result.failure(NoSuchElementException("Exam not found."))

        try {
            client.from("exams").delete {
                filter {
                    eq("id", examId)
                    eq("user_id", userId)
                }
            }

            if (getUserId() != userId) {
                return@withContext Result.failure(StaleRequestException("Exam deletion discarded: session changed."))
            }

            allExams = allExams.filter { it.id != examId }
            publishExams(assembleExamItems(allExams, _subjects.value))
            recalculateOverallProgress()
            recomputeRecommendations()
            _errorMessage.value = null
            Log.i(TAG, "Successfully deleted exam $examId")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete exam $examId", e)
            _errorMessage.value = "Failed to delete exam: ${e.message}"
            Result.failure(e)
        }
    }

    override fun setAuthenticatedUser(email: String, displayName: String) {
        // Clear old user state before loading new user data
        clearData()
        _overallProgress.update {
            it.copy(
                studentName = displayName.ifBlank { email.substringBefore("@").replaceFirstChar { c -> c.uppercase() } },
                studentEmail = email
            )
        }
        refreshData()
    }

    // --- Questions CRUD ---

    override suspend fun getQuestionsForSubject(subjectId: String): List<QuestionItem> = withContext(Dispatchers.IO) {
        val inMemory = _subjects.value.find { it.id == subjectId }?.questions
        if (!inMemory.isNullOrEmpty()) return@withContext inMemory

        val client = SupabaseClientProvider.client
            ?: throw IllegalStateException("Supabase is not configured.")
        val userId = getUserId()
            ?: throw IllegalStateException("User is not authenticated.")
        try {
            val dtos: List<QuestionDto> = client.from("questions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("subject_id", subjectId)
                    }
                }
                .decodeList()
            val subName = getSubjectById(subjectId)?.name ?: "Subject"
            dtos.mapIndexed { idx, qDto ->
                val unitName = allUnits.find { it.id == qDto.unitId }?.name ?: "General"
                QuestionItem(
                    id = qDto.id ?: "q_$idx",
                    questionNumber = idx + 1,
                    questionText = qDto.question,
                    marks = qDto.marksOrDefault(),
                    unitName = unitName,
                    subjectName = subName,
                    status = mapQuestionStatusToDisplay(qDto.status),
                    subjectId = subjectId,
                    unitId = qDto.unitId,
                    topicId = qDto.topicId,
                    importance = parseImportance(qDto.importance),
                    createdAt = qDto.createdAt,
                    updatedAt = qDto.updatedAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching questions for subject $subjectId", e)
            // Surface the failure to the caller: an error must be distinguishable from a
            // genuine empty result so the UI can show an error state instead of "no data".
            throw e
        }
    }

    override suspend fun getQuestionsForTopic(topicId: String): List<QuestionItem> = withContext(Dispatchers.IO) {
        val topic = getTopicById(topicId)
        if (topic != null && topic.questions.isNotEmpty()) return@withContext topic.questions

        val client = SupabaseClientProvider.client
            ?: throw IllegalStateException("Supabase is not configured.")
        val userId = getUserId()
            ?: throw IllegalStateException("User is not authenticated.")
        try {
            val dtos: List<QuestionDto> = client.from("questions")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("topic_id", topicId)
                    }
                }
                .decodeList()
            dtos.mapIndexed { idx, qDto ->
                QuestionItem(
                    id = qDto.id ?: "q_$idx",
                    questionNumber = idx + 1,
                    questionText = qDto.question,
                    marks = qDto.marksOrDefault(),
                    unitName = topic?.title ?: "",
                    subjectName = getSubjectById(qDto.subjectId)?.name ?: "",
                    status = mapQuestionStatusToDisplay(qDto.status),
                    subjectId = qDto.subjectId,
                    unitId = qDto.unitId,
                    topicId = topicId,
                    importance = parseImportance(qDto.importance),
                    createdAt = qDto.createdAt,
                    updatedAt = qDto.updatedAt
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching questions for topic $topicId", e)
            // Surface the failure instead of masking it as an empty list.
            throw e
        }
    }

    override suspend fun addQuestion(
        subjectId: String,
        unitId: String?,
        topicId: String?,
        questionText: String,
        marks: Int,
        importance: Importance,
        status: String
    ): Result<QuestionItem> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        val trimmedText = questionText.trim()
        if (trimmedText.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Question text cannot be blank."))
        }
        if (marks <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Marks must be a positive number."))
        }

        questionMutex.withLock {
            try {
                val newId = UUID.randomUUID().toString()
                val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val dto = QuestionDto(
                    id = newId,
                    userId = userId,
                    subjectId = subjectId,
                    unitId = unitId,
                    topicId = topicId,
                    question = trimmedText,
                    marks = marks,
                    importance = importance.name,
                    status = status.uppercase().replace(" ", "_"),
                    createdAt = nowStr,
                    updatedAt = nowStr
                )

                client.from("questions").insert(dto)

                // Update cached raw questions
                allQuestions = allQuestions + dto

                // Re-assemble domain state
                val domainSubjects = assembleSubjects(
                    subjectDtos = _subjects.value.map { s ->
                        SubjectDto(
                            id = s.id,
                            userId = userId,
                            name = s.name,
                            description = s.code,
                            semester = s.semesterNumber,
                            iconKey = s.iconKey
                        )
                    },
                    unitDtos = allUnits,
                    topicDtos = allTopics,
                    questionDtos = allQuestions,
                    noteDtos = allNotes,
                    materialDtos = allMaterials,
                    revisionDtos = allRevisionHistory
                )
                _subjects.value = domainSubjects
                recomputeRecommendations(domainSubjects)

                val subject = domainSubjects.find { it.id == subjectId }
                val unitName = allUnits.find { it.id == unitId }?.name ?: "General"
                val newItem = QuestionItem(
                    id = newId,
                    questionNumber = (subject?.questions?.size ?: allQuestions.size),
                    questionText = trimmedText,
                    marks = marks,
                    unitName = unitName,
                    subjectName = subject?.name ?: "Subject",
                    status = mapQuestionStatusToDisplay(status),
                    subjectId = subjectId,
                    unitId = unitId,
                    topicId = topicId,
                    importance = importance,
                    createdAt = nowStr,
                    updatedAt = nowStr
                )

                Log.i(TAG, "Successfully added question $newId for subject $subjectId")
                Result.success(newItem)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add question to Supabase", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun updateQuestion(question: QuestionItem): Result<QuestionItem> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        questionMutex.withLock {
            try {
                val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                client.from("questions").update(
                    {
                        set("question", question.questionText.trim())
                        set("marks", question.marks)
                        set("importance", question.importance.name)
                        set("status", question.status.uppercase().replace(" ", "_"))
                        set("updated_at", nowStr)
                    }
                ) {
                    filter {
                        eq("id", question.id)
                        eq("user_id", userId)
                    }
                }

                allQuestions = allQuestions.map {
                    if (it.id == question.id) {
                        it.copy(
                            question = question.questionText.trim(),
                            marks = question.marks,
                            importance = question.importance.name,
                            status = question.status.uppercase().replace(" ", "_"),
                            updatedAt = nowStr
                        )
                    } else it
                }

                // Re-assemble domain state
                val domainSubjects = assembleSubjects(
                    subjectDtos = _subjects.value.map { s ->
                        SubjectDto(
                            id = s.id,
                            userId = userId,
                            name = s.name,
                            description = s.code,
                            semester = s.semesterNumber,
                            iconKey = s.iconKey
                        )
                    },
                    unitDtos = allUnits,
                    topicDtos = allTopics,
                    questionDtos = allQuestions,
                    noteDtos = allNotes,
                    materialDtos = allMaterials,
                    revisionDtos = allRevisionHistory
                )
                _subjects.value = domainSubjects
                recomputeRecommendations(domainSubjects)

                Result.success(question.copy(updatedAt = nowStr))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update question ${question.id}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteQuestion(questionId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        questionMutex.withLock {
            try {
                client.from("questions").delete {
                    filter {
                        eq("id", questionId)
                        eq("user_id", userId)
                    }
                }

                allQuestions = allQuestions.filter { it.id != questionId }

                val domainSubjects = assembleSubjects(
                    subjectDtos = _subjects.value.map { s ->
                        SubjectDto(
                            id = s.id,
                            userId = userId,
                            name = s.name,
                            description = s.code,
                            semester = s.semesterNumber,
                            iconKey = s.iconKey
                        )
                    },
                    unitDtos = allUnits,
                    topicDtos = allTopics,
                    questionDtos = allQuestions,
                    noteDtos = allNotes,
                    materialDtos = allMaterials,
                    revisionDtos = allRevisionHistory
                )
                _subjects.value = domainSubjects
                recomputeRecommendations(domainSubjects)

                Log.i(TAG, "Successfully deleted question $questionId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete question $questionId", e)
                Result.failure(e)
            }
        }
    }

    // --- Notes CRUD ---

    override suspend fun getNotesForSubject(subjectId: String): List<NoteItem> = withContext(Dispatchers.IO) {
        val inMemory = _subjects.value.find { it.id == subjectId }?.notes
        if (!inMemory.isNullOrEmpty()) return@withContext inMemory

        val client = SupabaseClientProvider.client
            ?: throw IllegalStateException("Supabase is not configured.")
        val userId = getUserId()
            ?: throw IllegalStateException("User is not authenticated.")
        try {
            val dtos: List<NoteDto> = client.from("notes")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("subject_id", subjectId)
                    }
                }
                .decodeList()
            val subName = getSubjectById(subjectId)?.name ?: ""
            dtos.map { nDto ->
                val topicName = allTopics.find { it.id == nDto.topicId }?.name ?: ""
                NoteItem(
                    id = nDto.id ?: UUID.randomUUID().toString(),
                    subjectId = subjectId,
                    topicId = nDto.topicId,
                    title = nDto.title,
                    content = nDto.content,
                    createdAt = nDto.createdAt,
                    updatedAt = nDto.updatedAt,
                    subjectName = subName,
                    topicName = topicName
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching notes for subject $subjectId", e)
            // Surface the failure instead of masking it as an empty list.
            throw e
        }
    }

    override suspend fun getNotesForTopic(topicId: String): List<NoteItem> = withContext(Dispatchers.IO) {
        val topic = getTopicById(topicId)
        if (topic != null && topic.notes.isNotEmpty()) return@withContext topic.notes

        val client = SupabaseClientProvider.client
            ?: throw IllegalStateException("Supabase is not configured.")
        val userId = getUserId()
            ?: throw IllegalStateException("User is not authenticated.")
        try {
            val dtos: List<NoteDto> = client.from("notes")
                .select {
                    filter {
                        eq("user_id", userId)
                        eq("topic_id", topicId)
                    }
                }
                .decodeList()
            dtos.map { nDto ->
                NoteItem(
                    id = nDto.id ?: UUID.randomUUID().toString(),
                    subjectId = nDto.subjectId,
                    topicId = topicId,
                    title = nDto.title,
                    content = nDto.content,
                    createdAt = nDto.createdAt,
                    updatedAt = nDto.updatedAt,
                    subjectName = getSubjectById(nDto.subjectId)?.name ?: "",
                    topicName = topic?.title ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching notes for topic $topicId", e)
            // Surface the failure instead of masking it as an empty list.
            throw e
        }
    }

    override suspend fun addNote(
        subjectId: String,
        topicId: String?,
        title: String,
        content: String
    ): Result<NoteItem> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Note title cannot be blank."))
        }

        noteMutex.withLock {
            try {
                val newId = UUID.randomUUID().toString()
                val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val dto = NoteDto(
                    id = newId,
                    userId = userId,
                    subjectId = subjectId,
                    topicId = topicId,
                    title = trimmedTitle,
                    content = content.trim(),
                    createdAt = nowStr,
                    updatedAt = nowStr
                )

                client.from("notes").insert(dto)

                // Update cached raw notes
                allNotes = allNotes + dto

                // Re-assemble domain state
                val domainSubjects = assembleSubjects(
                    subjectDtos = _subjects.value.map { s ->
                        SubjectDto(
                            id = s.id,
                            userId = userId,
                            name = s.name,
                            description = s.code,
                            semester = s.semesterNumber,
                            iconKey = s.iconKey
                        )
                    },
                    unitDtos = allUnits,
                    topicDtos = allTopics,
                    questionDtos = allQuestions,
                    noteDtos = allNotes,
                    materialDtos = allMaterials,
                    revisionDtos = allRevisionHistory
                )
                _subjects.value = domainSubjects

                val subject = domainSubjects.find { it.id == subjectId }
                val topic = allTopics.find { it.id == topicId }
                val newItem = NoteItem(
                    id = newId,
                    subjectId = subjectId,
                    topicId = topicId,
                    title = trimmedTitle,
                    content = content.trim(),
                    createdAt = nowStr,
                    updatedAt = nowStr,
                    subjectName = subject?.name ?: "",
                    topicName = topic?.name ?: ""
                )

                Log.i(TAG, "Successfully added note $newId for subject $subjectId")
                Result.success(newItem)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add note to Supabase", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun updateNote(note: NoteItem): Result<NoteItem> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        val trimmedTitle = note.title.trim()
        if (trimmedTitle.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Note title cannot be blank."))
        }

        noteMutex.withLock {
            try {
                val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                client.from("notes").update(
                    {
                        set("title", trimmedTitle)
                        set("content", note.content.trim())
                        set("updated_at", nowStr)
                    }
                ) {
                    filter {
                        eq("id", note.id)
                        eq("user_id", userId)
                    }
                }

                allNotes = allNotes.map {
                    if (it.id == note.id) {
                        it.copy(
                            title = trimmedTitle,
                            content = note.content.trim(),
                            updatedAt = nowStr
                        )
                    } else it
                }

                val domainSubjects = assembleSubjects(
                    subjectDtos = _subjects.value.map { s ->
                        SubjectDto(
                            id = s.id,
                            userId = userId,
                            name = s.name,
                            description = s.code,
                            semester = s.semesterNumber,
                            iconKey = s.iconKey
                        )
                    },
                    unitDtos = allUnits,
                    topicDtos = allTopics,
                    questionDtos = allQuestions,
                    noteDtos = allNotes,
                    materialDtos = allMaterials,
                    revisionDtos = allRevisionHistory
                )
                _subjects.value = domainSubjects

                Result.success(note.copy(title = trimmedTitle, content = note.content.trim(), updatedAt = nowStr))
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update note ${note.id}", e)
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteNote(noteId: String): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(IllegalStateException("Supabase is not configured."))
        val userId = getUserId()
            ?: return@withContext Result.failure(IllegalStateException("User is not authenticated."))

        noteMutex.withLock {
            try {
                client.from("notes").delete {
                    filter {
                        eq("id", noteId)
                        eq("user_id", userId)
                    }
                }

                allNotes = allNotes.filter { it.id != noteId }

                val domainSubjects = assembleSubjects(
                    subjectDtos = _subjects.value.map { s ->
                        SubjectDto(
                            id = s.id,
                            userId = userId,
                            name = s.name,
                            description = s.code,
                            semester = s.semesterNumber,
                            iconKey = s.iconKey
                        )
                    },
                    unitDtos = allUnits,
                    topicDtos = allTopics,
                    questionDtos = allQuestions,
                    noteDtos = allNotes,
                    materialDtos = allMaterials,
                    revisionDtos = allRevisionHistory
                )
                _subjects.value = domainSubjects

                Log.i(TAG, "Successfully deleted note $noteId")
                Result.success(Unit)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete note $noteId", e)
                Result.failure(e)
            }
        }
    }

    // --- Helper Methods ---

    private fun parseTopicStatus(str: String): TopicStatus {
        return when (str.uppercase().replace(" ", "_").replace("-", "_")) {
            "COMPLETED" -> TopicStatus.COMPLETED
            "LEARNING" -> TopicStatus.LEARNING
            "NEEDS_REVISION", "REVISION_NEEDED", "REVISION_DUE", "NEEDSREVISION" -> TopicStatus.NEEDS_REVISION
            else -> TopicStatus.NOT_STARTED
        }
    }

    private fun parseDifficulty(str: String): Difficulty {
        return when (str.uppercase()) {
            "EASY" -> Difficulty.EASY
            "HARD" -> Difficulty.HARD
            else -> Difficulty.MEDIUM
        }
    }

    private fun parseImportance(str: String): Importance {
        return when (str.uppercase()) {
            "LOW" -> Importance.LOW
            "HIGH" -> Importance.HIGH
            else -> Importance.MEDIUM
        }
    }

    private fun calculatePriorityScore(topic: TopicDto): Int {
        var score = when (topic.importance.uppercase()) {
            "HIGH" -> 80
            "MEDIUM" -> 50
            else -> 30
        }
        score += when (topic.difficulty.uppercase()) {
            "HARD" -> 15
            "MEDIUM" -> 10
            else -> 5
        }
        if (topic.status.equals("NOT_STARTED", ignoreCase = true)) {
            score += 5
        }
        return score.coerceIn(0, 100)
    }

    private fun formatDisplayDate(dateStr: String?, defaultVal: String): String {
        if (dateStr.isNullOrBlank()) return defaultVal
        val candidateFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US),
            SimpleDateFormat("yyyy-MM-dd", Locale.US),
            SimpleDateFormat("MMM d, yyyy", Locale.US),
            SimpleDateFormat("MMMM d, yyyy", Locale.US)
        )
        val cleanStr = dateStr.substringBefore("Z").substringBefore(".")
        for (f in candidateFormats) {
            try {
                val d = f.parse(cleanStr)
                if (d != null) {
                    return displayDateFormat.format(d)
                }
            } catch (_: Exception) {}
        }
        return dateStr
    }

    private fun getAccentColorForIcon(iconKey: String): Long {
        return when (iconKey.lowercase()) {
            "database", "dbms" -> 0xFF10B981
            "ai" -> 0xFF8B5CF6
            "coffee", "java" -> 0xFF3B82F6
            "math" -> 0xFFF59E0B
            "os" -> 0xFFEC4899
            else -> 0xFF6366F1
        }
    }

    companion object {
        private const val TAG = "SupabaseStudyVaultRepo"
        val instance: SupabaseStudyVaultRepository by lazy { SupabaseStudyVaultRepository() }
    }
}
