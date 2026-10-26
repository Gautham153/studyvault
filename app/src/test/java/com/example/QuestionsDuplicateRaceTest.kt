package com.example

import android.os.Looper
import com.example.data.model.Importance
import com.example.data.model.QuestionItem
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.StudyVaultRepository
import com.example.ui.screens.questions.QuestionsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Test double mirroring the production race: the repository publishes the new question
 * through its `subjects` flow *before* addQuestion returns (the Supabase repository
 * reassembles domain state inside the insert path), so the ViewModel's subjects collector
 * can apply a refresh containing the question while the add-success callback is pending.
 */
private class RacyRefreshRepository(
    private val inner: MockStudyVaultRepository
) : StudyVaultRepository by inner {

    var createdQuestion: QuestionItem? = null

    /** Completed by the subjects collector's refresh once it has applied to the ViewModel. */
    private var refreshGate: CompletableDeferred<Unit>? = null

    fun armRefreshRace() {
        refreshGate = CompletableDeferred()
    }

    override suspend fun getQuestionsForTopic(topicId: String): List<QuestionItem> {
        val list = inner.getQuestionsForTopic(topicId)
        refreshGate?.takeIf { !it.isCompleted }?.complete(Unit)
        return list
    }

    override suspend fun addQuestion(
        subjectId: String,
        unitId: String?,
        topicId: String?,
        questionText: String,
        marks: Int,
        importance: Importance,
        status: String
    ): Result<QuestionItem> {
        val result = inner.addQuestion(subjectId, unitId, topicId, questionText, marks, importance, status)
        createdQuestion = result.getOrNull()
        // The question was published via the subjects flow above. Suspend so the collector's
        // refresh (queued by that emission) applies before the add-success callback appends.
        refreshGate?.await()
        return result
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QuestionsDuplicateRaceTest {

    @Test
    fun `repository subjects refresh racing the add-success callback leaves the question exactly once`() = runTest {
        val inner = MockStudyVaultRepository.createIsolated()
        val repo = RacyRefreshRepository(inner)
        val vm = QuestionsViewModel(repo)

        vm.loadForTopic("t_java_u2_1")
        shadowOf(Looper.getMainLooper()).idle()
        val before = vm.uiState.value
        assertFalse("Topic must load before the race starts", before.isEmpty)
        val beforeIds = before.questions.map { it.id }

        repo.armRefreshRace()
        vm.addQuestion(subjectId = "s_java", topicId = "t_java_u2_1", questionText = "Raced question")
        shadowOf(Looper.getMainLooper()).idle()

        val created = repo.createdQuestion
        assertNotNull("The add must persist through the repository", created)

        val state = vm.uiState.value
        assertTrue("Add success must be reported", state.successMessage != null)
        assertEquals(
            "A repository refresh racing the success callback must not duplicate the question",
            1,
            state.questions.count { it.id == created!!.id }
        )
        assertEquals(
            "Existing questions keep their ordering; the new question is appended once",
            beforeIds + created!!.id,
            state.questions.map { it.id }
        )
    }

    @Test
    fun `update handler keeps a question list free of duplicate ids while subjects refreshes`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()
        val vm = QuestionsViewModel(repo)

        vm.loadForTopic("t_java_u2_1")
        shadowOf(Looper.getMainLooper()).idle()
        assertFalse(vm.uiState.value.isEmpty)

        vm.addQuestion(subjectId = "s_java", topicId = "t_java_u2_1", questionText = "Raced question")
        shadowOf(Looper.getMainLooper()).idle()

        val raced = vm.uiState.value.questions.first { it.questionText == "Raced question" }
        vm.updateQuestion(raced.copy(questionText = "Raced question v2", marks = 7))
        shadowOf(Looper.getMainLooper()).idle()

        val state = vm.uiState.value
        assertTrue("Update success must be reported", state.successMessage != null)
        assertEquals(
            "Update must update in place, not multiply entries with the same id",
            1,
            state.questions.count { it.id == raced.id }
        )
        assertEquals("Marks must reflect the update", 7, state.questions.first { it.id == raced.id }.marks)
        assertEquals("Text must reflect the update", "Raced question v2", state.questions.first { it.id == raced.id }.questionText)
    }
}
