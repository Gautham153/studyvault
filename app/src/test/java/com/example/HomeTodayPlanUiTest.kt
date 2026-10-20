package com.example

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.Importance
import com.example.data.model.TopicStatus
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.StudyAction
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.theme.StudyVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 7 UI wiring: the Home "Today's Plan" section renders engine recommendations and its
 * actions mutate the real repository data (not a local copy).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HomeTodayPlanUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todaysPlanRendersAndMarkingDoneCompletesTheRealTopic() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val viewModel = HomeViewModel(repository)
            composeRule.setContent {
                StudyVaultTheme {
                    HomeScreen(
                        onNavigateToSubjectDetail = {},
                        onNavigateToTopicDetail = {},
                        onNavigateToSubjectsTab = {},
                        onNavigateToStudyTab = {},
                        onOpenProfile = {},
                        viewModel = viewModel
                    )
                }
            }

            val planned = repository.todayStudyPlan.value.first()
            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("today_plan_card_${planned.topicId}"))
            composeRule.onNodeWithTag("today_plan_card_${planned.topicId}").assertExists()

            if (planned.status != TopicStatus.COMPLETED) {
                composeRule.onNodeWithTag("home_screen")
                    .performScrollToNode(hasTestTag("today_plan_done_${planned.topicId}"))
                composeRule.onNodeWithTag("today_plan_done_${planned.topicId}").performClick()
                composeRule.waitForIdle()

                val updated = repository.studyRecommendations.value.first { it.topicId == planned.topicId }
                assertEquals(TopicStatus.COMPLETED, updated.status)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Regression: the Home "Revision Priorities" section derives from the full engine
     * recommendation list (not the five-item Today's Plan), so a completed topic the engine
     * recommends revising must appear in the section even when it is not in the selected plan,
     * while a freshly revised completed topic with no revision need must not appear. The section
     * is identified through its dedicated `revision_priority_card_` tags, which are never used by
     * the Today's Plan cards.
     */
    @Test
    fun revisionPrioritiesIncludeFlaggedTopicsOutsideTodaysPlanAndDropFreshlyRevisedOnes() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val viewModel = HomeViewModel(repository)
            composeRule.setContent {
                StudyVaultTheme {
                    HomeScreen(
                        onNavigateToSubjectDetail = {},
                        onNavigateToTopicDetail = {},
                        onNavigateToSubjectsTab = {},
                        onNavigateToStudyTab = {},
                        onOpenProfile = {},
                        viewModel = viewModel
                    )
                }
            }

            val flaggedOutsidePlan = "t_java_u2_7" // completed, never revised -> REVISE, below the plan cap
            val flaggedInsidePlan = "t_java_u2_1"  // completed + idle -> REVISE, inside the plan
            val stillFlaggedAnchor = "t_java_u2_2"  // completed + idle -> REVISE, used as scroll anchor

            // The flagged topic is a REVISE recommendation that did NOT make the Today's Plan.
            assertEquals(
                StudyAction.REVISE,
                repository.studyRecommendations.value.first { it.topicId == flaggedOutsidePlan }.action
            )
            assertTrue(
                "Fixture must sit outside the capped Today's Plan",
                repository.todayStudyPlan.value.none { it.topicId == flaggedOutsidePlan }
            )
            assertTrue(repository.todayStudyPlan.value.any { it.topicId == flaggedInsidePlan })

            // While the last planned card is on screen, the out-of-plan topic's plan card is absent.
            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("today_plan_card_$flaggedInsidePlan"))
            composeRule.onNodeWithTag("today_plan_card_$flaggedInsidePlan").assertExists()
            composeRule.onNodeWithTag("today_plan_card_$flaggedOutsidePlan").assertDoesNotExist()

            // ...yet it still appears in Revision Priorities (section-scoped tags).
            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("revision_priority_card_$flaggedOutsidePlan"))
            composeRule.onNodeWithTag("revision_priority_card_$flaggedOutsidePlan").assertExists()
            composeRule.onNodeWithTag("revision_priority_card_$flaggedInsidePlan").assertExists()

            runBlocking { repository.markTopicRevised(flaggedInsidePlan) }
            composeRule.waitForIdle()

            assertFalse(
                "Freshly revised completed topic must have no revision need",
                repository.studyRecommendations.value
                    .any { it.topicId == flaggedInsidePlan && it.action == StudyAction.REVISE }
            )

            // Anchor on a still-flagged section card; the freshly revised topic must be gone
            // from the Revision Priorities section.
            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("revision_priority_card_$stillFlaggedAnchor"))
            composeRule.onNodeWithTag("revision_priority_card_$stillFlaggedAnchor").assertExists()
            composeRule.onNodeWithTag("revision_priority_card_$flaggedInsidePlan").assertDoesNotExist()
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * Regression: Home's Revision Priorities section is backed by the combined
     * topic + question recommendation source, so a High Priority question surfaces there even
     * when its score leaves it outside the five-item Today's Plan.
     */
    @Test
    fun revisionPrioritiesIncludeHighPriorityQuestionOutsideFiveItemTodaysPlan() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val added = runBlocking {
                repository.addQuestion(
                    subjectId = "s_dbms",
                    unitId = "u_dbms_1",
                    topicId = null,
                    questionText = "Define the ACID properties of transactions",
                    marks = 7,
                    importance = Importance.LOW,
                    status = "High Priority"
                ).getOrThrow()
            }
            val viewModel = HomeViewModel(repository)
            composeRule.setContent {
                StudyVaultTheme {
                    HomeScreen(
                        onNavigateToSubjectDetail = {},
                        onNavigateToTopicDetail = {},
                        onNavigateToSubjectsTab = {},
                        onNavigateToStudyTab = {},
                        onOpenProfile = {},
                        viewModel = viewModel
                    )
                }
            }

            // The question is a flagged (NEEDS_REVISION / PRACTICE) recommendation...
            val questionRec =
                repository.allStudyRecommendations.value.first { it.questionId == added.id }
            assertEquals(TopicStatus.NEEDS_REVISION, questionRec.status)
            assertEquals(StudyAction.PRACTICE, questionRec.action)

            // ...while the capped five-item plan does not contain it (this section's premise).
            assertEquals(5, repository.todayStudyPlan.value.size)
            assertTrue(
                "Fixture question must sit outside the capped Today's Plan",
                repository.todayStudyPlan.value.none { it.isQuestion }
            )
            assertTrue(repository.studyRecommendations.value.none { it.isQuestion })

            // The last plan card renders...
            val lastPlanned = repository.todayStudyPlan.value.last()
            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("today_plan_card_${lastPlanned.topicId}"))
            composeRule.onNodeWithTag("today_plan_card_${lastPlanned.topicId}").assertExists()

            // ...the question has no plan card...
            composeRule.onNodeWithTag("today_plan_card_q_${added.id}").assertDoesNotExist()

            // ...yet it appears in Revision Priorities.
            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("revision_priority_card_q_${added.id}"))
            composeRule.onNodeWithTag("revision_priority_card_q_${added.id}").assertExists()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
