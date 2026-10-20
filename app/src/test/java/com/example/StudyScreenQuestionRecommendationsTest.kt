package com.example

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.example.data.model.Importance
import com.example.data.repository.MockStudyVaultRepository
import com.example.ui.screens.study.StudyFilter
import com.example.ui.screens.study.StudyScreen
import com.example.ui.screens.study.StudyViewModel
import com.example.ui.theme.StudyVaultTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Study screen question recommendations: the same eligible question surfaced by the engine
 * appears in Home's capped Today's Plan and in the Study Priority/Needs Revision views, and
 * selecting it navigates to the subject questions screen — never to a topic destination.
 * A question and its linked topic keep distinct identity keys so the lazy list never collides.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StudyScreenQuestionRecommendationsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun questionAppearsInHomePlanAndStudyPriorityAndNeedsRevisionViews() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val added = runBlocking {
                repository.addQuestion(
                    subjectId = "s_java",
                    unitId = null,
                    topicId = null,
                    questionText = "Explain garbage collection generations",
                    marks = 10,
                    importance = Importance.HIGH,
                    status = "High Priority"
                ).getOrThrow()
            }

            // Same question in Home's capped Today's Plan...
            val inHomePlan = repository.todayStudyPlan.value.firstOrNull { it.isQuestion }
            assertNotNull("Eligible question must surface in Today's Plan", inHomePlan)
            assertEquals(added.id, inHomePlan?.questionId)

            // ...and in the complete recommendation source, while the topic-only source stays clean.
            assertTrue(repository.allStudyRecommendations.value.any { it.questionId == added.id })
            assertTrue(repository.studyRecommendations.value.none { it.isQuestion })

            val viewModel = StudyViewModel(repository)
            val subscription = CoroutineScope(Dispatchers.Main).launch { viewModel.studyItems.collect {} }

            viewModel.setFilter(StudyFilter.PRIORITY)
            assertTrue(
                "Study Priority must include the same question",
                viewModel.studyItems.value.any { it.questionId == added.id }
            )

            viewModel.setFilter(StudyFilter.NEEDS_REVISION)
            assertTrue(
                "Study Needs Revision must include the same question",
                viewModel.studyItems.value.any { it.questionId == added.id }
            )

            subscription.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun clickingQuestionCardRoutesToSubjectQuestionsAndTopicCardRoutesToTopicDetail() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val added = runBlocking {
                repository.addQuestion(
                    subjectId = "s_java",
                    unitId = "u_java_2",
                    topicId = "t_java_u2_5",
                    questionText = "Explain the class loader delegation model",
                    marks = 10,
                    importance = Importance.HIGH,
                    status = "High Priority"
                ).getOrThrow()
            }

            val viewModel = StudyViewModel(repository)
            val topicDestinations = mutableListOf<String>()
            val questionDestinations = mutableListOf<String>()
            composeRule.setContent {
                StudyVaultTheme {
                    StudyScreen(
                        onNavigateToTopicDetail = { topicDestinations += it },
                        onNavigateToSubjectQuestions = { questionDestinations += it },
                        viewModel = viewModel
                    )
                }
            }

            viewModel.setFilter(StudyFilter.PRIORITY)
            composeRule.waitForIdle()

            val items = viewModel.studyItems.value
            val topicItem = items.first { it.topicId == "t_java_u2_5" && !it.isQuestion }
            val questionItem = items.first { it.questionId == added.id }

            assertEquals("Question keeps its real linked topic", "t_java_u2_5", questionItem.topicId)
            assertNotEquals(
                "Topic and question identities must not collide",
                topicItem.cardKey,
                questionItem.cardKey
            )

            composeRule.onNodeWithTag("study_screen")
                .performScrollToNode(hasTestTag("study_item_card_q_${added.id}"))
            composeRule.onNodeWithTag("study_item_card_q_${added.id}").assertExists()
            composeRule.onNodeWithTag("study_item_card_q_${added.id}").performClick()
            composeRule.waitForIdle()

            assertEquals(listOf("s_java"), questionDestinations)
            assertTrue("Question click must never route to a topic", topicDestinations.isEmpty())

            composeRule.onNodeWithTag("study_screen")
                .performScrollToNode(hasTestTag("study_item_card_t_java_u2_5"))
            composeRule.onNodeWithTag("study_item_card_t_java_u2_5").performClick()
            composeRule.waitForIdle()

            assertEquals(listOf("t_java_u2_5"), topicDestinations)
            assertEquals(1, questionDestinations.size)
        } finally {
            Dispatchers.resetMain()
        }
    }

    /**
     * A question saved under DBMS with HIGH importance and "High Priority" status surfaces in
     * the Study Priority and All filters with its Practice label, routes to DBMS's Questions
     * tab on tap, appears alongside topic recommendations in All, and is never fabricated into
     * Recently Revised (which stays backed by real revision-history records).
     */
    @Test
    fun dbmsHighPriorityQuestionShowsPracticeInFiltersAndIsNotInRecentlyRevised() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val repository = MockStudyVaultRepository.createIsolated()
            val added = runBlocking {
                repository.addQuestion(
                    subjectId = "s_dbms",
                    unitId = "u_dbms_1",
                    topicId = null,
                    questionText = "Explain transaction schedules and conflict serializability",
                    marks = 10,
                    importance = Importance.HIGH,
                    status = "High Priority"
                ).getOrThrow()
            }

            val viewModel = StudyViewModel(repository)
            val questionDestinations = mutableListOf<String>()
            val topicDestinations = mutableListOf<String>()
            composeRule.setContent {
                StudyVaultTheme {
                    StudyScreen(
                        onNavigateToTopicDetail = { topicDestinations += it },
                        onNavigateToSubjectQuestions = { questionDestinations += it },
                        viewModel = viewModel
                    )
                }
            }
            val subscription = CoroutineScope(Dispatchers.Main).launch { viewModel.studyItems.collect {} }

            viewModel.setFilter(StudyFilter.PRIORITY)
            composeRule.waitForIdle()
            assertTrue(
                "DBMS High Priority question must appear in Study Priority",
                viewModel.studyItems.value.any { it.questionId == added.id }
            )

            composeRule.onNodeWithTag("study_screen")
                .performScrollToNode(hasTestTag("study_item_card_q_${added.id}"))
            composeRule.onNode(
                hasTestTag("study_item_card_q_${added.id}") and hasText("Practice", substring = true)
            ).assertExists()

            composeRule.onNodeWithTag("study_item_card_q_${added.id}").performClick()
            composeRule.waitForIdle()
            assertEquals(listOf("s_dbms"), questionDestinations)
            assertTrue(topicDestinations.isEmpty())

            // The All filter shows both topic and question recommendations.
            viewModel.setFilter(StudyFilter.ALL)
            composeRule.waitForIdle()
            val allItems = viewModel.studyItems.value
            assertTrue(allItems.any { it.questionId == added.id })
            assertTrue("All must still include topic recommendations", allItems.any { !it.isQuestion })

            // Recently Revised stays based on revision history (seeded java revisions only).
            viewModel.setFilter(StudyFilter.RECENTLY_REVISED)
            composeRule.waitForIdle()
            val revised = viewModel.studyItems.value
            assertTrue(revised.any { it.topicId == "t_java_u2_1" })
            assertTrue(
                "A high-priority question must never appear in Recently Revised",
                revised.none { it.isQuestion }
            )

            subscription.cancel()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
