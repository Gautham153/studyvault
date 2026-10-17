package com.example.data.repository

import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.example.data.model.SignUpResult
import kotlinx.coroutines.flow.StateFlow

interface AuthRepository {
    val authState: StateFlow<AuthState>

    suspend fun signUp(email: String, password: String, displayName: String): Result<SignUpResult>
    suspend fun signIn(email: String, password: String): Result<AuthUser>
    suspend fun signOut(): Result<Unit>
    suspend fun resetPasswordForEmail(email: String): Result<Unit>
    suspend fun getCurrentUser(): AuthUser?
    fun checkSession()
}
