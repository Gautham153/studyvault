package com.example.data.repository

import android.util.Log
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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
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

    private val _priorityStudyItems = MutableStateFlow<List<StudyItem>>(emptyList())
    override val priorityStudyItems: StateFlow<List<StudyItem>> = _priorityStudyItems.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    override val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var loadRequestToken = 0

    // Cached raw collections for fast entity composition
    private var allUnits: List<UnitDto> = emptyList()
    private var allTopics: List<TopicDto> = emptyList()
    private var allQuestions: List<QuestionDto> = emptyList()
    private var allNotes: List<NoteDto> = emptyList()
    private var allMaterials: List<StudyMaterialDto> = emptyList()

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
        _priorityStudyItems.value = emptyList()
        allUnits = emptyList()
        allTopics = emptyList()
        allQuestions = emptyList()
        allNotes = emptyList()
        allMaterials = emptyList()
        _overallProgress.value = defaultProgress
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

            // Construct Domain Subjects
            val domainSubjects = assembleSubjects(subjectDtos, unitDtos, topicDtos, questionDtos, noteDtos, materialDtos)
            _subjects.value = domainSubjects

            // Construct Domain Exams
            val domainExams = assembleExams(examDtos, subjectDtos)
            _upcomingExams.value = domainExams

            // Construct Priority Study Items
            val domainPriorityItems = assemblePriorityStudyItems(topicDtos, unitDtos, subjectDtos)
            _priorityStudyItems.value = domainPriorityItems

            // Calculate overall progress
            val totalTopics = topicDtos.size
            val completedTopics = topicDtos.count { it.status.equals("COMPLETED", ignoreCase = true) }
            val overallPct = if (totalTopics > 0) (completedTopics * 100) / totalTopics else 0

            val activeSemester = subjectDtos.firstOrNull()?.semester?.let { formatSemesterLabel(it) }
                ?: _overallProgress.value.semester

            _overallProgress.update { current ->
                current.copy(
                    overallPercentage = overallPct,
                    subjectsCount = domainSubjects.size,
                    upcomingExamsCount = domainExams.size,
                    semester = activeSemester
                )
            }

            Log.i(TAG, "Successfully loaded data from Supabase: ${domainSubjects.size} subjects, ${domainExams.size} exams.")
        } catch (e: Exception) {
            if (requestToken == loadRequestToken && getUserId() == startingUserId) {
                Log.e(TAG, "Error loading data from Supabase", e)
                clearData()
                _errorMessage.value = e.message ?: "Failed to load academic data from Supabase."
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
        materialDtos: List<StudyMaterialDto> = emptyList()
    ): List<Subject> {
        val domainMaterials = materialDtos.map { dtoToMaterialItem(it) }

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
                        relatedMaterials = materialsForTopic,
                        questions = questionsForTopic.mapIndexed { idx, qDto ->
                            QuestionItem(
                                id = qDto.id ?: "q_$idx",
                                questionNumber = idx + 1,
                                questionText = qDto.question,
                                marks = qDto.marks,
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
                        marks = qDto.marks,
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

    private fun assembleExams(
        examDtos: List<ExamDto>,
        subjectDtos: List<SubjectDto>
    ): List<ExamItem> {
        val subjectMap = subjectDtos.associateBy { it.id }
        return examDtos.map { eDto ->
            val subject = subjectMap[eDto.subjectId]
            val subName = subject?.name ?: "Exam"
            val icon = subject?.iconKey ?: "book"
            val days = calculateDaysRemaining(eDto.examDate)

            ExamItem(
                id = eDto.id ?: "",
                subjectId = eDto.subjectId,
                subjectName = subName,
                iconKey = icon,
                examDate = formatDisplayDate(eDto.examDate, eDto.examDate),
                daysRemaining = days,
                preparationPercent = 50,
                code = subject?.description?.takeIf { it.isNotBlank() } ?: "EXAM",
                venue = eDto.venue ?: "Main Hall"
            )
        }
    }

    private fun assemblePriorityStudyItems(
        topicDtos: List<TopicDto>,
        unitDtos: List<UnitDto>,
        subjectDtos: List<SubjectDto>
    ): List<StudyItem> {
        val unitMap = unitDtos.associateBy { it.id }
        val subjectMap = subjectDtos.associateBy { it.id }

        return topicDtos
            .filter { !it.status.equals("COMPLETED", ignoreCase = true) }
            .map { tDto ->
                val unit = unitMap[tDto.unitId]
                val subject = unit?.let { subjectMap[it.subjectId] }
                val score = calculatePriorityScore(tDto)
                val level = if (score >= 80) "High Priority" else "Medium Priority"
                val note = when {
                    score >= 90 -> "Revision due today"
                    score >= 80 -> "Revision due tomorrow"
                    tDto.status.equals("LEARNING", ignoreCase = true) -> "Learning in progress"
                    else -> "Not completed"
                }

                StudyItem(
                    topicId = tDto.id ?: "",
                    subjectId = subject?.id ?: "",
                    unitId = unit?.id ?: "",
                    topicName = tDto.name,
                    subjectName = subject?.name ?: "Subject",
                    unitName = unit?.name ?: "Unit",
                    priorityScore = score,
                    priorityLevel = level,
                    statusNote = note
                )
            }
            .sortedByDescending { it.priorityScore }
    }

    // --- StudyVaultRepository Interface Implementation ---

    override fun getTodayStudyItems(): List<StudyItem> {
        return _priorityStudyItems.value.take(3)
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

    override fun updateTopicStatus(topicId: String, status: TopicStatus) {
        val client = SupabaseClientProvider.client
        val userId = getUserId()
        if (client == null || userId == null) {
            val err = if (client == null) {
                "Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence."
            } else {
                "Cannot update topic status: User is not authenticated."
            }
            Log.e(TAG, err)
            _errorMessage.value = err
            return
        }

        val previousTopic = getTopicById(topicId)
        val prevStatus = previousTopic?.status ?: TopicStatus.NOT_STARTED

        scope.launch {
            topicMutex.withLock {
                try {
                    // Fetch latest revision history and topic record from Supabase directly to prevent stale cache or race conditions
                    val historyList: List<RevisionHistoryDto> = client.from("revision_history")
                        .select {
                            filter {
                                eq("topic_id", topicId)
                                eq("user_id", userId)
                            }
                        }
                        .decodeList()

                    val maxHistRev = historyList.maxOfOrNull { it.revisionNumber } ?: 0

                    val topicDto: TopicDto? = client.from("topics")
                        .select {
                            filter {
                                eq("id", topicId)
                                eq("user_id", userId)
                            }
                        }
                        .decodeSingleOrNull()

                    val currentRev = maxOf(maxHistRev, topicDto?.revisionCount ?: 0)
                    val nextRevisionNumber = currentRev + 1

                    val nowStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                    }.format(Date())

                    val statusStr = status.name

                    // 1. Update topic in database
                    client.from("topics").update(
                        {
                            set("status", statusStr)
                            set("last_studied_at", nowStr)
                            set("revision_count", nextRevisionNumber)
                            set("updated_at", nowStr)
                        }
                    ) {
                        filter {
                            eq("id", topicId)
                            eq("user_id", userId)
                        }
                    }

                    // 2. Insert revision history record
                    val revHistory = RevisionHistoryDto(
                        id = UUID.randomUUID().toString(),
                        userId = userId,
                        topicId = topicId,
                        studiedAt = nowStr,
                        revisionNumber = nextRevisionNumber,
                        previousStatus = prevStatus.name,
                        newStatus = statusStr,
                        createdAt = nowStr
                    )
                    client.from("revision_history").insert(revHistory)

                    // 3. Only update local memory and StateFlow after BOTH database operations succeed
                    allTopics = allTopics.map {
                        if (it.id == topicId) it.copy(
                            status = statusStr,
                            revisionCount = nextRevisionNumber,
                            lastStudiedAt = nowStr
                        ) else it
                    }

                    applyLocalTopicStatusUpdate(topicId, status, nextRevisionNumber, nowStr)
                    _errorMessage.value = null
                    Log.i(TAG, "Successfully updated topic $topicId status to $status (revision #$nextRevisionNumber)")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to persist topic status update to Supabase", e)
                    _errorMessage.value = "Failed to update topic: ${e.message}"
                    // Sync local state with actual database state to avoid inconsistent display
                    loadAllData()
                }
            }
        }
    }

    private fun applyLocalTopicStatusUpdate(
        topicId: String,
        status: TopicStatus,
        revisionCount: Int,
        lastStudiedIso: String
    ) {
        val displayDate = formatDisplayDate(lastStudiedIso, "Just now")
        _subjects.update { currentSubjects ->
            currentSubjects.map { subject ->
                subject.copy(
                    units = subject.units.map { unit ->
                        val updatedTopics = unit.topics.map { topic ->
                            if (topic.id == topicId) {
                                topic.copy(
                                    status = status,
                                    lastStudiedDate = displayDate
                                )
                            } else topic
                        }
                        val completedInUnit = updatedTopics.count { it.status == TopicStatus.COMPLETED }
                        val unitPrepPct = if (updatedTopics.isNotEmpty()) (completedInUnit * 100) / updatedTopics.size else 0
                        unit.copy(topics = updatedTopics, preparationPercent = unitPrepPct)
                    }
                )
            }
        }

        // Recalculate priority items
        _priorityStudyItems.update { items ->
            if (status == TopicStatus.COMPLETED) {
                items.filter { it.topicId != topicId }
            } else {
                items.map {
                    if (it.topicId == topicId) it.copy(statusNote = "Learning in progress") else it
                }
            }
        }

        // Recalculate overall progress
        val allCurrentTopics = _subjects.value.flatMap { it.units }.flatMap { it.topics }
        val totalTopics = allCurrentTopics.size
        val completedTopics = allCurrentTopics.count { it.status == TopicStatus.COMPLETED }
        val overallPct = if (totalTopics > 0) (completedTopics * 100) / totalTopics else 0
        _overallProgress.update { it.copy(overallPercentage = overallPct) }
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

    override fun addExam(subjectName: String, date: String, daysRemaining: Int) {
        val client = SupabaseClientProvider.client
        val userId = getUserId()
        if (client == null || userId == null) {
            val err = if (client == null) {
                "Supabase is unconfigured. Please configure SUPABASE_URL and SUPABASE_ANON_KEY to enable cloud persistence."
            } else {
                "Cannot schedule exam: User is not authenticated."
            }
            Log.e(TAG, err)
            _errorMessage.value = err
            return
        }

        // Validate exam date strictly before any optimistic update or insert
        val isoTimestamp = convertToIso8601Timestamp(date)
        if (isoTimestamp == null) {
            val err = "Invalid exam date '$date'. Please enter a valid date (e.g. 'Nov 15, 2026' or '2026-11-15')."
            Log.e(TAG, err)
            _errorMessage.value = err
            return
        }

        // Match subject strictly; never create an invalid or random UUID when no subject exists
        val trimmedName = subjectName.trim()
        val matchedSubject = _subjects.value.find {
            it.name.equals(trimmedName, ignoreCase = true) ||
            it.code.equals(trimmedName, ignoreCase = true) ||
            it.id == trimmedName
        }

        if (matchedSubject == null) {
            val err = "Subject '$trimmedName' not found. Please create the subject before scheduling an exam."
            Log.e(TAG, err)
            _errorMessage.value = err
            return
        }

        val subjectId = matchedSubject.id
        val newId = UUID.randomUUID().toString()
        val formattedDate = formatDisplayDate(isoTimestamp, date)
        val calculatedDays = calculateDaysRemaining(isoTimestamp)

        val newExam = ExamItem(
            id = newId,
            subjectId = subjectId,
            subjectName = matchedSubject.name,
            iconKey = matchedSubject.iconKey,
            examDate = formattedDate,
            daysRemaining = calculatedDays,
            preparationPercent = 0,
            code = matchedSubject.code,
            venue = "Main Hall"
        )

        // Optimistically update
        _upcomingExams.update { it + newExam }
        _overallProgress.update { it.copy(upcomingExamsCount = it.upcomingExamsCount + 1) }

        scope.launch {
            try {
                val now = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                val dto = ExamDto(
                    id = newId,
                    userId = userId,
                    subjectId = subjectId,
                    name = matchedSubject.name,
                    examDate = isoTimestamp, // Valid ISO-8601 format for PostgreSQL timestamp column
                    venue = "Main Hall",
                    description = "${matchedSubject.name} Exam",
                    createdAt = now,
                    updatedAt = now
                )
                client.from("exams").insert(dto)
                _errorMessage.value = null
                Log.i(TAG, "Successfully inserted exam ${matchedSubject.name} ($isoTimestamp) to Supabase.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert exam to Supabase", e)
                // Revert optimistic update to prevent phantom records
                _upcomingExams.update { currentList -> currentList.filter { it.id != newId } }
                _overallProgress.update { it.copy(upcomingExamsCount = (it.upcomingExamsCount - 1).coerceAtLeast(0)) }
                _errorMessage.value = "Failed to save exam: ${e.message}"
            }
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

        val client = SupabaseClientProvider.client ?: return@withContext emptyList()
        val userId = getUserId() ?: return@withContext emptyList()
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
                    marks = qDto.marks,
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
            emptyList()
        }
    }

    override suspend fun getQuestionsForTopic(topicId: String): List<QuestionItem> = withContext(Dispatchers.IO) {
        val topic = getTopicById(topicId)
        if (topic != null && topic.questions.isNotEmpty()) return@withContext topic.questions

        val client = SupabaseClientProvider.client ?: return@withContext emptyList()
        val userId = getUserId() ?: return@withContext emptyList()
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
                    marks = qDto.marks,
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
            emptyList()
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
                    materialDtos = allMaterials
                )
                _subjects.value = domainSubjects

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
                    materialDtos = allMaterials
                )
                _subjects.value = domainSubjects

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
                    materialDtos = allMaterials
                )
                _subjects.value = domainSubjects

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

        val client = SupabaseClientProvider.client ?: return@withContext emptyList()
        val userId = getUserId() ?: return@withContext emptyList()
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
            emptyList()
        }
    }

    override suspend fun getNotesForTopic(topicId: String): List<NoteItem> = withContext(Dispatchers.IO) {
        val topic = getTopicById(topicId)
        if (topic != null && topic.notes.isNotEmpty()) return@withContext topic.notes

        val client = SupabaseClientProvider.client ?: return@withContext emptyList()
        val userId = getUserId() ?: return@withContext emptyList()
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
            emptyList()
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
                    materialDtos = allMaterials
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
                    materialDtos = allMaterials
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
                    materialDtos = allMaterials
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

    private fun convertToIso8601Timestamp(dateStr: String): String? {
        val trimmed = dateStr.trim()
        if (trimmed.isBlank()) return null

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

        if (trimmed.contains("T")) {
            val isoCandidatePatterns = listOf(
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd'T'HH:mm:ss"
            )
            for (pattern in isoCandidatePatterns) {
                try {
                    val parser = SimpleDateFormat(pattern, Locale.US).apply {
                        timeZone = TimeZone.getTimeZone("UTC")
                        isLenient = false
                    }
                    val parsed = parser.parse(trimmed)
                    if (parsed != null) {
                        return isoFormat.format(parsed)
                    }
                } catch (_: Exception) {}
            }
        }

        val candidatePatterns = listOf(
            "MMM d, yyyy",
            "MMM dd, yyyy",
            "MMMM d, yyyy",
            "MMMM dd, yyyy",
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "dd-MM-yyyy",
            "dd/MM/yyyy",
            "MM/dd/yyyy",
            "d MMM yyyy",
            "dd MMM yyyy"
        )

        for (pattern in candidatePatterns) {
            try {
                val parser = SimpleDateFormat(pattern, Locale.US).apply {
                    isLenient = false
                }
                val parsed = parser.parse(trimmed)
                if (parsed != null) {
                    return isoFormat.format(parsed)
                }
            } catch (_: Exception) {}
        }

        return null
    }

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

    private fun calculateDaysRemaining(dateStr: String): Int {
        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val dateOnlyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val displayFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)

        return try {
            val target = when {
                dateStr.contains("T") -> isoFormat.parse(dateStr.substringBefore("Z").substringBefore("."))
                dateStr.contains("-") -> dateOnlyFormat.parse(dateStr)
                else -> displayFormat.parse(dateStr)
            } ?: return 30
            val diff = target.time - System.currentTimeMillis()
            (diff / (1000 * 60 * 60 * 24)).toInt().coerceAtLeast(0)
        } catch (_: Exception) {
            30
        }
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
