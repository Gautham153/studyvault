package com.example.data.repository

import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.example.data.model.SignUpResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FakeAuthRepository(
    initialState: AuthState = AuthState.Unauthenticated,
    var requireEmailConfirmation: Boolean = false,
    var shouldFailSignIn: Boolean = false,
    var shouldFailSignUp: Boolean = false,
    var shouldFailSignOut: Boolean = false,
    var failureMessage: String = "Simulated error"
) : AuthRepository {

    private val _authState = MutableStateFlow<AuthState>(initialState)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val registeredUsers = mutableMapOf<String, String>() // email -> password
    private var currentUser: AuthUser? = when (initialState) {
        is AuthState.Authenticated -> initialState.user
        else -> null
    }

    override fun checkSession() {
        if (currentUser != null) {
            _authState.value = AuthState.Authenticated(currentUser!!)
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<SignUpResult> {
        if (shouldFailSignUp) {
            return Result.failure(Exception(failureMessage))
        }

        registeredUsers[email] = password

        return if (requireEmailConfirmation) {
            _authState.value = AuthState.Unauthenticated
            Result.success(SignUpResult.ConfirmationRequired(email))
        } else {
            val user = AuthUser(
                id = "fake_user_${email.hashCode()}",
                email = email,
                displayName = displayName.ifBlank { email.substringBefore("@") }
            )
            currentUser = user
            _authState.value = AuthState.Authenticated(user)
            Result.success(SignUpResult.Authenticated(user))
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> {
        if (shouldFailSignIn) {
            _authState.value = AuthState.Error(failureMessage)
            return Result.failure(Exception(failureMessage))
        }

        val user = AuthUser(
            id = "fake_user_${email.hashCode()}",
            email = email,
            displayName = email.substringBefore("@").replaceFirstChar { it.uppercase() }
        )
        currentUser = user
        _authState.value = AuthState.Authenticated(user)
        return Result.success(user)
    }

    override suspend fun signOut(): Result<Unit> {
        if (shouldFailSignOut) {
            return Result.failure(Exception(failureMessage))
        }
        currentUser = null
        _authState.value = AuthState.Unauthenticated
        return Result.success(Unit)
    }

    override suspend fun resetPasswordForEmail(email: String): Result<Unit> {
        return Result.success(Unit)
    }

    override suspend fun getCurrentUser(): AuthUser? = currentUser
}
