package com.example

import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.StudyVaultRepository
import com.example.ui.screens.unitdetail.UnitDetailViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Topic creation from the unit screen: the ViewModel must expose an in-flight state so the
 * dialog can disable duplicate saves, must surface the real repository error, must refresh the
 * on-screen unit so the new topic and its count appear immediately, and must only signal
 * success (via the callback) after the insert actually persisted.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnitTopicCreationTest {

    /** Delegating double letting a single createTopic be held in flight or forced to fail. */
    private class GatedVaultRepository(
        private val delegate: StudyVaultRepository,
        private val topicGate: CompletableDeferred<Unit>? = null,
        private val forcedFailure: Throwable? = null
    ) : StudyVaultRepository by delegate {
        override suspend fun createTopic(
            subjectId: String,
            unitId: String,
            title: String
        ): Result<com.example.data.model.TopicModel> {
            topicGate?.await()
            forcedFailure?.let { return Result.failure(it) }
            return delegate.createTopic(subjectId, unitId, title)
        }
    }

    @Test
    fun `successful creation refreshes the unit list and count without navigating away`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val viewModel = UnitDetailViewModel(repository)
            viewModel.loadUnit("s_java", "u_java_2")
            val beforeCount = viewModel.unit.value!!.topics.size

            var created = false
            viewModel.createTopic("Design Patterns") { created = true }

            val after = viewModel.unit.value!!
            assertEquals(beforeCount + 1, after.topics.size)
            assertTrue(after.topics.any { it.title == "Design Patterns" })
            assertNotNull(repository.getTopicById(after.topics.last().id))
            assertTrue("Success callback must fire on persisted creation", created)
            assertEquals("Added topic Design Patterns", viewModel.statusMessage.value)
            assertNull(viewModel.errorMessage.value)
            assertFalse(viewModel.isCreatingTopic.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `duplicate save is ignored while a request is in flight`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val gate = CompletableDeferred<Unit>()
            val gated = GatedVaultRepository(repository, topicGate = gate)
            val viewModel = UnitDetailViewModel(gated)
            viewModel.loadUnit("s_java", "u_java_2")
            val beforeCount = viewModel.unit.value!!.topics.size

            var createdCount = 0
            viewModel.createTopic("Concurrency") { createdCount++ }
            assertTrue("In-flight state must be exposed", viewModel.isCreatingTopic.value)

            // Second tap while the first is pending must be ignored.
            viewModel.createTopic("Concurrency") { createdCount++ }

            gate.complete(Unit)
            val after = viewModel.unit.value!!
            assertEquals(beforeCount + 1, after.topics.size)
            assertEquals(1, createdCount)
            assertFalse(viewModel.isCreatingTopic.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `failed creation surfaces the error, keeps the topic list unchanged, and does not signal success`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val gated = GatedVaultRepository(
                repository,
                forcedFailure = IllegalStateException("new row violates row-level security policy for table \"topics\"")
            )
            val viewModel = UnitDetailViewModel(gated)
            viewModel.loadUnit("s_java", "u_java_2")
            val beforeCount = viewModel.unit.value!!.topics.size

            var created = false
            viewModel.createTopic("Should Fail") { created = true }

            assertFalse("Failure must not signal success", created)
            assertEquals(beforeCount, viewModel.unit.value!!.topics.size)
            assertTrue(
                "Real repository error must reach the screen",
                viewModel.errorMessage.value?.contains("row-level security") == true
            )
            assertNull(viewModel.statusMessage.value)
            assertFalse(viewModel.isCreatingTopic.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `blank title fails at the repository and does not signal success`() = runTest {
        Dispatchers.setMain(UnconfinedTestDispatcher(testScheduler))
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val viewModel = UnitDetailViewModel(repository)
            viewModel.loadUnit("s_java", "u_java_2")
            val beforeCount = viewModel.unit.value!!.topics.size

            var created = false
            viewModel.createTopic("   ") { created = true }

            assertFalse(created)
            assertEquals(beforeCount, viewModel.unit.value!!.topics.size)
            assertNotNull(viewModel.errorMessage.value)
        } finally {
            Dispatchers.resetMain()
        }
    }
}
