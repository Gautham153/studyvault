package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.MockStudyMaterialRepository
import com.example.ui.screens.subjectdetail.SubjectDetailScreen
import com.example.ui.screens.subjectdetail.SubjectDetailViewModel
import com.example.ui.theme.StudyVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression for the reported defect: on Subject Detail the "No units yet..." empty state was
 * shown with no reachable Add Unit action, because the "Unit Progress" SectionHeader filled the
 * whole row and pushed the Add Unit button off-screen. These tests pin the empty-state action,
 * the create-refresh-list behavior, and that tapping the new unit row navigates to Unit Detail.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class SubjectEmptyUnitCreationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun emptySubjectRepository(): Pair<MockStudyVaultRepository, String> {
        val repository = MockStudyVaultRepository.createIsolated()
        repository.addSubject("Ethics", "HU201", "book")
        val subject = repository.subjects.value.first { it.name == "Ethics" }
        kotlinx.coroutines.runBlocking {
            for (unit in subject.units.toList()) {
                repository.deleteUnit(subject.id, unit.id)
            }
        }
        val reloaded = repository.getSubjectById(subject.id)!!
        assertTrue("precondition: subject must start with no units", reloaded.units.isEmpty())
        return repository to subject.id
    }

    private fun renderSubject(
        repository: MockStudyVaultRepository,
        subjectId: String,
        onNavigateToUnitDetail: (String, String) -> Unit
    ): SubjectDetailViewModel {
        val viewModel = SubjectDetailViewModel(
            repository = repository,
            materialRepository = MockStudyMaterialRepository.createIsolated()
        )
        composeRule.setContent {
            StudyVaultTheme {
                SubjectDetailScreen(
                    subjectId = subjectId,
                    onNavigateBack = {},
                    onNavigateToUnitDetail = onNavigateToUnitDetail,
                    viewModel = viewModel
                )
            }
        }
        return viewModel
    }

    @Test
    fun emptySubjectShowsEmptyStateAndAVisibleAddUnitAction() {
        val (repository, subjectId) = emptySubjectRepository()
        renderSubject(repository, subjectId) { _, _ -> }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("empty_units_message").assertExists()
        val addUnit = composeRule.onNodeWithTag("add_unit_button")
        addUnit.assertExists()
        // The core regression: the button must be laid out on screen, not clipped off the row.
        addUnit.assertIsDisplayed()
        composeRule.onNodeWithText("Add Unit").assertIsDisplayed()
    }

    @Test
    fun creatingAUnitMakesItAppearInTheListAndTappingItOpensUnitDetail() {
        val (repository, subjectId) = emptySubjectRepository()
        val navigations = mutableListOf<Pair<String, String>>()
        val viewModel = renderSubject(repository, subjectId) { subId, unitId ->
            navigations += subId to unitId
        }
        composeRule.waitForIdle()

        // The Add Unit action is present and clickable on the empty state (see the visibility
        // test above). Here we drive the same unit-creation code path the dialog's confirm
        // invokes and assert the resulting screen behavior.
        composeRule.onNodeWithTag("add_unit_button").assertIsDisplayed()
        viewModel.createUnit("Introduction", null)
        composeRule.waitForIdle()

        val created = repository.getSubjectById(subjectId)!!.units.single()
        assertEquals("Introduction", created.title)

        val row = composeRule.onNodeWithTag("unit_progress_row_${created.unitNumber}")
        row.assertIsDisplayed()

        // Still on Subject Detail (no navigation happened during creation).
        assertTrue("creation must not navigate away from Subject Detail", navigations.isEmpty())

        row.performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(subjectId to created.id), navigations)
    }
}
