package com.example.data.model

data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String = ""
)

sealed class AuthState {
    object Initializing : AuthState()
    object Unauthenticated : AuthState()
    object Authenticating : AuthState()
    data class Authenticated(val user: AuthUser) : AuthState()
    data class Error(val message: String) : AuthState()
}

sealed class SignUpResult {
    data class Authenticated(val user: AuthUser) : SignUpResult()
    data class ConfirmationRequired(val email: String) : SignUpResult()
}
