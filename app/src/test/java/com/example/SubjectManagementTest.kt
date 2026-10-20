package com.example

import com.example.data.repository.MockStudyVaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Subject management: rename/edit and delete operations update the subjects flow immediately,
 * republish derived exam/dashboard/plan state, and never touch data belonging to other subjects.
 */
class SubjectManagementTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `renaming a subject updates the flow and republishes its exams with the new name`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        val updated = repository.updateSubject("s_dbms", "Databases", "CS202", "database").getOrThrow()

        assertEquals("Databases", updated.name)
        assertEquals("Databases", repository.getSubjectById("s_dbms")?.name)
        assertEquals(
            "Databases",
            repository.subjects.value.first { it.id == "s_dbms" }.name
        )

        val examNames = repository.upcomingExams.value
            .filter { it.subjectId == "s_dbms" }
            .map { it.subjectName }
        assertTrue("Fixture must have an upcoming exam", examNames.isNotEmpty())
        assertTrue("All exams must republish with the renamed subject", examNames.all { it == "Databases" })
    }

    @Test
    fun `blank names and unknown subjects are rejected without mutating state`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        assertTrue(repository.updateSubject("s_dbms", "   ", "CS202", "database").isFailure)
        assertTrue(repository.updateSubject("s_missing", "New Name", "X", "book").isFailure)
        assertEquals("DBMS", repository.getSubjectById("s_dbms")?.name)
        assertEquals(6, repository.subjects.value.size)
    }

    @Test
    fun `deleting a subject removes only its own data and refreshes dashboard and plan`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        val planKeysBefore = repository.todayStudyPlan.value.map { it.recommendationKey }.toSet()
        assertTrue("Fixture plan must contain java topics", planKeysBefore.isNotEmpty())
        assertTrue(repository.recentRevisions.value.any { it.subjectId == "s_java" })

        val result = repository.deleteSubject("s_java")

        assertTrue(result.isSuccess)
        assertNull(repository.getSubjectById("s_java"))
        assertTrue(repository.subjects.value.none { it.id == "s_java" })
        assertEquals(5, repository.subjects.value.size)
        assertEquals(5, repository.overallProgress.value.subjectsCount)

        assertTrue(repository.getQuestionsForSubject("s_java").isEmpty())
        assertTrue(repository.getNotesForSubject("s_java").isEmpty())
        assertTrue(repository.upcomingExams.value.none { it.subjectId == "s_java" })
        assertTrue(repository.pastExams.value.none { it.subjectId == "s_java" })
        assertTrue(repository.todayStudyPlan.value.none { it.subjectId == "s_java" })
        assertTrue(repository.allStudyRecommendations.value.none { it.subjectId == "s_java" })
        assertTrue(repository.recentRevisions.value.none { it.subjectId == "s_java" })

        // Other subjects and their data stay untouched.
        assertNotNull(repository.getSubjectById("s_dbms"))
        assertTrue(repository.subjects.value.first { it.id == "s_dbms" }.units.isNotEmpty())
        assertTrue(repository.upcomingExams.value.any { it.subjectId == "s_dbms" })
        assertTrue(repository.getQuestionsForSubject("s_java").isEmpty())
        assertTrue(repository.getQuestionsForSubject("s_dbms").isEmpty()) // fixture has no dbms questions
    }

    @Test
    fun `deleting an unknown subject fails and leaves state alone`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        assertTrue(repository.deleteSubject("s_missing").isFailure)
        assertEquals(6, repository.subjects.value.size)
        assertEquals(6, repository.overallProgress.value.subjectsCount)
    }
}
