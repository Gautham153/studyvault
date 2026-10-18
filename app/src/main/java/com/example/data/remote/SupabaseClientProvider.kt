package com.example.data.remote

import android.util.Log
import com.example.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp

object SupabaseClientProvider {
    private const val TAG = "SupabaseClientProvider"

    val supabaseUrl: String by lazy {
        BuildConfig.SUPABASE_URL.trim()
    }

    val supabaseAnonKey: String by lazy {
        BuildConfig.SUPABASE_ANON_KEY.trim()
    }

    val isConfigured: Boolean
        get() {
            val url = supabaseUrl
            val key = supabaseAnonKey
            return url.isNotBlank() &&
                    !url.contains("placeholder", ignoreCase = true) &&
                    (url.startsWith("https://") || url.startsWith("http://")) &&
                    key.isNotBlank() &&
                    !key.contains("placeholder", ignoreCase = true)
        }

    val client: SupabaseClient? by lazy {
        if (!isConfigured) {
            Log.w(TAG, "Supabase credentials are not configured or are placeholder values.")
            null
        } else {
            try {
                createSupabaseClient(
                    supabaseUrl = supabaseUrl,
                    supabaseKey = supabaseAnonKey
                ) {
                    httpEngine = OkHttp.create()
                    install(Auth) {
                        alwaysAutoRefresh = true
                        autoLoadFromStorage = true
                    }
                    install(Postgrest)
                    install(Storage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize Supabase client", e)
                null
            }
        }
    }
}
