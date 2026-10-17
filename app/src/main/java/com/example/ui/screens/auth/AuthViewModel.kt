package com.example.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.example.data.model.SignUpResult
import com.example.data.repository.AuthRepository
import com.example.data.repository.MockStudyVaultRepository
import com.example.data.repository.StudyVaultRepository
import com.example.data.repository.StudyVaultRepositoryProvider
import com.example.data.repository.SupabaseAuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val isPasswordVisible: Boolean = false,
    val isConfirmPasswordVisible: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val confirmationRequiredMessage: String? = null,
    val isConfirmationRequired: Boolean = false
)

data class ForgotPasswordUiState(
    val email: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val successMessage: String? = null
)

class AuthViewModel(
    private val authRepository: AuthRepository = SupabaseAuthRepository.instance,
    private val studyVaultRepository: StudyVaultRepository = StudyVaultRepositoryProvider.repository
) : ViewModel() {

    val authState: StateFlow<AuthState> = authRepository.authState

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _signUpState = MutableStateFlow(SignUpUiState())
    val signUpState: StateFlow<SignUpUiState> = _signUpState.asStateFlow()

    private val _forgotPasswordState = MutableStateFlow(ForgotPasswordUiState())
    val forgotPasswordState: StateFlow<ForgotPasswordUiState> = _forgotPasswordState.asStateFlow()

    init {
        // Sync genuine authenticated user into StudyVault profile metadata when authState changes
        viewModelScope.launch {
            authRepository.authState.collect { state ->
                if (state is AuthState.Authenticated) {
                    studyVaultRepository.setAuthenticatedUser(
                        email = state.user.email,
                        displayName = state.user.displayName
                    )
                }
            }
        }
    }

    // --- Login Handlers ---
    fun onLoginEmailChange(email: String) {
        _loginState.value = _loginState.value.copy(email = email, errorMessage = null)
    }

    fun onLoginPasswordChange(password: String) {
        _loginState.value = _loginState.value.copy(password = password, errorMessage = null)
    }

    fun toggleLoginPasswordVisibility() {
        _loginState.value = _loginState.value.copy(isPasswordVisible = !_loginState.value.isPasswordVisible)
    }

    fun login(onSuccess: () -> Unit) {
        val state = _loginState.value
        val email = state.email.trim()
        val password = state.password

        if (email.isBlank()) {
            _loginState.value = state.copy(errorMessage = "Please enter your email address.")
            return
        }
        if (!isValidEmail(email)) {
            _loginState.value = state.copy(errorMessage = "Please enter a valid email address.")
            return
        }
        if (password.isBlank()) {
            _loginState.value = state.copy(errorMessage = "Please enter your password.")
            return
        }

        _loginState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            val result = authRepository.signIn(email, password)
            if (result.isSuccess) {
                _loginState.value = _loginState.value.copy(isLoading = false, errorMessage = null)
                val user = result.getOrNull()
                if (user != null) {
                    studyVaultRepository.setAuthenticatedUser(user.email, user.displayName)
                }
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Login failed. Please check your credentials."
                _loginState.value = _loginState.value.copy(isLoading = false, errorMessage = errorMsg)
            }
        }
    }

    // --- Sign Up Handlers ---
    fun onSignUpNameChange(name: String) {
        _signUpState.value = _signUpState.value.copy(
            name = name,
            errorMessage = null,
            confirmationRequiredMessage = null,
            isConfirmationRequired = false
        )
    }

    fun onSignUpEmailChange(email: String) {
        _signUpState.value = _signUpState.value.copy(
            email = email,
            errorMessage = null,
            confirmationRequiredMessage = null,
            isConfirmationRequired = false
        )
    }

    fun onSignUpPasswordChange(password: String) {
        _signUpState.value = _signUpState.value.copy(
            password = password,
            errorMessage = null
        )
    }

    fun onSignUpConfirmPasswordChange(confirmPassword: String) {
        _signUpState.value = _signUpState.value.copy(
            confirmPassword = confirmPassword,
            errorMessage = null
        )
    }

    fun toggleSignUpPasswordVisibility() {
        _signUpState.value = _signUpState.value.copy(isPasswordVisible = !_signUpState.value.isPasswordVisible)
    }

    fun toggleSignUpConfirmPasswordVisibility() {
        _signUpState.value = _signUpState.value.copy(isConfirmPasswordVisible = !_signUpState.value.isConfirmPasswordVisible)
    }

    fun signUp(onAuthenticated: () -> Unit) {
        val state = _signUpState.value
        val name = state.name.trim()
        val email = state.email.trim()
        val password = state.password
        val confirmPassword = state.confirmPassword

        if (name.isBlank()) {
            _signUpState.value = state.copy(errorMessage = "Please enter your name.")
            return
        }
        if (email.isBlank() || !isValidEmail(email)) {
            _signUpState.value = state.copy(errorMessage = "Please enter a valid email address.")
            return
        }
        if (password.length < 6) {
            _signUpState.value = state.copy(errorMessage = "Password must be at least 6 characters.")
            return
        }
        if (password != confirmPassword) {
            _signUpState.value = state.copy(errorMessage = "Passwords do not match.")
            return
        }

        _signUpState.value = state.copy(
            isLoading = true,
            errorMessage = null,
            confirmationRequiredMessage = null,
            isConfirmationRequired = false
        )

        viewModelScope.launch {
            val result = authRepository.signUp(email, password, name)
            if (result.isSuccess) {
                when (val signUpResult = result.getOrNull()) {
                    is SignUpResult.Authenticated -> {
                        _signUpState.value = _signUpState.value.copy(
                            isLoading = false,
                            errorMessage = null,
                            confirmationRequiredMessage = null,
                            isConfirmationRequired = false
                        )
                        studyVaultRepository.setAuthenticatedUser(
                            signUpResult.user.email,
                            signUpResult.user.displayName
                        )
                        onAuthenticated()
                    }
                    is SignUpResult.ConfirmationRequired -> {
                        _signUpState.value = _signUpState.value.copy(
                            isLoading = false,
                            errorMessage = null,
                            confirmationRequiredMessage = "Account created! Please check your email to verify your account, then sign in.",
                            isConfirmationRequired = true
                        )
                        // Do NOT call onAuthenticated(): user must verify email first
                    }
                    null -> {
                        _signUpState.value = _signUpState.value.copy(
                            isLoading = false,
                            errorMessage = "Unexpected response during sign up. Please try again."
                        )
                    }
                }
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Sign up failed. Please try again."
                _signUpState.value = _signUpState.value.copy(
                    isLoading = false,
                    errorMessage = errorMsg,
                    confirmationRequiredMessage = null,
                    isConfirmationRequired = false
                )
            }
        }
    }

    // --- Forgot Password Handlers ---
    fun onForgotPasswordEmailChange(email: String) {
        _forgotPasswordState.value = _forgotPasswordState.value.copy(email = email, errorMessage = null)
    }

    fun sendPasswordReset() {
        val state = _forgotPasswordState.value
        val email = state.email.trim()

        if (email.isBlank() || !isValidEmail(email)) {
            _forgotPasswordState.value = state.copy(errorMessage = "Please enter a valid email address.")
            return
        }

        _forgotPasswordState.value = state.copy(isLoading = true, errorMessage = null, successMessage = null)

        viewModelScope.launch {
            val result = authRepository.resetPasswordForEmail(email)
            if (result.isSuccess) {
                _forgotPasswordState.value = _forgotPasswordState.value.copy(
                    isLoading = false,
                    errorMessage = null,
                    successMessage = "Password reset link sent to $email. Please check your inbox."
                )
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to send reset link. Please try again."
                _forgotPasswordState.value = _forgotPasswordState.value.copy(
                    isLoading = false,
                    errorMessage = errorMsg,
                    successMessage = null
                )
            }
        }
    }

    // --- Sign Out Handler ---
    fun signOut(onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            val result = authRepository.signOut()
            if (result.isSuccess) {
                studyVaultRepository.clearData()
                com.example.data.repository.StudyMaterialRepositoryProvider.repository.clearData()
                onSuccess()
            } else {
                val errorMsg = result.exceptionOrNull()?.message ?: "Failed to sign out. Please check your network and try again."
                onError(errorMsg)
            }
        }
    }

    fun checkExistingSession() {
        authRepository.checkSession()
    }

    private fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() &&
                Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\$").matches(email)
    }
}
