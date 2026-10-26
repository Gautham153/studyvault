package com.example

import com.example.data.model.ExamItem
import com.example.data.model.RevisionEvent
import com.example.data.model.TopicModel
import com.example.data.model.TopicStatus
import com.example.data.remote.RevisionHistoryDto
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.RevisionHistoryLoader
import com.example.data.repository.StudyPlanning
import com.example.util.StudyDateUtils
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * Focused Phase 6 tests for topic preparation, revision tracking and exam classification.
 * Everything here is pure/in-memory, so no live Supabase connection is required.
 */
class Phase6StudyPlanningTest {

    // --- Topic preparation percentage -----------------------------------------

    @Test
    fun `preparation percent counts only completed topics`() {
        val topics = listOf(
            topic("t1", TopicStatus.COMPLETED),
            topic("t2", TopicStatus.LEARNING),
            topic("t3", TopicStatus.NOT_STARTED),
            topic("t4", TopicStatus.NEEDS_REVISION)
        )

        assertEquals("One of four completed topics is 25%", 25, StudyPlanning.preparationPercent(topics))
    }

    @Test
    fun `preparation percent is full when every topic is completed`() {
        val topics = listOf(
            topic("t1", TopicStatus.COMPLETED),
            topic("t2", TopicStatus.COMPLETED)
        )

        assertEquals(100, StudyPlanning.preparationPercent(topics))
    }

    @Test
    fun `preparation percent is zero when no topic is completed`() {
        val topics = listOf(
            topic("t1", TopicStatus.LEARNING),
            topic("t2", TopicStatus.NOT_STARTED)
        )

        assertEquals(0, StudyPlanning.preparationPercent(topics))
    }

    @Test
    fun `preparation percent is zero when no topics are tracked`() {
        assertEquals("No tracked topics must not report a fake 100%", 0, StudyPlanning.preparationPercent(emptyList<TopicModel>()))
        assertEquals(0, StudyPlanning.subjectsPreparationPercent(emptyList()))
    }

    // --- Revision event sorting, counts and empty history ---------------------

    @Test
    fun `revision events are sorted newest first`() {
        val events = listOf(
            revisionEvent("r1", number = 1, revisedAt = "2026-10-01T09:00:00Z"),
            revisionEvent("r2", number = 2, revisedAt = "2026-10-07T09:00:00Z"),
            revisionEvent("r3", number = 3, revisedAt = "2026-10-03T09:00:00Z")
        )

        val sorted = StudyPlanning.sortRevisionEventsNewestFirst(events)

        assertEquals(listOf("r2", "r3", "r1"), sorted.map { it.id })
    }

    @Test
    fun `sorting an empty revision history stays empty`() {
        assertTrue(StudyPlanning.sortRevisionEventsNewestFirst(emptyList()).isEmpty())
        assertTrue(StudyPlanning.recentRevisions(emptyList()).isEmpty())
    }

    @Test
    fun `recent revisions are capped and newest first`() {
        val events = (1..8).map { revisionEvent("r$it", number = it, revisedAt = "2026-10-0${it}T09:00:00Z") }

        val recent = StudyPlanning.recentRevisions(events)

        assertEquals(StudyPlanning.RECENT_REVISION_LIMIT, recent.size)
        assertEquals("r8", recent.first().id)
    }

    @Test
    fun `a topic with no history reports an empty list`() {
        val repo = MockStudyVaultRepository.createIsolated()

        assertTrue(repo.getRevisionEventsForTopic("t_java_u2_5").isEmpty())
    }

    @Test
    fun `marking a topic revised records a numbered event and a revision count`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()
        val topicId = "t_java_u2_1" // seeded with two revisions
        val before = repo.getTopicById(topicId)
        assertNotNull(before)

        val result = repo.markTopicRevised(topicId, notes = "  reviewed  ")

        assertTrue(result.isSuccess)
        val event = result.getOrThrow()
        assertEquals("Seeded two revisions, so this is the third", 3, event.revisionNumber)
        assertEquals("reviewed", event.notes)

