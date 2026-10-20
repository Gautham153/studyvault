package com.example

import com.example.data.model.Difficulty
import com.example.data.model.ExamItem
import com.example.data.model.Importance
import com.example.data.model.QuestionItem
import com.example.data.model.Subject
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.data.model.UnitModel
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.StudyAction
import com.example.data.repository.StudyPriorityEngine
import com.example.data.repository.TodayPlanConfig
import com.example.ui.screens.home.todayPlanEmptyText
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Today's Plan question recommendations: important questions (including ones with no linked
 * topic) surface in the plan with a Practice action, while topic recommendations stay unchanged.
 *
 * The engine is pure Kotlin, so these are plain JVM tests (no Robolectric/Android runtime). All
 * dates are passed explicitly via a fixed [TODAY] so results never depend on the machine clock.
 */
class TodayPlanQuestionRecommendationsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    // --- Eligibility ---

    @Test
    fun `high importance question without a topic link surfaces as a practice recommendation`() {
        val subject = subjectWith(question("q1", text = "Explain deadlock avoidance", marks = 8, topicId = null))

        val rec = StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), emptyList(), TODAY).single()

        assertTrue(rec.isQuestion)
        assertEquals("q1", rec.questionId)
        assertEquals("", rec.topicId)
        assertEquals("question:q1", rec.recommendationKey)
        assertEquals(StudyAction.PRACTICE, rec.action)
        assertEquals("Explain deadlock avoidance", rec.topicName)
        assertTrue(rec.reasons.contains("High importance"))
        assertTrue(rec.reasons.contains("8 marks"))
    }

    @Test
    fun `medium and low importance questions are eligible with high importance scoring first`() {
        val subject = subjectWith(
            question("q_med", importance = Importance.MEDIUM, status = "Not Prepared"),
            question("q_low", importance = Importance.LOW, status = "Not Prepared"),
            question("q_high", importance = Importance.HIGH, status = "Not Prepared")
        )

        val recs = StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), emptyList(), TODAY)

        assertEquals(listOf("q_high", "q_med", "q_low"), recs.map { it.questionId })
    }

    @Test
    fun `medium importance not prepared question with no exam is still eligible`() {
        val subject = subjectWith(
            question("q_med", importance = Importance.MEDIUM, status = "Not Prepared", marks = 3)
        )

        val rec = StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), emptyList(), TODAY).single()

        assertEquals("q_med", rec.questionId)
        assertEquals(15 + 25 + 0 + 6, rec.score)
    }

    @Test
    fun `medium importance question with an upcoming exam outscores one without an exam`() {
        val withExam = Subject(
            id = "s1",
            name = "Subject s1",
            code = "C1",
            iconKey = "book",
            preparationPercent = 0,
            unitsCount = 1,
            topicsCount = 0,
            units = listOf(UnitModel("s1_u1", "s1", 1, "Unit 1", 0)),
            questions = listOf(question("q_near", importance = Importance.MEDIUM, status = "Not Prepared"))
        )
        val withoutExam = Subject(
            id = "s2",
            name = "Subject s2",
            code = "C2",
            iconKey = "book",
            preparationPercent = 0,
            unitsCount = 1,
            topicsCount = 0,
            units = listOf(UnitModel("s2_u1", "s2", 1, "Unit 1", 0)),
            questions = listOf(
                question("q_far", importance = Importance.MEDIUM, status = "Not Prepared").copy(subjectId = "s2")
            )
        )
        val exam = examItem("e1", "s1", iso = isoPlusDays(TODAY, 3))

        val recs = StudyPriorityEngine.buildQuestionRecommendations(
            listOf(withExam, withoutExam),
            listOf(exam),
            TODAY
        )

        assertEquals(listOf("q_near", "q_far"), recs.map { it.questionId })
        val near = recs.first { it.questionId == "q_near" }
        val far = recs.first { it.questionId == "q_far" }
        assertTrue("Exam urgency must boost the medium-importance question", near.score > far.score)
    }

    @Test
    fun `prepared questions are excluded while unprepared learning and high priority ones are included`() {
        val subject = subjectWith(
            question("q_done", status = "Prepared"),
            question("q_not", status = "Not Prepared"),
            question("q_learn", status = "Learning"),
            question("q_flag", status = "High Priority")
        )

        val recs = StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), emptyList(), TODAY)

        assertEquals(listOf("q_flag", "q_learn", "q_not"), recs.sortedBy { it.questionId }.map { it.questionId })
        assertTrue(recs.none { it.questionId == "q_done" })
    }

    @Test
    fun `question preparation status maps onto the shared topic status scale`() {
        val subject = subjectWith(
            question("q_not", status = "Not Prepared"),
            question("q_learn", status = "Learning"),
            question("q_flag", status = "High Priority"),
            question("q_done", status = "Prepared")
        )

        val statuses = StudyPriorityEngine.buildQuestionSnapshots(listOf(subject), emptyList(), TODAY)
            .associate { it.questionId to it.status }

        assertEquals(TopicStatus.NOT_STARTED, statuses["q_not"])
        assertEquals(TopicStatus.LEARNING, statuses["q_learn"])
        assertEquals(TopicStatus.NEEDS_REVISION, statuses["q_flag"])
        assertEquals(TopicStatus.COMPLETED, statuses["q_done"])
    }

    @Test
    fun `question recommendations preserve real topic links and never invent topic ids`() {
        val subject = subjectWith(
            question("q_linked", topicId = "t_real"),
            question("q_unlinked", topicId = null)
        )

        val recs = StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), emptyList(), TODAY)
            .associateBy { it.questionId }

        assertEquals("t_real", recs.getValue("q_linked").topicId)
        assertEquals("question:q_linked", recs.getValue("q_linked").recommendationKey)
        assertEquals("", recs.getValue("q_unlinked").topicId)
    }

    // --- Scoring ---

    @Test
    fun `question score is the sum of importance gap exam urgency and marks capped at the ceiling`() {
        val worst = StudyPriorityEngine.scoreQuestion(
            snapshotOf(marks = 10, status = TopicStatus.NOT_STARTED, daysUntilExam = 0),
            TODAY
        )
        assertEquals(25 + 25 + 30 + 20, worst.score)
        assertTrue(worst.score <= StudyPriorityEngine.MAX_SCORE)

        val noExam = StudyPriorityEngine.scoreQuestion(
            snapshotOf(marks = 3, status = TopicStatus.NOT_STARTED, daysUntilExam = null),
            TODAY
        )
        assertEquals(25 + 25 + 0 + 6, noExam.score)

        val revisionGap = StudyPriorityEngine.scoreQuestion(
            snapshotOf(marks = 0, status = TopicStatus.NEEDS_REVISION, daysUntilExam = null),
            TODAY
        )
        assertEquals(25 + 10 + 0 + 0, revisionGap.score)
    }

    @Test
    fun `question estimated minutes grow with marks and never drop below the floor`() {
        val five = StudyPriorityEngine.scoreQuestion(snapshotOf(marks = 5, status = TopicStatus.NOT_STARTED), TODAY)
        assertEquals(20 + 5, five.estimatedMinutes)

        val big = StudyPriorityEngine.scoreQuestion(snapshotOf(marks = 40, status = TopicStatus.LEARNING), TODAY)
        assertEquals(15 + StudyPriorityEngine.MAX_QUESTION_MARKS_SIGNAL, big.estimatedMinutes)

        val floor = StudyPriorityEngine.scoreQuestion(
            snapshotOf(marks = -3, status = TopicStatus.COMPLETED),
            TODAY
        )
        assertEquals(10, floor.estimatedMinutes)
    }

    @Test
    fun `exam urgency applies to a subject that has no topics at all`() {
        val subject = Subject(
            id = "s1",
            name = "Subject s1",
            code = "C1",
            iconKey = "book",
            preparationPercent = 0,
            unitsCount = 0,
            topicsCount = 0,
            units = emptyList(),
            questions = listOf(question("q1", marks = 5, topicId = null))
        )
        val exam = examItem("e1", "s1", iso = isoPlusDays(TODAY, 3))

        val rec = StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), listOf(exam), TODAY).single()

        assertEquals(3, rec.daysUntilExam)
        assertEquals(25 + 25 + 28 + 10, rec.score)
        assertTrue(rec.reasons.any { it.contains("in 3 days") })
    }

    // --- Plan selection ---

    @Test
    fun `selectTodayPlan mixes topics and questions without duplicate keys`() {
        val topic = topicModel("t1", TopicStatus.NOT_STARTED, Importance.HIGH)
        val subject = subjectWith(question("q1", marks = 5, topicId = "t1"), topics = listOf(topic))

        val topicRecs = StudyPriorityEngine.buildRecommendations(listOf(subject), emptyList(), todayIso = TODAY)
        val questionRecs = StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), emptyList(), TODAY)

        val plan = StudyPriorityEngine.selectTodayPlan(
            recommendations = topicRecs + questionRecs,
            config = TodayPlanConfig(maxItems = 10)
        )

        val keys = plan.map { it.recommendationKey }
        assertEquals(keys.size, keys.toSet().size)
        assertTrue("topic:t1" in keys)
        assertTrue("question:q1" in keys)
    }

    @Test
    fun `selectTodayPlan caps a mixed plan and prefers the higher scored question first`() {
        val topic = topicModel("t1", TopicStatus.NOT_STARTED, Importance.HIGH)
        val subject = subjectWith(question("q1", marks = 5, topicId = "t1"), topics = listOf(topic))

        val mixed = StudyPriorityEngine.buildRecommendations(listOf(subject), emptyList(), todayIso = TODAY) +
            StudyPriorityEngine.buildQuestionRecommendations(listOf(subject), emptyList(), TODAY)

        val capped = StudyPriorityEngine.selectTodayPlan(mixed, TodayPlanConfig(maxItems = 1))

        assertEquals(1, capped.size)
        assertTrue(capped.single().isQuestion)
    }

    @Test
    fun `topic recommendations stay question free even when important questions exist`() {
        val subject = subjectWith(
            question("q1", importance = Importance.HIGH, status = "Not Prepared"),
            topics = listOf(topicModel("t1", TopicStatus.NOT_STARTED, Importance.HIGH))
        )

        val topicRecs = StudyPriorityEngine.buildRecommendations(listOf(subject), emptyList(), todayIso = TODAY)

        assertTrue(topicRecs.isNotEmpty())
        assertTrue(topicRecs.all { !it.isQuestion && it.questionId == null })
    }

    // --- Repository wiring ---

    @Test
    fun `mock repository surfaces added high importance questions in the plan only`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        assertTrue(repository.todayStudyPlan.value.none { it.isQuestion })
        assertTrue(repository.studyRecommendations.value.none { it.isQuestion })

        val added = repository.addQuestion(
            subjectId = "s_java",
            unitId = null,
            topicId = null,
            questionText = "Explain class loaders",
            marks = 8,
            importance = Importance.HIGH,
            status = "Not Prepared"
        ).getOrThrow()

        val planRec = repository.todayStudyPlan.value.single { it.isQuestion }
        assertEquals(added.id, planRec.questionId)
        assertEquals("", planRec.topicId)
        assertEquals(StudyAction.PRACTICE, planRec.action)

        assertTrue("Study recommendations stay topic-only",
            repository.studyRecommendations.value.none { it.isQuestion })
        assertTrue("Legacy Study Today items stay topic-only",
            repository.getTodayStudyItems().none { it.topicId.isBlank() })

        repository.updateQuestion(added.copy(importance = Importance.MEDIUM))
        assertEquals(
            "MEDIUM-importance questions stay eligible while unprepared",
            added.id,
            repository.todayStudyPlan.value.single { it.isQuestion }.questionId
        )

        repository.updateQuestion(added.copy(status = "Prepared"))
        assertTrue("Prepared questions drop out of the plan", repository.todayStudyPlan.value.none { it.isQuestion })

        val reAdded = repository.addQuestion(
            subjectId = "s_java",
            unitId = null,
            topicId = null,
            questionText = "Explain JIT compilation",
            marks = 10,
            importance = Importance.HIGH,
            status = "High Priority"
        ).getOrThrow()
        assertEquals(reAdded.id, repository.todayStudyPlan.value.single { it.isQuestion }.questionId)

        repository.deleteQuestion(reAdded.id)
        assertTrue(repository.todayStudyPlan.value.none { it.isQuestion })
    }

    @Test
    fun `upcoming exams stay visible even when subjects have no topics`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        assertTrue("Exam schedule is topic-independent", repository.upcomingExams.value.isNotEmpty())
    }

    // --- Empty-state messaging ---

    @Test
    fun `empty plan text distinguishes loading error no data and clear states`() {
        assertEquals("Loading your plan...", todayPlanEmptyText(isLoading = true, hasSubjects = false, hasError = false))
        assertEquals("Plan unavailable — see the notice above.", todayPlanEmptyText(isLoading = false, hasSubjects = true, hasError = true))
        assertEquals(
            "No study data yet — add subjects, topics, or important questions to build your plan.",
            todayPlanEmptyText(isLoading = false, hasSubjects = false, hasError = false)
        )
        assertEquals("Your plan is clear — nothing flagged right now.", todayPlanEmptyText(isLoading = false, hasSubjects = true, hasError = false))
    }

    // --- Fixtures ---

    private fun question(
        id: String,
        text: String = "Question $id",
        marks: Int = 5,
        status: String = "Not Prepared",
        importance: Importance = Importance.HIGH,
        topicId: String? = null
    ) = QuestionItem(
        id = id,
        questionNumber = 1,
        questionText = text,
        marks = marks,
        unitName = "Unit 1",
        subjectName = "Subject s1",
        status = status,
        subjectId = "s1",
        unitId = "s1_u1",
        topicId = topicId,
        importance = importance
    )

    private fun topicModel(
        id: String,
        status: TopicStatus,
        importance: Importance
    ) = TopicModel(
        id = id,
        unitId = "s1_u1",
        subjectId = "s1",
        title = "Topic $id",
        status = status,
        difficulty = Difficulty.MEDIUM,
        importance = importance,
        lastRevisedAt = null
    )

    private fun subjectWith(vararg questions: QuestionItem, topics: List<TopicModel> = emptyList()) = Subject(
        id = "s1",
        name = "Subject s1",
        code = "C1",
        iconKey = "book",
        preparationPercent = 0,
        unitsCount = 1,
        topicsCount = topics.size,
        units = listOf(
            UnitModel(
                id = "s1_u1",
                subjectId = "s1",
                unitNumber = 1,
                title = "Unit 1",
                preparationPercent = 0,
                topics = topics.toList()
            )
        ),
        questions = questions.toList()
    )

    private fun snapshotOf(
        marks: Int = 5,
        status: TopicStatus = TopicStatus.NOT_STARTED,
        importance: Importance = Importance.HIGH,
        daysUntilExam: Int? = null
    ) = com.example.data.repository.QuestionStudySnapshot(
        questionId = "q1",
        subjectId = "s1",
        unitId = "s1_u1",
        questionText = "Question",
        subjectName = "Subject s1",
        unitName = "Unit 1",
        iconKey = "book",
        status = status,
        importance = importance,
        marks = marks,
        daysUntilExam = daysUntilExam,
        examDateDisplay = null,
        examName = null,
        linkedTopicId = null
    )

    private fun examItem(id: String, subjectId: String, iso: String) = ExamItem(
        id = id,
        subjectId = subjectId,
        subjectName = "Subject $subjectId",
        iconKey = "book",
        examDate = iso,
        daysRemaining = 999,
        preparationPercent = 0,
        name = "Exam",
        isoDate = iso
    )

    private fun isoPlusDays(baseIso: String, days: Int): String {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = format.parse(baseIso)!!
        return Calendar.getInstance().apply {
            time = date
            add(Calendar.DAY_OF_MONTH, days)
        }.let { format.format(it.time) }
    }

    private companion object {
        const val TODAY = "2026-10-18"
    }
}
