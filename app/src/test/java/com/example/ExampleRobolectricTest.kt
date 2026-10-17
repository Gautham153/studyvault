package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.example.data.model.Importance
import com.example.data.model.NoteItem
import com.example.data.model.QuestionItem
import com.example.data.repository.FakeAuthRepository
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.SupabaseAuthRepository
import com.example.ui.screens.auth.AuthViewModel
import com.example.ui.screens.notes.NotesViewModel
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
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("StudyVault", appName)
  }

  @Test
  fun `repository contains mock subjects`() {
    val repo = MockStudyVaultRepository.instance
    val subjects = repo.subjects.value
    assertTrue("Should contain at least 5 subjects", subjects.size >= 5)
    val javaSubject = repo.getSubjectById("s_java")
    assertNotNull("Java subject should exist", javaSubject)
    assertEquals("Java", javaSubject?.name)
  }

  @Test
  fun `missing or placeholder credentials cannot produce successful login in SupabaseAuthRepository`() = runTest {
    val authRepo = SupabaseAuthRepository.instance
    val result = authRepo.signIn("test@example.com", "password123")
    assertTrue("Login must fail when Supabase is not configured", result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(
      "Error should state Supabase is not configured",
      exception?.message?.contains("Supabase is not configured", ignoreCase = true) == true
    )
    assertNull("No fake user should be returned", authRepo.getCurrentUser())
    assertEquals("Auth state must remain Unauthenticated", AuthState.Unauthenticated, authRepo.authState.value)
  }

  @Test
  fun `missing or placeholder credentials cannot produce successful signup in SupabaseAuthRepository`() = runTest {
    val authRepo = SupabaseAuthRepository.instance
    val result = authRepo.signUp("test@example.com", "password123", "Test User")
    assertTrue("SignUp must fail when Supabase is not configured", result.isFailure)
    val exception = result.exceptionOrNull()
    assertNotNull(exception)
    assertTrue(
      "Error should state Supabase is not configured",
      exception?.message?.contains("Supabase is not configured", ignoreCase = true) == true
    )
    assertNull("No fake user should be returned", authRepo.getCurrentUser())
  }

  @Test
  fun `auth viewmodel rejects invalid email on login`() {
    val fakeRepo = FakeAuthRepository()
    val vaultRepo = MockStudyVaultRepository.instance
    val vm = AuthViewModel(fakeRepo, vaultRepo)

    vm.onLoginEmailChange("invalid-email")
    vm.onLoginPasswordChange("password123")
    vm.login {}

    val error = vm.loginState.value.errorMessage
    assertNotNull("Should have validation error for invalid email", error)
    assertTrue("Error message should mention valid email", error?.contains("valid email", ignoreCase = true) == true)
  }

  @Test
  fun `auth viewmodel rejects mismatched passwords on sign up`() {
    val fakeRepo = FakeAuthRepository()
    val vaultRepo = MockStudyVaultRepository.instance
    val vm = AuthViewModel(fakeRepo, vaultRepo)

    vm.onSignUpNameChange("Gautham")
    vm.onSignUpEmailChange("gautham@example.com")
    vm.onSignUpPasswordChange("password123")
    vm.onSignUpConfirmPasswordChange("differentPassword")
    vm.signUp {}

    val error = vm.signUpState.value.errorMessage
    assertNotNull("Should have validation error for password mismatch", error)
    assertTrue("Error message should mention match", error?.contains("match", ignoreCase = true) == true)
  }

  @Test
  fun `signup with email confirmation enabled does not open protected screens`() = runTest {
    val fakeRepo = FakeAuthRepository(requireEmailConfirmation = true)
    val vaultRepo = MockStudyVaultRepository.instance
    val vm = AuthViewModel(fakeRepo, vaultRepo)

    var authenticatedCallbackCalled = false

    vm.onSignUpNameChange("Gautham")
    vm.onSignUpEmailChange("gautham@studyvault.edu")
    vm.onSignUpPasswordChange("password123")
    vm.onSignUpConfirmPasswordChange("password123")
    vm.signUp {
      authenticatedCallbackCalled = true
    }

    assertFalse("Callback to navigate to protected screen must NOT be called", authenticatedCallbackCalled)
    assertTrue("Confirmation required flag must be true", vm.signUpState.value.isConfirmationRequired)
    assertNotNull("Confirmation message must be shown", vm.signUpState.value.confirmationRequiredMessage)
    assertEquals("Auth state must remain Unauthenticated", AuthState.Unauthenticated, fakeRepo.authState.value)
  }

  @Test
  fun `signup with email confirmation disabled establishes active session`() = runTest {
    val fakeRepo = FakeAuthRepository(requireEmailConfirmation = false)
    val vaultRepo = MockStudyVaultRepository.instance
    val vm = AuthViewModel(fakeRepo, vaultRepo)

    var authenticatedCallbackCalled = false

    vm.onSignUpNameChange("Gautham")
    vm.onSignUpEmailChange("gautham@studyvault.edu")
    vm.onSignUpPasswordChange("password123")
    vm.onSignUpConfirmPasswordChange("password123")
    vm.signUp {
      authenticatedCallbackCalled = true
    }

    assertTrue("Callback to navigate to Home should be called", authenticatedCallbackCalled)
    assertFalse("Confirmation required flag should be false", vm.signUpState.value.isConfirmationRequired)
    assertTrue("Auth state should be Authenticated", fakeRepo.authState.value is AuthState.Authenticated)
    assertEquals("Gautham", vaultRepo.overallProgress.value.studentName)
  }

  @Test
  fun `sign out success clears session and updates auth state`() = runTest {
    val initialUser = AuthUser("u1", "user@example.com", "Test User")
    val fakeRepo = FakeAuthRepository(
      initialState = AuthState.Authenticated(initialUser)
    )
    val vaultRepo = MockStudyVaultRepository.instance
    val vm = AuthViewModel(fakeRepo, vaultRepo)

    var successCalled = false
    var errorCalled = false

    vm.signOut(
      onSuccess = { successCalled = true },
      onError = { errorCalled = true }
    )

    assertTrue("onSuccess must be called when signOut succeeds", successCalled)
    assertFalse("onError must not be called", errorCalled)
    assertEquals(AuthState.Unauthenticated, fakeRepo.authState.value)
  }

  @Test
  fun `sign out failure handles error honestly without reporting success`() = runTest {
    val initialUser = AuthUser("u1", "user@example.com", "Test User")
    val fakeRepo = FakeAuthRepository(
      initialState = AuthState.Authenticated(initialUser),
      shouldFailSignOut = true,
      failureMessage = "Network disconnect"
    )
    val vaultRepo = MockStudyVaultRepository.instance
    val vm = AuthViewModel(fakeRepo, vaultRepo)

    var successCalled = false
    var errorCalled = false
    var errorMessage: String? = null

    vm.signOut(
      onSuccess = { successCalled = true },
      onError = {
        errorCalled = true
        errorMessage = it
      }
    )

    assertFalse("onSuccess must NOT be called when signOut fails", successCalled)
    assertTrue("onError must be called when signOut fails", errorCalled)
    assertEquals("Network disconnect", errorMessage)
    assertTrue("User remains authenticated when logout fails", fakeRepo.authState.value is AuthState.Authenticated)
  }

  @Test
  fun `session restoration restores genuine authenticated user`() = runTest {
    val savedUser = AuthUser("u42", "active@studyvault.edu", "Active Student")
    val fakeRepo = FakeAuthRepository(
      initialState = AuthState.Authenticated(savedUser)
    )
    fakeRepo.checkSession()
    val state = fakeRepo.authState.value
    assertTrue("State should be Authenticated", state is AuthState.Authenticated)
    assertEquals("active@studyvault.edu", (state as AuthState.Authenticated).user.email)
  }

  @Test
  fun `questions CRUD in repository works properly`() = runTest {
    val repo = MockStudyVaultRepository.instance
    val initialQuestions = repo.getQuestionsForSubject("s_java")
    assertTrue("Should have initial sample questions for Java", initialQuestions.isNotEmpty())

    // Add question
    val addResult = repo.addQuestion(
      subjectId = "s_java",
      unitId = "u_java_2",
      topicId = "t_java_u2_1",
      questionText = "What is the difference between Comparable and Comparator?",
      marks = 10,
      importance = Importance.HIGH,
      status = "NOT_PREPARED"
    )
    assertTrue("Add question should succeed", addResult.isSuccess)
    val created = addResult.getOrNull()
    assertNotNull(created)
    assertEquals("What is the difference between Comparable and Comparator?", created?.questionText)

    // Update question
    val updatedQuestion = created!!.copy(status = "Prepared", marks = 15)
    val updateResult = repo.updateQuestion(updatedQuestion)
    assertTrue("Update question should succeed", updateResult.isSuccess)
    assertEquals(15, updateResult.getOrNull()?.marks)

    // Delete question
    val deleteResult = repo.deleteQuestion(created.id)
    assertTrue("Delete question should succeed", deleteResult.isSuccess)
  }

  @Test
  fun `notes CRUD in repository works properly`() = runTest {
    val repo = MockStudyVaultRepository.instance
    val initialNotes = repo.getNotesForSubject("s_java")
    assertTrue("Should have initial sample notes for Java", initialNotes.isNotEmpty())

    // Add note
    val addResult = repo.addNote(
      subjectId = "s_java",
      topicId = "t_java_u2_1",
      title = "Generics Wildcard Rules",
      content = "Upper bounded wildcards (? extends Number) provide read-only access (covariance)."
    )
    assertTrue("Add note should succeed", addResult.isSuccess)
    val created = addResult.getOrNull()
    assertNotNull(created)
    assertEquals("Generics Wildcard Rules", created?.title)

    // Update note
    val updatedNote = created!!.copy(content = "Updated note content.")
    val updateResult = repo.updateNote(updatedNote)
    assertTrue("Update note should succeed", updateResult.isSuccess)
    assertEquals("Updated note content.", updateResult.getOrNull()?.content)

    // Delete note
    val deleteResult = repo.deleteNote(created.id)
    assertTrue("Delete note should succeed", deleteResult.isSuccess)
  }

  @Test
  fun `QuestionsViewModel loads and manages questions state without stale data`() = runTest {
    val repo = MockStudyVaultRepository.instance
    val vm = QuestionsViewModel(repo)

    vm.loadForTopic("t_java_u2_1")
    val state = vm.uiState.value
    assertFalse("Loading flag should be reset after load", state.isLoading)
    assertFalse("Questions list should not be empty for test topic", state.isEmpty)

    // Add question through ViewModel
    vm.addQuestion(
      subjectId = "s_java",
      topicId = "t_java_u2_1",
      questionText = "VM Added Question"
    )
    val afterAdd = vm.uiState.value
    assertNotNull("Success message should be set", afterAdd.successMessage)
    assertTrue("Questions list should contain new question", afterAdd.questions.any { it.questionText == "VM Added Question" })
  }

  @Test
  fun `NotesViewModel loads and manages notes state without stale data`() = runTest {
    val repo = MockStudyVaultRepository.instance
    val vm = NotesViewModel(repo)

    vm.loadForTopic("t_java_u2_1")
    val state = vm.uiState.value
    assertFalse("Loading flag should be reset after load", state.isLoading)
    assertFalse("Notes list should not be empty for test topic", state.isEmpty)

    // Add note through ViewModel
    vm.addNote(
      subjectId = "s_java",
      topicId = "t_java_u2_1",
      title = "VM Added Note",
      content = "Key details"
    )
    val afterAdd = vm.uiState.value
    assertNotNull("Success message should be set", afterAdd.successMessage)
    assertTrue("Notes list should contain new note", afterAdd.notes.any { it.title == "VM Added Note" })
  }
}
