package com.example

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.example.ui.screens.unitdetail.AddTopicDialog
import com.example.ui.theme.StudyVaultTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Feedback contract of the unit screen's Add Topic dialog: saving disables the confirm action
 * so duplicate taps cannot fire, a repository error renders in place (dialog stays available for
 * retry), and only a non-blank title triggers the save callback.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AddTopicDialogTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun confirmIsDisabledWhileSavingAndTapDoesNotFireSave() {
        var saved = 0
        composeRule.setContent {
            StudyVaultTheme {
                AddTopicDialog(
                    isSaving = true,
                    errorMessage = null,
                    onDismiss = {},
                    onSave = { saved++ }
                )
            }
        }
        composeRule.onNodeWithTag("add_topic_title_field").assertIsNotEnabled()
        composeRule.onNodeWithTag("add_topic_confirm_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("add_topic_confirm_button").performClick()
        composeRule.waitForIdle()

        assertEquals("Confirm must not fire while a save is in flight", 0, saved)
        composeRule.onNodeWithText("Adding...").assertExists()
    }

    @Test
    fun repositoryErrorIsShownInDialogAndSaveStillCallable() {
        var saved = 0
        composeRule.setContent {
            StudyVaultTheme {
                AddTopicDialog(
                    isSaving = false,
                    errorMessage = "new row violates row-level security policy for table \"topics\"",
                    onDismiss = {},
                    onSave = { saved++ }
                )
            }
        }
        composeRule.onNodeWithTag("add_topic_error").assertExists()
        composeRule.onNodeWithText("new row violates row-level security policy for table \"topics\"").assertExists()

        composeRule.onNodeWithTag("add_topic_title_field").performTextInput("Retry Topic")
        composeRule.onNodeWithTag("add_topic_confirm_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, saved)
    }

    @Test
    fun blankTitleShowsValidationAndDoesNotSave() {
        var saved = 0
        composeRule.setContent {
            StudyVaultTheme {
                AddTopicDialog(
                    isSaving = false,
                    errorMessage = null,
                    onDismiss = {},
                    onSave = { saved++ }
                )
            }
        }
        composeRule.onNodeWithTag("add_topic_title_field").performTextInput("   ")
        composeRule.onNodeWithTag("add_topic_confirm_button").performClick()
        composeRule.waitForIdle()

        assertEquals(0, saved)
        composeRule.onNodeWithTag("add_topic_error").assertExists()
        assertTrue(true)
    }
}