        val after = repo.getTopicById(topicId)
        assertEquals(3, after?.revisionCount)
        assertNotNull("Last revised timestamp must be set", after?.lastRevisedAt)
        assertEquals(3, repo.getRevisionEventsForTopic(topicId).size)
    }

    // --- Exam classification and ordering -------------------------------------

    @Test
    fun `exams dated today are upcoming and exams before today are past`() {
        val schedule = StudyPlanning.classifyExams(
            listOf(
                exam("today", daysRemaining = 0, isoDate = "2026-10-18"),
                exam("future", daysRemaining = 5, isoDate = "2026-10-23"),
                exam("past", daysRemaining = -3, isoDate = "2026-10-15")
            )
        )

        assertEquals("Today counts as upcoming", listOf("today", "future"), schedule.upcoming.map { it.id })
        assertEquals(listOf("past"), schedule.past.map { it.id })
    }

    @Test
    fun `upcoming exams are soonest first and past exams most recent first`() {
        val schedule = StudyPlanning.classifyExams(
            listOf(
                exam("f10", daysRemaining = 10, isoDate = "2026-10-28"),
                exam("f2", daysRemaining = 2, isoDate = "2026-10-20"),
                exam("p1", daysRemaining = -1, isoDate = "2026-10-17"),
                exam("p9", daysRemaining = -9, isoDate = "2026-10-09")
            )
        )

        assertEquals(listOf("f2", "f10"), schedule.upcoming.map { it.id })
        assertEquals(listOf("p1", "p9"), schedule.past.map { it.id })
    }

    @Test
    fun `classifying an empty exam list yields two empty sections`() {
        val schedule = StudyPlanning.classifyExams(emptyList())

        assertTrue(schedule.upcoming.isEmpty())
        assertTrue(schedule.past.isEmpty())
    }

    @Test
    fun `day classification treats today as zero and yesterday as past`() {
        val now = System.currentTimeMillis()

        assertEquals(0, StudyDateUtils.daysFromToday(isoDaysFrom(now, 0), now))
        assertEquals(2, StudyDateUtils.daysFromToday(isoDaysFrom(now, 2), now))
        assertEquals(-3, StudyDateUtils.daysFromToday(isoDaysFrom(now, -3), now))
        assertFalse("An exam dated today is not past", StudyDateUtils.isPastDate(isoDaysFrom(now, 0), now))
        assertTrue("An exam dated yesterday is past", StudyDateUtils.isPastDate(isoDaysFrom(now, -1), now))
    }

    @Test
    fun `exam created for today lands in upcoming and a past exam in past`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()

        val todayResult = repo.createExam(subjectId = "s_java", name = "Today Quiz", examDate = StudyDateUtils.todayIso())
        assertTrue(todayResult.isSuccess)
        val todayExam = todayResult.getOrThrow()
        assertEquals(0, todayExam.daysRemaining)
        assertTrue("Today's exam is upcoming", repo.upcomingExams.value.any { it.id == todayExam.id })
        assertFalse("Today's exam is not past", repo.pastExams.value.any { it.id == todayExam.id })

        val pastDate = isoDaysFrom(System.currentTimeMillis(), -1)
        val pastResult = repo.createExam(subjectId = "s_java", name = "Past Quiz", examDate = pastDate)
        assertTrue(pastResult.isSuccess)
        val pastExam = pastResult.getOrThrow()
        assertTrue("A past exam is classified as past", repo.pastExams.value.any { it.id == pastExam.id })
        assertFalse("A past exam is not upcoming", repo.upcomingExams.value.any { it.id == pastExam.id })
    }

    @Test
    fun `exam creation rejects an invalid date`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()

        val result = repo.createExam(subjectId = "s_java", name = "Bad Date", examDate = "not-a-date")

        assertTrue(result.isFailure)
        assertTrue(
            "Error should explain the date is invalid",
            result.exceptionOrNull()?.message?.contains("Invalid exam date", ignoreCase = true) == true
        )
    }

    // --- Topic status transitions and completion timestamps --------------------

    @Test
    fun `completing a topic stamps completedAt and reopening it clears it`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()
        val topicId = "t_java_u2_3" // starts as LEARNING

        assertTrue(repo.updateTopicStatus(topicId, TopicStatus.COMPLETED).isSuccess)
        val completed = repo.getTopicById(topicId)
        assertEquals(TopicStatus.COMPLETED, completed?.status)
        assertNotNull("Reaching COMPLETED must record a completion timestamp", completed?.completedAt)

        assertTrue(repo.updateTopicStatus(topicId, TopicStatus.LEARNING).isSuccess)
        val reopened = repo.getTopicById(topicId)
        assertEquals(TopicStatus.LEARNING, reopened?.status)
        assertNull("Leaving COMPLETED must clear the completion timestamp", reopened?.completedAt)
    }

    @Test
    fun `updating the status of an unknown topic fails`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()

        val result = repo.updateTopicStatus("t_does_not_exist", TopicStatus.COMPLETED)

        assertTrue(result.isFailure)
    }

    @Test
    fun `revising a needs-revision topic returns it to completed`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()
        val topicId = "t_java_u2_3"

        repo.updateTopicStatus(topicId, TopicStatus.NEEDS_REVISION)
        assertNull("NEEDS_REVISION is not a completed state", repo.getTopicById(topicId)?.completedAt)

        val result = repo.markTopicRevised(topicId, notes = "quick recap")
        assertTrue(result.isSuccess)
        val event = result.getOrThrow()
        assertEquals(TopicStatus.NEEDS_REVISION, event.previousStatus)
        assertEquals(TopicStatus.COMPLETED, event.newStatus)

        val after = repo.getTopicById(topicId)
        assertEquals(TopicStatus.COMPLETED, after?.status)
        assertNotNull("Revising into COMPLETED must stamp a completion timestamp", after?.completedAt)
    }

    // --- Revision-history load failure handling -------------------------------

    @Test
    fun `a failed revision history load is a failure not a confirmed empty list`() = runTest {
        val result = RevisionHistoryLoader.load {
            throw IllegalStateException("relation \"revision_history\" does not exist")
        }

        assertTrue("A failed load must report failure", result.isFailure)
        assertFalse("A failed load must not report success", result.isSuccess)
        assertNull("A failed load must expose no list", result.getOrNull())
        assertTrue(
            "Failure must carry the underlying error message",
            result.exceptionOrNull()?.message?.contains("does not exist") == true
        )
    }

    @Test
    fun `a successful empty revision history is a confirmed empty list`() = runTest {
        val result = RevisionHistoryLoader.load { emptyList() }

        assertTrue("A valid empty history is a successful load", result.isSuccess)
        assertEquals(emptyList<RevisionHistoryDto>(), result.getOrThrow())
    }

    @Test
    fun `a successful revision history load returns its records`() = runTest {
        val dto = RevisionHistoryDto(
            id = "r1",
            userId = "u1",
            topicId = "t1",
            revisionNumber = 1,
            newStatus = "COMPLETED"
        )

        val result = RevisionHistoryLoader.load { listOf(dto) }

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrThrow().size)
    }

    @Test
    fun `RevisionHistoryDto encodes revision_number even when revisionNumber is 1 and encodeDefaults is false`() {
        val dto = RevisionHistoryDto(
            id = "r1",
            userId = "u1",
            topicId = "t1",
            revisionNumber = 1,
            newStatus = "COMPLETED"
        )
        val json = kotlinx.serialization.json.Json { encodeDefaults = false }
        val encoded = json.encodeToString(RevisionHistoryDto.serializer(), dto)
        assertTrue(
            "revision_number must be explicitly serialized so database not-null constraint is satisfied",
            encoded.contains("\"revision_number\":1")
        )
    }

    // --- Test fixtures --------------------------------------------------------

    private fun topic(id: String, status: TopicStatus): TopicModel =
        TopicModel(id = id, unitId = "u1", subjectId = "s1", title = id, status = status)

    private fun revisionEvent(id: String, number: Int, revisedAt: String) =
        RevisionEvent(
            id = id,
            topicId = "t1",
            subjectId = "s1",
            unitId = "u1",
            topicName = "Topic",
            subjectName = "Subject",
            unitName = "Unit",
            revisionNumber = number,
            previousStatus = TopicStatus.LEARNING,
            newStatus = TopicStatus.COMPLETED,
            revisedAt = revisedAt
        )

    private fun exam(id: String, daysRemaining: Int, isoDate: String): ExamItem =
        ExamItem(
            id = id,
            subjectId = "s1",
            subjectName = "Subject",
            iconKey = "book",
            examDate = isoDate,
            daysRemaining = daysRemaining,
            preparationPercent = 0,
            isoDate = isoDate
        )

    private fun isoDaysFrom(baseMillis: Long, days: Int): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = baseMillis
        calendar.add(Calendar.DAY_OF_YEAR, days)
        return StudyDateUtils.todayIso(calendar.timeInMillis)
    }
}
