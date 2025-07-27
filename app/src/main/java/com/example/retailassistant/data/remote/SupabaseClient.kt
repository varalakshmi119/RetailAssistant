package com.example.retailassistant.data.remote

import com.example.retailassistant.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage

/**
 * A factory function to create and configure the SupabaseClient singleton.
 * This centralizes client setup and ensures session persistence is correctly handled.
 */
fun createSupabaseClient(): SupabaseClient {
    return createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        // Install the Auth module with session persistence.
        // This is CRITICAL for keeping the user logged in even after the app is killed.
        // It will use SharedPreferences on Android to store the session.
        install(Auth)
        install(Postgrest)
        install(Storage)
    }
}
