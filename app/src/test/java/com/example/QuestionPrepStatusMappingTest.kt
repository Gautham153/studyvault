package com.example

import com.example.data.model.Importance
import com.example.data.model.QuestionItem
import com.example.data.model.Subject
import com.example.data.model.TopicStatus
import com.example.data.repository.StudyAction
import com.example.data.repository.StudyPriorityEngine
import com.example.data.repository.TodayPlanConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Question form status mapping regression: the question form's exact statuses
 * (Not Prepared, Learning, Prepared, High Priority) map onto the engine's preparation scale
 * correctly — unfinished questions stay eligible, Prepared is excluded, and a High Priority
 * question is never lost by being misread as completed.
 */
class QuestionPrepStatusMappingTest {

    private fun question(
        status: String,
        importance: Importance = Importance.HIGH,
        marks: Int = 5
    ) = QuestionItem(
        id = "q_${status.replace(' ', '_')}",
        questionText = "Sample question",
        marks = marks,
        subjectId = "s1",
        subjectName = "Sample",
        status = status,
        importance = importance
    )

    private fun subjectWith(vararg questions: QuestionItem) = Subject(
        id = "s1",
        name = "Sample",
        code = "S1",
        iconKey = "book",
        preparationPercent = 0,
        unitsCount = 1,
        topicsCount = 0,
        questions = questions.toList()
    )

    private fun recommendationsFor(vararg questions: QuestionItem) =
        StudyPriorityEngine.buildQuestionRecommendations(listOf(subjectWith(*questions)), emptyList())

    @Test
    fun `not prepared questions are eligible with a not started preparation gap`() {
        val recs = recommendationsFor(question("Not Prepared"))

        assertEquals(1, recs.size)
        val rec = recs.first()
        assertEquals(TopicStatus.NOT_STARTED, rec.status)
        assertEquals(StudyAction.PRACTICE, rec.action)
        assertTrue(rec.reasons.contains("Not prepared yet"))
        // HIGH importance 25 + NOT_STARTED gap 25 + no exam 0 + marks 5 -> 10
        assertEquals(60, rec.score)
    }

    @Test
    fun `learning questions are eligible with a learning preparation gap`() {
        val recs = recommendationsFor(question("Learning"))

        assertEquals(1, recs.size)
        val rec = recs.first()
        assertEquals(TopicStatus.LEARNING, rec.status)
        assertEquals(StudyAction.PRACTICE, rec.action)
        assertTrue(rec.reasons.contains("In progress"))
        // HIGH importance 25 + LEARNING gap 18 + no exam 0 + marks 5 -> 10
        assertEquals(53, rec.score)
    }

    @Test
    fun `prepared questions are excluded from recommendations`() {
        val recs = recommendationsFor(question("Prepared"))

        assertTrue(
            "Prepared questions must never surface as recommendations",
            recs.none { it.questionId == question("Prepared").id }
        )
        assertTrue(recs.isEmpty())
    }

    @Test
    fun `high priority questions stay eligible and are never treated as completed`() {
        val recs = recommendationsFor(question("High Priority"))

        assertEquals(1, recs.size)
        val rec = recs.first()
        assertEquals(
            "High Priority must map to NEEDS_REVISION, not COMPLETED",
            TopicStatus.NEEDS_REVISION,
            rec.status
        )
        assertEquals(StudyAction.PRACTICE, rec.action)
        assertTrue(rec.reasons.contains("Flagged for revision"))
        // HIGH importance 25 + NEEDS_REVISION gap 10 + no exam 0 + marks 5 -> 10
        assertEquals(45, rec.score)

        // It also survives Today's Plan selection (the COMPLETED exclusion must not drop it).
        val plan = StudyPriorityEngine.selectTodayPlan(recs, TodayPlanConfig(maxItems = 5))
        assertTrue(plan.any { it.questionId == rec.questionId })
    }

    @Test
    fun `all four form statuses follow the documented mapping`() {
        val byStatus = recommendationsFor(
            question("Not Prepared"),
            question("Learning"),
            question("Prepared"),
            question("High Priority")
        ).associateBy { it.questionId }

        assertEquals(TopicStatus.NOT_STARTED, byStatus["q_Not_Prepared"]?.status)
        assertEquals(TopicStatus.LEARNING, byStatus["q_Learning"]?.status)
        assertEquals(TopicStatus.NEEDS_REVISION, byStatus["q_High_Priority"]?.status)
        assertTrue("Prepared must be absent from the mapping", !byStatus.containsKey("q_Prepared"))
    }
}
