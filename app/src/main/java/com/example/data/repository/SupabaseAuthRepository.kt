package com.example.data.repository

import android.util.Log
import com.example.data.model.AuthState
import com.example.data.model.AuthUser
import com.example.data.model.SignUpResult
import com.example.data.remote.SupabaseClientProvider
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

class SupabaseAuthRepository private constructor() : AuthRepository {

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val _authState = MutableStateFlow<AuthState>(AuthState.Initializing)
    override val authState: StateFlow<AuthState> = _authState.asStateFlow()

    init {
        initSessionObservation()
    }

    private fun initSessionObservation() {
        val client = SupabaseClientProvider.client
        if (client != null) {
            scope.launch {
                try {
                    client.auth.sessionStatus.collect { status ->
                        when (status) {
                            is SessionStatus.Authenticated -> {
                                val user = client.auth.currentUserOrNull()
                                if (user != null) {
                                    val displayName = user.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                                        ?: user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                                        ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                                        ?: "Student"
                                    _authState.value = AuthState.Authenticated(
                                        AuthUser(
                                            id = user.id,
                                            email = user.email ?: "",
                                            displayName = displayName
                                        )
                                    )
                                } else {
                                    _authState.value = AuthState.Unauthenticated
                                }
                            }
                            is SessionStatus.NotAuthenticated -> {
                                _authState.value = AuthState.Unauthenticated
                            }
                            is SessionStatus.Initializing -> {
                                _authState.value = AuthState.Initializing
                            }
                            else -> {
                                _authState.value = AuthState.Unauthenticated
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("SupabaseAuthRepo", "Error observing sessionStatus", e)
                    _authState.value = AuthState.Unauthenticated
                }
            }
        } else {
            // When Supabase is not configured, set to Unauthenticated
            _authState.value = AuthState.Unauthenticated
        }
    }

    override fun checkSession() {
        val client = SupabaseClientProvider.client
        if (client != null) {
            val session = client.auth.currentSessionOrNull()
            val user = client.auth.currentUserOrNull()
            if (session != null && user != null) {
                val displayName = user.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: "Student"
                _authState.value = AuthState.Authenticated(
                    AuthUser(id = user.id, email = user.email ?: "", displayName = displayName)
                )
            } else {
                _authState.value = AuthState.Unauthenticated
            }
        } else {
            _authState.value = AuthState.Unauthenticated
        }
    }

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): Result<SignUpResult> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in your configuration.")
            )

        _authState.value = AuthState.Authenticating
        try {
            client.auth.signUpWith(Email) {
                this.email = email
                this.password = password
                this.data = buildJsonObject {
                    put("name", displayName)
                    put("full_name", displayName)
                }
            }

            val session = client.auth.currentSessionOrNull()
            val user = client.auth.currentUserOrNull()

            if (session != null && user != null) {
                // Email confirmation is disabled; genuine active session established immediately
                val resolvedName = user.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: displayName.ifBlank { email.substringBefore("@").replaceFirstChar { it.uppercase() } }

                val authUser = AuthUser(
                    id = user.id,
                    email = user.email ?: email,
                    displayName = resolvedName
                )
                _authState.value = AuthState.Authenticated(authUser)
                Result.success(SignUpResult.Authenticated(authUser))
            } else {
                // Email confirmation is required; no active session yet
                _authState.value = AuthState.Unauthenticated
                Result.success(SignUpResult.ConfirmationRequired(email))
            }
        } catch (e: Exception) {
            _authState.value = AuthState.Unauthenticated
            val friendlyMessage = mapSupabaseError(e)
            Result.failure(Exception(friendlyMessage, e))
        }
    }

    override suspend fun signIn(email: String, password: String): Result<AuthUser> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.client
                ?: return@withContext Result.failure(
                    IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in your configuration.")
                )

            _authState.value = AuthState.Authenticating
            try {
                client.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }

                val session = client.auth.currentSessionOrNull()
                val user = client.auth.currentUserOrNull()

