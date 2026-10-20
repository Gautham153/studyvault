package com.example

import com.example.data.repository.MockStudyVaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Unit management: create/rename/delete operations flow through the repository, keep
 * dependent data safe (block deletion while topics or questions still reference the unit),
 * and refresh Today's Plan and dashboard-derived counts immediately.
 */
class UnitManagementTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `creating a unit appends it with the next order index and updates counts immediately`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        val before = repository.getSubjectById("s_english")!!
        assertEquals(3, before.units.size)

        val created = repository.createUnit("s_english", "Technical Communication", null).getOrThrow()

        assertEquals("Technical Communication", created.title)
        assertEquals(4, created.unitNumber)

        val after = repository.getSubjectById("s_english")!!
        assertEquals(4, after.units.size)
        assertEquals(4, after.unitsCount)
        assertTrue(after.units.any { it.id == created.id })
    }

    @Test
    fun `creating a unit with an explicit order index places it deterministically`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        val created = repository.createUnit("s_english", "Bridge Course", 2).getOrThrow()

        assertEquals(2, created.unitNumber)
        val unitNumbers = repository.getSubjectById("s_english")!!.units.map { it.unitNumber }
        assertEquals(listOf(1, 2, 2, 3), unitNumbers.sorted())
    }

    @Test
    fun `blank unit names and unknown subjects are rejected`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        assertTrue(repository.createUnit("s_english", "   ", null).isFailure)
        assertTrue(repository.createUnit("s_missing", "Unit 1", null).isFailure)
        assertEquals(3, repository.getSubjectById("s_english")!!.units.size)
    }

    @Test
    fun `subject with no units stays usable for questions and exams`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        repository.addSubject("Ethics", "HU201", "book")
        val subject = repository.subjects.value.first { it.name == "Ethics" }

        for (unit in subject.units.toList()) {
            assertTrue(repository.deleteUnit(subject.id, unit.id).isSuccess)
        }

        val reloaded = repository.getSubjectById(subject.id)!!
        assertTrue(reloaded.units.isEmpty())
        assertEquals(0, reloaded.unitsCount)

        val question = repository.addQuestion(
            subjectId = subject.id,
            unitId = null,
            topicId = null,
            questionText = "What is professional code ethics?",
            marks = 5
        ).getOrThrow()
        assertEquals(subject.id, question.subjectId)

        val exam = repository.createExam(subject.id, "Internal Test", "Dec 1, 2026").getOrThrow()
        assertEquals(subject.id, exam.subjectId)
        assertNotNull(repository.getSubjectById(subject.id))
    }

    @Test
    fun `updating a unit renames it in place without disturbing siblings`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        val renamed = repository.updateUnit("s_english", "u_eng_1", "Report Writing", null).getOrThrow()

        assertEquals("Report Writing", renamed.title)
        val unit = repository.getUnitById("s_english", "u_eng_1")
        assertEquals("Report Writing", unit?.title)
        assertEquals(1, unit?.unitNumber)
        assertEquals(3, repository.getSubjectById("s_english")!!.units.size)
    }

    @Test
    fun `updating unknown units or blank names fails`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        assertTrue(repository.updateUnit("s_english", "u_missing", "New", null).isFailure)
        assertTrue(repository.updateUnit("s_missing", "u_eng_1", "New", null).isFailure)
        assertTrue(repository.updateUnit("s_english", "u_eng_1", "  ", null).isFailure)
        assertEquals("Technical Writing & Reports", repository.getUnitById("s_english", "u_eng_1")?.title)
    }

    @Test
    fun `deleting a unit is blocked while it still has topics or questions`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        val blocked = repository.deleteUnit("s_java", "u_java_2")

        assertTrue(blocked.isFailure)
        val message = blocked.exceptionOrNull()?.message.orEmpty()
        assertTrue("Message must explain the blocking topics", message.contains("topic(s)"))
        assertTrue("Message must explain the blocking questions", message.contains("question(s)"))
        assertNotNull(repository.getUnitById("s_java", "u_java_2"))
        assertTrue(repository.getQuestionsForSubject("s_java").isNotEmpty())
    }

    @Test
    fun `question unit associations survive a failed unit deletion untouched`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val questionsBefore = repository.getQuestionsForSubject("s_java").map { it.id }

        repository.deleteUnit("s_java", "u_java_2")

        assertEquals(questionsBefore, repository.getQuestionsForSubject("s_java").map { it.id })
        assertTrue(repository.getQuestionsForSubject("s_java").all { it.unitId == "u_java_2" })
    }

    @Test
    fun `deleting an empty unit succeeds and refreshes dashboard and plan state`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val created = repository.createUnit("s_english", "Spare Unit", null).getOrThrow()
        val planBefore = repository.todayStudyPlan.value.map { it.recommendationKey }
        val progressBefore = repository.overallProgress.value

        val result = repository.deleteUnit("s_english", created.id)

        assertTrue(result.isSuccess)
        val subject = repository.getSubjectById("s_english")!!
        assertTrue(subject.units.none { it.id == created.id })
        assertEquals(3, subject.units.size)
        assertEquals(3, subject.unitsCount)
        // Dashboard and plan state were recomputed and stay well-formed.
        assertEquals(progressBefore.subjectsCount, repository.overallProgress.value.subjectsCount)
        assertEquals(planBefore, repository.todayStudyPlan.value.map { it.recommendationKey })
    }

    @Test
    fun `deleting unknown units fails and leaves counts alone`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        assertTrue(repository.deleteUnit("s_english", "u_missing").isFailure)
        assertTrue(repository.deleteUnit("s_missing", "u_eng_1").isFailure)
        assertEquals(3, repository.getSubjectById("s_english")!!.units.size)
        assertEquals(3, repository.getSubjectById("s_english")!!.unitsCount)
    }
}
