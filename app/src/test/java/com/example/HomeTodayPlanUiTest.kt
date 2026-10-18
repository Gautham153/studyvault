package com.example

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.TopicStatus
import com.example.data.repository.MockStudyVaultRepository
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.theme.StudyVaultTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
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
}
