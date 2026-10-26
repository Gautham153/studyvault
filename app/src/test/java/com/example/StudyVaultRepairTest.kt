package com.example

import android.os.Looper
import com.example.data.model.Importance
import com.example.data.model.NoteItem
import com.example.data.model.QuestionItem
import com.example.data.repository.MockStudyMaterialRepository
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.SupabaseStudyMaterialRepository
import com.example.ui.screens.notes.NotesViewModel
import com.example.ui.screens.questions.QuestionsViewModel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Test double that lets a single mutation (add question / add note) be held in flight so a
 * resource switch can be simulated while the operation is still pending.
 */
private class GatedVaultRepository(
    private val delegate: StudyVaultRepository,
    private val questionGate: CompletableDeferred<Unit>? = null,
    private val noteGate: CompletableDeferred<Unit>? = null
) : StudyVaultRepository by delegate {

    override suspend fun addQuestion(
        subjectId: String,
        unitId: String?,
        topicId: String?,
        questionText: String,
        marks: Int,
        importance: Importance,
        status: String
    ): Result<QuestionItem> {
        questionGate?.await()
        return delegate.addQuestion(subjectId, unitId, topicId, questionText, marks, importance, status)
    }

    override suspend fun addNote(
        subjectId: String,
        topicId: String?,
        title: String,
        content: String
    ): Result<NoteItem> {
        noteGate?.await()
        return delegate.addNote(subjectId, topicId, title, content)
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyVaultRepairTest {

    // --- Material upload validation (parity with SupabaseStudyMaterialRepository) ---

    @Test
    fun `material upload rejects oversized file`() = runTest {
        val repo = MockStudyMaterialRepository.createIsolated()
        val oversized = ByteArray(MockStudyMaterialRepository.MAX_FILE_SIZE_BYTES.toInt() + 1)

        val result = repo.uploadMaterial(
            subjectId = "s_java",
            unitId = "u_java_1",
            topicId = null,
            originalFilename = "huge.pdf",
            mimeType = "application/pdf",
            fileBytes = oversized,
            onProgress = null
        )

        assertTrue("Oversized upload must fail", result.isFailure)
        assertTrue(
            "Error should reference the 25 MB limit",
            result.exceptionOrNull()?.message?.contains("25 MB") == true
        )
        assertTrue(
            "Failed upload must not be added to the material list",
            repo.materials.value.none { it.title == "huge" }
        )
    }

    @Test
    fun `material upload rejects empty file`() = runTest {
        val repo = MockStudyMaterialRepository.createIsolated()

        val result = repo.uploadMaterial(
            subjectId = "s_java",
            unitId = null,
            topicId = null,
            originalFilename = "empty.pdf",
            mimeType = "application/pdf",
            fileBytes = ByteArray(0),
            onProgress = null
        )

        assertTrue("Empty upload must fail", result.isFailure)
        assertTrue(
            "Error should explain the file is empty",
            result.exceptionOrNull()?.message?.contains("empty", ignoreCase = true) == true
        )
    }

    @Test
    fun `valid material upload is scoped to its subject and unit`() = runTest {
        val repo = MockStudyMaterialRepository.createIsolated()

        val result = repo.uploadMaterial(
            subjectId = "s_java",
            unitId = "u_java_1",
            topicId = null,
            originalFilename = "Graphs.pdf",
            mimeType = "application/pdf",
            fileBytes = ByteArray(1024),
            onProgress = null
        )

        assertTrue("Valid upload should succeed", result.isSuccess)
        val created = result.getOrNull()
        assertNotNull(created)
        assertEquals("s_java", created?.subjectId)

        val unitMaterials = repo.getMaterialsForUnit("s_java", "u_java_1")
        assertTrue(
            "Uploaded material should appear for its unit",
            unitMaterials.any { it.id == created?.id }
        )
        assertTrue(
            "Material must not leak into a different unit",
            repo.getMaterialsForUnit("s_java", "u_java_2").none { it.id == created?.id }
        )
    }

    @Test
    fun `createIsolated material repo does not share state with the singleton`() = runTest {
        val isolated = MockStudyMaterialRepository.createIsolated()
        val singletonCountBefore = MockStudyMaterialRepository.instance.materials.value.size

        val result = isolated.uploadMaterial(
            subjectId = "s_java",
            unitId = null,
            topicId = null,
            originalFilename = "Isolated.pdf",
            mimeType = "application/pdf",
            fileBytes = ByteArray(64),
            onProgress = null
        )

        assertTrue(result.isSuccess)
        assertTrue(
            "Isolated repo must contain the upload",
            isolated.materials.value.any { it.title == "Isolated" }
        )
        assertEquals(
            "Singleton must be unaffected by isolated repo writes",
            singletonCountBefore,
            MockStudyMaterialRepository.instance.materials.value.size
        )
    }

    // --- Vault repository correctness ---

    @Test
    fun `getTopicById returns null for an unknown topic instead of a fallback`() {
        val repo = MockStudyVaultRepository.createIsolated()

        assertNull("Unknown topic must resolve to null", repo.getTopicById("t_does_not_exist"))
        assertNotNull("A real topic must still resolve", repo.getTopicById("t_java_u2_1"))
    }

    @Test
    fun `addQuestion rejects non-positive marks`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()

        val result = repo.addQuestion(
            subjectId = "s_java",
            unitId = "u_java_2",
            topicId = "t_java_u2_1",
            questionText = "Explain JVM memory areas.",
            marks = 0,
            importance = Importance.MEDIUM,
            status = "NOT_PREPARED"
        )

        assertTrue("Question with zero marks must be rejected", result.isFailure)
        assertTrue(
            "Error should require positive marks",
            result.exceptionOrNull()?.message?.contains("positive", ignoreCase = true) == true
        )
    }

    // --- ViewModel state correctness ---

    @Test
    fun `QuestionsViewModel rejects blank question text`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()
        val vm = QuestionsViewModel(repo)

        vm.addQuestion(
            subjectId = "s_java",
            topicId = "t_java_u2_1",
            questionText = "   "
        )

        val state = vm.uiState.value
        assertNotNull("Blank text should surface an error", state.errorMessage)
        assertNull("Blank text must not report success", state.successMessage)
        assertTrue(
            "Add must not create a blank question",
            state.questions.none { it.questionText.isBlank() }
        )
    }

    @Test
    fun `QuestionsViewModel switching resources never shows stale questions`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()
        val vm = QuestionsViewModel(repo)

        vm.loadForSubject("s_java")
        val subjectState = vm.uiState.value
        assertFalse("Load must settle", subjectState.isLoading)
        assertFalse("Java subject should have questions", subjectState.isEmpty)

        vm.loadForTopic("t_java_u2_1")
        val topicState = vm.uiState.value
        assertFalse("Topic load must settle", topicState.isLoading)
        assertTrue(
            "Every displayed question must belong to the active topic",
            topicState.questions.all { it.topicId == "t_java_u2_1" }
        )
    }

    @Test
    fun `adding question does not duplicate when repository subjects flow refreshes before onSuccess`() = runTest {
        val repo = MockStudyVaultRepository.createIsolated()
        val vm = QuestionsViewModel(repo)

        vm.loadForTopic("t_java_u2_1")
        shadowOf(Looper.getMainLooper()).idle()

        val initialCount = vm.uiState.value.questions.size

        // In MockStudyVaultRepository, addQuestion updates subjects Flow, which triggers the
        // ViewModel's subjects collector to refreshActiveResource() before/concurrently with onSuccess completing.
        vm.addQuestion(
            subjectId = "s_java",
            unitId = "u_java_2",
            topicId = "t_java_u2_1",
            questionText = "Concurrency duplicate race test question",
            marks = 5,
            importance = Importance.MEDIUM,
            status = "NOT_PREPARED"
        )
        shadowOf(Looper.getMainLooper()).idle()

        val state = vm.uiState.value
        assertFalse("isSaving must be cleared", state.isSaving)
        assertEquals("Success message must be set", "Question added successfully.", state.successMessage)
        assertEquals(
            "Topic questions count must increase by exactly 1 without duplicates",
            initialCount + 1,
            state.questions.size
        )
        val matchingQuestions = state.questions.filter { it.questionText == "Concurrency duplicate race test question" }
        assertEquals(
            "Exactly 1 instance of the new question must exist in uiState (no duplicate from refresh/add-success race)",
            1,
            matchingQuestions.size
        )
        val questionIds = state.questions.map { it.id }
        assertEquals(
            "All question IDs in uiState must be distinct",
            questionIds.distinct().size,
            questionIds.size
        )
    }

    // --- Resource switching while a mutation is in flight ---

    @Test
    fun `switching topics while a question save is in flight never leaks into the new topic`() = runTest {
        val inner = MockStudyVaultRepository.createIsolated()
        // Give the destination topic its own question so the refreshed list is verifiable.
        inner.addQuestion(
            subjectId = "s_java",
            unitId = "u_java_2",
            topicId = "t_java_u2_2",
            questionText = "Existing topic B question",
            marks = 5,
            importance = Importance.MEDIUM,
            status = "NOT_PREPARED"
        )

        val gate = CompletableDeferred<Unit>()
        val repo = GatedVaultRepository(inner, questionGate = gate)
        val vm = QuestionsViewModel(repo)

        vm.loadForTopic("t_java_u2_1")
        assertTrue("Topic A should show its own questions", vm.uiState.value.questions.isNotEmpty())

        // Start a save for topic A; it blocks on the gate.
        vm.addQuestion(subjectId = "s_java", topicId = "t_java_u2_1", questionText = "Gated topic A question")
        assertTrue("The save should be in flight", vm.uiState.value.isSaving)

        // Switch to topic B while the save is still pending.
        vm.loadForTopic("t_java_u2_2")

        // Let the pending save finish.
        gate.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        val state = vm.uiState.value
        assertFalse("isSaving must not remain stuck after a resource switch", state.isSaving)
        assertTrue(
            "A question saved for topic A must never appear in topic B",
            state.questions.none { it.questionText == "Gated topic A question" }
        )
        assertTrue(
            "The active topic's own questions must be refreshed from the source",
            state.questions.any { it.questionText == "Existing topic B question" }
        )
        assertTrue(
            "Only the active topic's questions may be displayed",
            state.questions.all { it.topicId == "t_java_u2_2" }
        )
    }

    @Test
    fun `switching topics while a note save is in flight never leaks into the new topic`() = runTest {
        val inner = MockStudyVaultRepository.createIsolated()
        val gate = CompletableDeferred<Unit>()
        val repo = GatedVaultRepository(inner, noteGate = gate)
        val vm = NotesViewModel(repo)

        vm.loadForTopic("t_java_u2_1")
        assertTrue("Topic A should show its own notes", vm.uiState.value.notes.isNotEmpty())

        vm.addNote(subjectId = "s_java", topicId = "t_java_u2_1", title = "Gated topic A note", content = "body")
        assertTrue("The save should be in flight", vm.uiState.value.isSaving)

        vm.loadForTopic("t_java_u2_2")
        gate.complete(Unit)
        shadowOf(Looper.getMainLooper()).idle()

        val state = vm.uiState.value
        assertFalse("isSaving must not remain stuck after a resource switch", state.isSaving)
        assertTrue(
            "A note saved for topic A must never appear in topic B",
            state.notes.none { it.title == "Gated topic A note" }
        )
        assertTrue(
            "The active topic's own notes must be refreshed from the source",
            state.notes.any { it.title == "Polymorphism & Dynamic Dispatch" }
        )
        assertTrue(
            "Only the active topic's notes may be displayed",
            state.notes.all { it.topicId == "t_java_u2_2" }
        )
    }

    // --- Material reload failure contract ---

    @Test
    fun `getMaterialsForSubject propagates a failed reload instead of stale cache`() = runTest {
        val repo = SupabaseStudyMaterialRepository.instance

        val failure = try {
            repo.getMaterialsForSubject("s_java")
            null
        } catch (e: Exception) {
            e
        }

        assertNotNull(
            "A failed reload must fail loudly, never resolve as a successful (possibly empty) list",
            failure
        )
        assertTrue(
            "Failure must carry a non-blank error message",
            (failure?.message ?: "").isNotBlank()
        )
    }
}