                if (session != null && user != null) {
                    val name = user.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                        ?: user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                        ?: email.substringBefore("@").replaceFirstChar { it.uppercase() }

                    val authUser = AuthUser(
                        id = user.id,
                        email = user.email ?: email,
                        displayName = name
                    )
                    _authState.value = AuthState.Authenticated(authUser)
                    Result.success(authUser)
                } else {
                    _authState.value = AuthState.Unauthenticated
                    Result.failure(IllegalStateException("No active session returned after sign in."))
                }
            } catch (e: Exception) {
                val friendlyMessage = mapSupabaseError(e)
                _authState.value = AuthState.Error(friendlyMessage)
                Result.failure(Exception(friendlyMessage, e))
            }
        }

    override suspend fun signOut(): Result<Unit> = withContext(Dispatchers.IO) {
        val client = SupabaseClientProvider.client
            ?: return@withContext Result.failure(
                IllegalStateException("Supabase is not configured.")
            )

        try {
            client.auth.signOut()
            val session = try { client.auth.currentSessionOrNull() } catch (_: Exception) { null }
            val user = try { client.auth.currentUserOrNull() } catch (_: Exception) { null }
            if (session == null || user == null) {
                _authState.value = AuthState.Unauthenticated
                StudyVaultRepositoryProvider.repository.clearData()
                StudyMaterialRepositoryProvider.repository.clearData()
                Result.success(Unit)
            } else {
                val displayName = user.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: "Student"
                _authState.value = AuthState.Authenticated(
                    AuthUser(id = user.id, email = user.email ?: "", displayName = displayName)
                )
                Result.failure(IllegalStateException("Sign out failed to invalidate session."))
            }
        } catch (e: Exception) {
            Log.e("SupabaseAuthRepo", "Error during Supabase signOut", e)
            val friendly = mapSupabaseError(e)

            val session = try { client.auth.currentSessionOrNull() } catch (_: Exception) { null }
            val user = try { client.auth.currentUserOrNull() } catch (_: Exception) { null }
            if (session != null && user != null) {
                // Session still exists: retain authenticated state, do not clear data.
                val displayName = user.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: user.userMetadata?.get("full_name")?.jsonPrimitive?.contentOrNull
                    ?: user.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: "Student"
                _authState.value = AuthState.Authenticated(
                    AuthUser(id = user.id, email = user.email ?: "", displayName = displayName)
                )
            } else if (_authState.value is AuthState.Authenticated) {
                // If we were authenticated and sign-out threw an exception, retain authenticated state as invalidation is unconfirmed
                Log.w("SupabaseAuthRepo", "Sign-out threw exception; retaining authenticated state as session invalidation unconfirmed.")
            } else {
                _authState.value = AuthState.Unauthenticated
                StudyVaultRepositoryProvider.repository.clearData()
                StudyMaterialRepositoryProvider.repository.clearData()
            }

            Result.failure(Exception(friendly, e))
        }
    }

    override suspend fun resetPasswordForEmail(email: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            val client = SupabaseClientProvider.client
                ?: return@withContext Result.failure(
                    IllegalStateException("Supabase is not configured. Please set SUPABASE_URL and SUPABASE_ANON_KEY in your configuration.")
                )

            try {
                client.auth.resetPasswordForEmail(email = email)
                Result.success(Unit)
            } catch (e: Exception) {
                val friendly = mapSupabaseError(e)
                Result.failure(Exception(friendly, e))
            }
        }

    override suspend fun getCurrentUser(): AuthUser? = withContext(Dispatchers.IO) {
        when (val state = _authState.value) {
            is AuthState.Authenticated -> state.user
            else -> {
                val client = SupabaseClientProvider.client ?: return@withContext null
                val session = client.auth.currentSessionOrNull() ?: return@withContext null
                val u = client.auth.currentUserOrNull() ?: return@withContext null
                val name = u.userMetadata?.get("name")?.jsonPrimitive?.contentOrNull
                    ?: u.email?.substringBefore("@")?.replaceFirstChar { it.uppercase() }
                    ?: "Student"
                AuthUser(id = u.id, email = u.email ?: "", displayName = name)
            }
        }
    }

    private fun mapSupabaseError(e: Throwable): String {
        val message = e.message ?: ""
        val lower = message.lowercase()
        return when {
            lower.contains("invalid login credentials") || lower.contains("invalid_grant") || lower.contains("invalid email or password") ->
                "Email or password is incorrect."
            lower.contains("user already registered") || lower.contains("already exists") || lower.contains("user_already_exists") ->
                "An account with this email already exists."
            lower.contains("password should be at least") || lower.contains("password is too short") ->
                "Password must be at least 6 characters."
            lower.contains("email not confirmed") || lower.contains("email_not_confirmed") ->
                "Email has not been confirmed yet. Please verify your email before logging in."
            lower.contains("rate limit") || lower.contains("over_email_send_rate_limit") ->
                "Too many attempts. Please wait a moment and try again."
            lower.contains("network") || lower.contains("timeout") || lower.contains("unable to resolve host") || lower.contains("connect") ->
                "Please check your internet connection and try again."
            lower.contains("invalid email") || lower.contains("email address is invalid") ->
                "Please enter a valid email address."
            else ->
                e.message?.takeIf { it.isNotBlank() } ?: "Authentication failed. Please check your credentials and try again."
        }
    }

    companion object {
        val instance: SupabaseAuthRepository by lazy { SupabaseAuthRepository() }
    }
}
