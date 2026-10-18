package com.example

import com.example.data.model.Difficulty
import com.example.data.model.ExamItem
import com.example.data.model.Importance
import com.example.data.model.RevisionEvent
import com.example.data.model.Subject
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.data.model.UnitModel
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.PriorityLevel
import com.example.data.repository.StudyAction
import com.example.data.repository.StudyPriorityEngine
import com.example.data.repository.StudyRecommendation
import com.example.data.repository.TodayPlanConfig
import com.example.data.repository.TopicStudySnapshot
import com.example.ui.screens.study.StudyViewModel
import com.example.util.StudyDateUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Phase 7: deterministic Smart Study Priority Engine.
 *
 * The engine is pure Kotlin, so these are plain JVM tests (no Robolectric/Android runtime). All
 * dates are passed explicitly via a fixed [TODAY] so results never depend on the machine clock.
 */
class Phase7StudyPriorityTest {

    // --- Scoring components: each isolated so failures point at a single weight ---

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `importance contributes expected points`() {
        assertEquals(25, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.HIGH)))
        assertEquals(15, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.MEDIUM)))
        assertEquals(8, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW)))
    }

    @Test
    fun `progress gap contributes expected points`() {
        assertEquals(8 + 25, scored(snapshot("a", TopicStatus.NOT_STARTED, importance = Importance.LOW)))
        assertEquals(8 + 18, scored(snapshot("a", TopicStatus.LEARNING, importance = Importance.LOW)))
        assertEquals(8 + 10 + 20, scored(snapshot("a", TopicStatus.NEEDS_REVISION, importance = Importance.LOW)))
        assertEquals(8, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW)))
    }

    @Test
    fun `exam urgency scales with proximity to the nearest exam`() {
        assertEquals(8 + 30, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = 0)))
        assertEquals(8 + 30, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = 1)))
        assertEquals(8 + 28, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = 3)))
        assertEquals(8 + 24, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = 5)))
        assertEquals(8 + 16, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = 10)))
        assertEquals(8 + 8, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = 20)))
        assertEquals(8 + 3, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = 60)))
        assertEquals(8, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, daysUntilExam = -2)))
    }

    @Test
    fun `revision signal reflects never-revised and idle topics`() {
        // Score is additive: importance + progress gap + revision signal (no exam here).
        // COMPLETED never revised: gap 0 + never-revised signal 14
        assertEquals(8 + 0 + 14, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, lastRevised = null)))
        // LEARNING never revised: gap 18 + never-revised signal 10
        assertEquals(8 + 18 + 10, scored(snapshot("a", TopicStatus.LEARNING, importance = Importance.LOW, lastRevised = null)))
        // NOT_STARTED has gap 25 but no revision signal until a first revision exists
        assertEquals(8 + 25 + 0, scored(snapshot("a", TopicStatus.NOT_STARTED, importance = Importance.LOW, lastRevised = null)))
        // COMPLETED idle 15 days: gap 0 + idle signal 6
        assertEquals(8 + 0 + 6, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, lastRevised = "2026-10-03")))
        // COMPLETED idle 30 days: gap 0 + idle signal 12
        assertEquals(8 + 0 + 12, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, lastRevised = "2026-09-18")))
        // COMPLETED revised 5 days ago: gap 0 + no revision signal yet
        assertEquals(8 + 0 + 0, scored(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, lastRevised = "2026-10-13")))
    }

    @Test
    fun `score never exceeds the documented component ceiling`() {
        val worst = StudyPriorityEngine.score(
            snapshot("a", TopicStatus.NOT_STARTED, importance = Importance.HIGH, difficulty = Difficulty.HARD, lastRevised = null, daysUntilExam = 0)
        )
        assertEquals(25 + 25 + 30, worst.score)
        assertTrue(worst.score in 0..100)

        val highestSignalled = StudyPriorityEngine.score(
            snapshot("b", TopicStatus.NEEDS_REVISION, importance = Importance.HIGH, daysUntilExam = 0)
        )
        assertEquals(25 + 10 + 30 + 20, highestSignalled.score)
        assertTrue(highestSignalled.score <= 100)
    }

    // --- Action derivation ---

    @Test
    fun `actions match status and revision need`() {
        assertEquals(StudyAction.STUDY, rec(snapshot("a", TopicStatus.NOT_STARTED)).action)
        assertEquals(StudyAction.CONTINUE, rec(snapshot("a", TopicStatus.LEARNING)).action)
        assertEquals(StudyAction.REVISE, rec(snapshot("a", TopicStatus.NEEDS_REVISION)).action)
        assertEquals(
            StudyAction.REVISE,
            rec(snapshot("a", TopicStatus.COMPLETED, lastRevised = "2026-09-18")).action
        )
        assertEquals(
            StudyAction.CONTINUE,
            rec(snapshot("a", TopicStatus.COMPLETED, lastRevised = TODAY)).action
        )
    }

    @Test
    fun `priority level buckets follow score thresholds`() {
        assertEquals(PriorityLevel.HIGH, rec(snapshot("a", TopicStatus.NEEDS_REVISION, importance = Importance.HIGH, daysUntilExam = 0)).level)
        assertEquals(PriorityLevel.LOW, rec(snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW)).level)
    }

    // --- Deterministic ordering ---

    @Test
    fun `ordering prefers near exams then importance then id`() {
        val recommendations = listOf(
            recommendation("b", score = 50, days = null, importance = Importance.LOW),
            recommendation("a", score = 50, days = null, importance = Importance.HIGH),
            recommendation("c", score = 50, days = 5, importance = Importance.LOW)
        )
        val ordered = recommendations.sortedWith(StudyPriorityEngine.recommendationOrder())
        assertEquals(listOf("c", "a", "b"), ordered.map { it.topicId })
    }

    @Test
    fun `ordering falls back to topic id for full ties`() {
        val recommendations = listOf(
            recommendation("z", score = 40, days = null, importance = Importance.MEDIUM),
            recommendation("a", score = 40, days = null, importance = Importance.MEDIUM)
        )
        val ordered = recommendations.sortedWith(StudyPriorityEngine.recommendationOrder())
        assertEquals(listOf("a", "z"), ordered.map { it.topicId })
    }

    // --- Today's Plan selection ---

    @Test
    fun `plan excludes completed topics that do not need revision`() {
        val recommendations = listOf(
            recommendation("done", score = 90, status = TopicStatus.COMPLETED, action = StudyAction.CONTINUE),
            recommendation("revise", score = 80, status = TopicStatus.NEEDS_REVISION, action = StudyAction.REVISE),
            recommendation("start", score = 70, status = TopicStatus.NOT_STARTED, action = StudyAction.STUDY)
        )
        val plan = StudyPriorityEngine.selectTodayPlan(recommendations)
        assertEquals(listOf("revise", "start"), plan.map { it.topicId })
    }

    @Test
    fun `plan honors the item cap and removes duplicates`() {
        val recommendations = listOf(
            recommendation("a", score = 90),
            recommendation("a", score = 90),
            recommendation("b", score = 80),
            recommendation("c", score = 70)
        )
        val plan = StudyPriorityEngine.selectTodayPlan(recommendations, TodayPlanConfig(maxItems = 2))
        assertEquals(listOf("a", "b"), plan.map { it.topicId })
    }

    @Test
    fun `plan respects an optional minutes budget and keeps at least one item`() {
        val recommendations = listOf(
            recommendation("a", score = 90, minutes = 50),
            recommendation("b", score = 80, minutes = 40),
            recommendation("c", score = 70, minutes = 30)
        )
        val plan = StudyPriorityEngine.selectTodayPlan(
            recommendations,
            TodayPlanConfig(maxItems = 5, dailyMinutesTarget = 60)
        )
        assertEquals(listOf("a"), plan.map { it.topicId })
    }

    @Test
    fun `empty input yields empty recommendations and empty plan`() {
        assertTrue(StudyPriorityEngine.buildRecommendations(emptyList(), emptyList(), todayIso = TODAY).isEmpty())
        assertTrue(StudyPriorityEngine.selectTodayPlan(emptyList()).isEmpty())
    }

    // --- Subject/Exam integration ---

    @Test
    fun `buildRecommendations uses the nearest upcoming exam for a subject`() {
        val topic = topicModel("t1", "s1", TopicStatus.COMPLETED, Importance.LOW, lastRevisedAt = "2026-10-18T09:00:00Z")
        val subjects = listOf(subjectModel("s1", topic))
        val exams = listOf(
            examItem("e_far", "s1", "2026-11-07"),
            examItem("e_near", "s1", "2026-10-20")
        )

        val recommendations = StudyPriorityEngine.buildRecommendations(subjects, exams, todayIso = TODAY)

        assertEquals(1, recommendations.size)
        assertEquals(2, recommendations.first().daysUntilExam)
        assertEquals(8 + 28, recommendations.first().score)
    }

    @Test
    fun `buildRecommendations ignores past exams for urgency`() {
        val topic = topicModel("t1", "s1", TopicStatus.NOT_STARTED, Importance.MEDIUM, lastRevisedAt = null)
        val exams = listOf(examItem("e_past", "s1", "2026-10-01"))

        val recommendations = StudyPriorityEngine.buildRecommendations(listOf(subjectModel("s1", topic)), exams, todayIso = TODAY)

        assertEquals(null, recommendations.first().daysUntilExam)
        assertEquals(15 + 25, recommendations.first().score)
    }

    // --- Revision events are the source of truth (matching Phase 6) ---

    @Test
    fun `engine derives revision recency from revision events not the denormalized field`() {
        val staleTopic = topicModel("t1", "s1", TopicStatus.COMPLETED, Importance.LOW, lastRevisedAt = null)
        val subjects = listOf(subjectModel("s1", staleTopic))

        val recentlyRevised = StudyPriorityEngine.buildRecommendations(
            subjects = subjects,
            upcomingExams = emptyList(),
            revisionEventsByTopic = mapOf("t1" to listOf(revisionEvent("t1", "2026-10-18T09:00:00Z", 1))),
            todayIso = TODAY
        ).first()
        // A recent event clears the revision signal even though the topic field is null.
        assertEquals(8, recentlyRevised.score)
        assertEquals(TODAY, recentlyRevised.lastRevisedIsoDate)
        assertEquals(1, recentlyRevised.revisionCount)

        val longIdle = StudyPriorityEngine.buildRecommendations(
            subjects = subjects,
            upcomingExams = emptyList(),
            revisionEventsByTopic = mapOf("t1" to listOf(revisionEvent("t1", "2026-09-18T09:00:00Z", 1))),
            todayIso = TODAY
        ).first()
        // 30 days idle -> idle signal 12.
        assertEquals(8 + 12, longIdle.score)
    }

    @Test
    fun `a newer revision event wins over an older topic revision field`() {
        val topic = topicModel("t1", "s1", TopicStatus.COMPLETED, Importance.LOW, lastRevisedAt = "2026-09-18T09:00:00Z")
        val recommendation = StudyPriorityEngine.buildRecommendations(
            subjects = listOf(subjectModel("s1", topic)),
            upcomingExams = emptyList(),
            revisionEventsByTopic = mapOf("t1" to listOf(revisionEvent("t1", "2026-10-18T09:00:00Z", 1))),
            todayIso = TODAY
        ).first()
        assertEquals(8, recommendation.score)
    }

    // --- Repository wiring ---

    @Test
    fun `mock repository recomputes the plan when a topic is flagged and revised`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val target = "t_java_u2_5" // seeded NOT_STARTED topic
        val planTopicIds = { repository.todayStudyPlan.value.map { it.topicId } }

        assertEquals(StudyAction.STUDY, repository.studyRecommendations.value.first { it.topicId == target }.action)
        assertTrue(target in planTopicIds())

        repository.updateTopicStatus(target, TopicStatus.NEEDS_REVISION)
        assertEquals(StudyAction.REVISE, repository.studyRecommendations.value.first { it.topicId == target }.action)
        assertTrue(target in planTopicIds())

        repository.markTopicRevised(target)
        assertEquals(StudyAction.CONTINUE, repository.studyRecommendations.value.first { it.topicId == target }.action)
        assertTrue(target !in planTopicIds())
    }

    @Test
    fun `mock repository plan tracks a daily minutes budget and mirrors getTodayStudyItems`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        repository.setDailyStudyMinutesTarget(1)
        assertTrue(repository.todayStudyPlan.value.isNotEmpty())

        repository.setDailyStudyMinutesTarget(null)
        assertTrue(repository.todayStudyPlan.value.isNotEmpty())
        assertEquals(
            repository.todayStudyPlan.value.map { it.topicId },
            repository.getTodayStudyItems().map { it.topicId }
        )
    }

    @Test
    fun `mock repository recomputes exam urgency when exams change`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val javaTopic = "t_java_u2_5" // seeded Java topic with a seeded Java exam

        assertTrue(repository.studyRecommendations.value.first { it.topicId == javaTopic }.daysUntilExam != null)

        repository.deleteExam("e1") // removes the only seeded Java exam
        assertEquals(null, repository.studyRecommendations.value.first { it.topicId == javaTopic }.daysUntilExam)

        val examIso = isoPlusDays(StudyDateUtils.todayIso(), 5)
        repository.createExam("s_java", "New Exam", examIso, "", null)
        assertEquals(5, repository.studyRecommendations.value.first { it.topicId == javaTopic }.daysUntilExam)
    }

    @Test
    fun `mock repository plan never repeats a topic`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val topicIds = repository.todayStudyPlan.value.map { it.topicId }
        assertEquals(topicIds.size, topicIds.toSet().size)
        assertTrue(topicIds.size <= StudyPriorityEngine.DEFAULT_PLAN_MAX_ITEMS)
    }

    @Test
    fun `study view model surfaces engine scores instead of stored placeholders`() {
        val repository = MockStudyVaultRepository.createIsolated()
        val viewModel = StudyViewModel(repository)
        val subscription = CoroutineScope(Dispatchers.Main).launch { viewModel.studyItems.collect {} }

        val items = viewModel.studyItems.value
        assertTrue(items.isNotEmpty())
        assertTrue("Priority tab never lists completed topics", items.none { it.topicStatus == TopicStatus.COMPLETED })

        val engineScores = repository.studyRecommendations.value.associate { it.topicId to it.score }
        items.forEach { item -> assertEquals(engineScores[item.topicId], item.priorityScore) }

        subscription.cancel()
    }

    // --- Recommendation explanations ---

    @Test
    fun `reasons explain the drivers behind a recommendation`() {
        val recommendation = StudyPriorityEngine.score(
            snapshot(
                "a",
                TopicStatus.NEEDS_REVISION,
                importance = Importance.HIGH,
                lastRevised = null,
                daysUntilExam = 0
            ),
            TODAY
        )
        assertTrue("Flagged for revision" in recommendation.reasons)
        assertTrue("High importance" in recommendation.reasons)
        assertTrue(recommendation.reasons.any { it.endsWith("today") })
    }

    @Test
    fun `reasons omit high importance and exams that do not apply`() {
        val recommendation = StudyPriorityEngine.score(
            snapshot("a", TopicStatus.LEARNING, importance = Importance.LOW, lastRevised = null),
            TODAY
        )
        assertTrue("In progress" in recommendation.reasons)
        assertTrue("Never revised" in recommendation.reasons)
        assertTrue(recommendation.reasons.none { it == "High importance" })
        assertTrue(recommendation.reasons.none { it.contains("days") || it.endsWith("today") })
    }

    @Test
    fun `reasons report idle revision age`() {
        val recommendation = StudyPriorityEngine.score(
            snapshot("a", TopicStatus.COMPLETED, importance = Importance.LOW, lastRevised = "2026-09-18"),
            TODAY
        )
        assertTrue("Not revised in 30 days" in recommendation.reasons)
    }

    // --- Test fixtures ---

    private fun scored(snapshot: TopicStudySnapshot): Int = StudyPriorityEngine.score(snapshot, TODAY).score

    private fun rec(snapshot: TopicStudySnapshot): StudyRecommendation = StudyPriorityEngine.score(snapshot, TODAY)

    private fun snapshot(
        id: String,
        status: TopicStatus,
        importance: Importance = Importance.MEDIUM,
        difficulty: Difficulty = Difficulty.MEDIUM,
        lastRevised: String? = TODAY,
        daysUntilExam: Int? = null,
        revisionCount: Int = 0
    ) = TopicStudySnapshot(
        topicId = id,
        subjectId = "s",
        unitId = "u",
        topicName = "Topic $id",
        subjectName = "Subject",
        unitName = "Unit",
        iconKey = "book",
        status = status,
        importance = importance,
        difficulty = difficulty,
        revisionCount = revisionCount,
        lastRevisedIsoDate = lastRevised,
        daysUntilExam = daysUntilExam,
        examDateDisplay = null,
        examName = null
    )

    private fun recommendation(
        id: String,
        score: Int,
        days: Int? = null,
        importance: Importance = Importance.MEDIUM,
        status: TopicStatus = TopicStatus.NOT_STARTED,
        action: StudyAction = StudyAction.STUDY,
        minutes: Int = 30
    ) = StudyRecommendation(
        topicId = id,
        subjectId = "s",
        unitId = "u",
        topicName = "Topic $id",
        subjectName = "Subject",
        unitName = "Unit",
        iconKey = "book",
        status = status,
        importance = importance,
        score = score,
        level = when {
            score >= 75 -> PriorityLevel.HIGH
            score >= 50 -> PriorityLevel.MEDIUM
            else -> PriorityLevel.LOW
        },
        action = action,
        reasons = emptyList(),
        estimatedMinutes = minutes,
        daysUntilExam = days,
        examDateDisplay = null,
        examName = null,
        lastRevisedIsoDate = TODAY,
        revisionCount = 0
    )

    private fun topicModel(
        id: String,
        subjectId: String,
        status: TopicStatus,
        importance: Importance,
        lastRevisedAt: String?
    ) = TopicModel(
        id = id,
        unitId = "${subjectId}_u1",
        subjectId = subjectId,
        title = "Topic $id",
        status = status,
        difficulty = Difficulty.MEDIUM,
        importance = importance,
        lastRevisedAt = lastRevisedAt
    )

    private fun subjectModel(id: String, vararg topics: TopicModel) = Subject(
        id = id,
        name = "Subject $id",
        code = "C$id",
        iconKey = "book",
        preparationPercent = 0,
        unitsCount = 1,
        topicsCount = topics.size,
        units = listOf(
            UnitModel(
                id = "${id}_u1",
                subjectId = id,
                unitNumber = 1,
                title = "Unit 1",
                preparationPercent = 0,
                topics = topics.toList()
            )
        )
    )

    // daysRemaining is intentionally wrong so the test proves the engine derives urgency from isoDate.
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

    private fun revisionEvent(topicId: String, revisedAt: String, number: Int) = RevisionEvent(
        id = "r_${topicId}_$number",
        topicId = topicId,
        subjectId = "s1",
        unitId = "s1_u1",
        topicName = "Topic $topicId",
        subjectName = "Subject s1",
        unitName = "Unit 1",
        revisionNumber = number,
        previousStatus = TopicStatus.COMPLETED,
        newStatus = TopicStatus.COMPLETED,
        revisedAt = revisedAt
    )

    private fun isoPlusDays(baseIso: String, days: Int): String {
        val format = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val date = format.parse(baseIso)!!
        return java.util.Calendar.getInstance().apply {
            time = date
            add(java.util.Calendar.DAY_OF_MONTH, days)
        }.let { format.format(it.time) }
    }

    private companion object {
        const val TODAY = "2026-10-18"
    }
}

/** Installs a test Main dispatcher before any coroutine (e.g. viewModelScope) touches it. */
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
