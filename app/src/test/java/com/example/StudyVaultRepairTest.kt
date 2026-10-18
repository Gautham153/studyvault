package com.example

import com.example.data.model.Importance
import com.example.data.repository.MockStudyMaterialRepository
import com.example.data.repository.MockStudyVaultRepository
import com.example.ui.screens.questions.QuestionsViewModel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

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
}
