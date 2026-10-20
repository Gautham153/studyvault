package com.example

import com.example.data.model.TopicStatus
import com.example.data.repository.MockStudyVaultRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Topic management: creating a topic through the repository appends it to the owning unit,
 * validates its subject/unit ownership, and refreshes derived dashboard/plan state immediately.
 */
class TopicManagementTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `creating a topic appends it to the unit and updates counts immediately`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val before = repository.getUnitById("s_java", "u_java_2")!!
        val beforeCount = before.topics.size

        val created = repository.createTopic("s_java", "u_java_2", "Multithreading Basics").getOrThrow()

        assertEquals("Multithreading Basics", created.title)
        assertEquals("u_java_2", created.unitId)
        assertEquals("s_java", created.subjectId)
        assertEquals(TopicStatus.NOT_STARTED, created.status)

        val after = repository.getUnitById("s_java", "u_java_2")!!
        assertEquals(beforeCount + 1, after.topics.size)
        assertTrue(after.topics.any { it.id == created.id })
        assertNotNull(repository.getTopicById(created.id))
    }

    @Test
    fun `creating a topic trims whitespace and rejects blank titles`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val beforeCount = repository.getUnitById("s_java", "u_java_2")!!.topics.size

        val created = repository.createTopic("s_java", "u_java_2", "  Collections Framework  ").getOrThrow()
        assertEquals("Collections Framework", created.title)

        assertTrue(repository.createTopic("s_java", "u_java_2", "   ").isFailure)
        assertEquals(
            beforeCount + 1,
            repository.getUnitById("s_java", "u_java_2")!!.topics.size
        )
    }

    @Test
    fun `creating a topic rejects unknown subjects and units`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        assertTrue(repository.createTopic("s_missing", "u_java_2", "Topic").isFailure)
        assertTrue(repository.createTopic("s_java", "u_missing", "Topic").isFailure)
    }

    @Test
    fun `creating a topic rejects a unit that belongs to another subject`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()

        val result = repository.createTopic("s_dbms", "u_java_2", "Cross-subject topic")

        assertTrue(result.isFailure)
        assertTrue(repository.getUnitById("s_java", "u_java_2")!!.topics.none { it.title == "Cross-subject topic" })
    }

    @Test
    fun `creating a topic refreshes dashboard counts and leaves the plan well-formed`() = runTest {
        val repository = MockStudyVaultRepository.createIsolated()
        val totalBefore = repository.overallProgress.value.totalTopics

        val created = repository.createTopic("s_java", "u_java_2", "Streams API").getOrThrow()

        assertEquals(totalBefore + 1, repository.overallProgress.value.totalTopics)
        assertTrue(repository.studyRecommendations.value.any { it.topicId == created.id })
        assertTrue(repository.todayStudyPlan.value.size <= 5)
    }
}
