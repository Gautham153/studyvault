package com.example

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.TopicStatus
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.StudyAction
import com.example.data.repository.StudyPlanning
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.home.revisionItemKey
import com.example.ui.screens.home.todayPlanItemKey
import com.example.ui.theme.StudyVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression: the same entity can appear in both Home's Today's Plan and Revision Priorities
 * sections. The two sections are items() calls of one LazyColumn, so their keys must never
 * collide - a shared key makes Compose throw "Key ... was already used" mid-scroll, which
 * kills the process and reads as the app closing.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h1600dp-xhdpi")
class HomeLazyKeyCollisionTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun overlappingTopicKeysDifferBetweenPlanAndRevisionSections() {
        val repository = MockStudyVaultRepository.createIsolated()
        val overlap = repository.todayStudyPlan.value.first { it.topicId == "t_java_u2_1" }
        val item = StudyPlanning.recommendationToStudyItem(overlap)

        val planKey = todayPlanItemKey(overlap)
        val revisionKey = revisionItemKey(item)

        assertNotEquals(planKey, revisionKey)
        assertTrue(planKey.startsWith("plan_"))
        assertTrue(revisionKey.startsWith("revision_"))
    }

    @Test
    fun overlappingQuestionKeysDifferBetweenPlanAndRevisionSections() {
        val repository = MockStudyVaultRepository.createIsolated()
        kotlinx.coroutines.runBlocking {
            repository.addQuestion(
                subjectId = "s_dbms",
                unitId = "u_dbms_1",
                topicId = null,
                questionText = "Define the ACID properties of transactions",
                marks = 7,
                importance = com.example.data.model.Importance.LOW,
                status = "High Priority"
            ).getOrThrow()
        }
        val recommendation = repository.allStudyRecommendations.value
            .first { it.questionId != null && it.status == TopicStatus.NEEDS_REVISION }
        val item = StudyPlanning.recommendationToStudyItem(recommendation)

        val planKey = todayPlanItemKey(recommendation)
        val revisionKey = revisionItemKey(item)

        assertNotEquals(planKey, revisionKey)
        assertTrue(planKey.startsWith("plan_"))
        assertTrue(revisionKey.startsWith("revision_"))
    }

    @Test
    fun scrollingHomeWithTheSameTopicInBothSectionsDoesNotCrash() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val viewModel = HomeViewModel(repository)

            val overlapTopicId = "t_java_u2_1"
            assertTrue(repository.todayStudyPlan.value.any { it.topicId == overlapTopicId })
            val revisionSection = repository.allStudyRecommendations.value
                .filter { it.status == TopicStatus.NEEDS_REVISION || it.action == StudyAction.REVISE }
                .take(3)
                .map { StudyPlanning.recommendationToStudyItem(it) }
            assertTrue(
                "Fixture must place the same topic in both Home sections",
                revisionSection.any { it.topicId == overlapTopicId }
            )

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

            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("revision_priority_card_$overlapTopicId"))
            composeRule.onNodeWithTag("revision_priority_card_$overlapTopicId").assertExists()

            composeRule.onNodeWithTag("home_screen")
                .performScrollToNode(hasTestTag("today_plan_card_$overlapTopicId"))
            composeRule.onNodeWithTag("today_plan_card_$overlapTopicId").assertExists()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
