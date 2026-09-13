package com.shortrange.app.supabase

import android.util.Log
import com.shortrange.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Manages the SupabaseClient instance and session lifecycle RPC calls.
 * Reads public URL and anon key securely from BuildConfig.
 */
object SupabaseManager {
    private const val TAG = "SupabaseManager"

    val isConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.trim().isNotBlank() &&
                BuildConfig.SUPABASE_ANON_KEY.trim().isNotBlank() &&
                !BuildConfig.SUPABASE_ANON_KEY.contains("PASTE_PUBLIC_KEY")

    val client: SupabaseClient by lazy {
        check(isConfigured) {
            "Supabase credentials not configured. Please add supabase.url and supabase.anon.key to local.properties."
        }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            httpEngine = OkHttp.create()
            install(Postgrest)
            install(Realtime)
        }
    }

    var activeSession: ActiveSession? = null
        private set

    suspend fun createSession(): Result<CreateSessionResponse> = runCatching {
        if (!isConfigured) {
            error("Supabase credentials missing. Please set supabase.anon.key in local.properties.")
        }
        Log.i(TAG, "Calling RPC create_session()...")
        val response = client.postgrest.rpc("create_session").decodeAs<CreateSessionResponse>()
        Log.i(TAG, "create_session() succeeded: code=${response.session_code}, participant=${response.participant_id}")

        activeSession = ActiveSession(
            sessionId = response.session_id,
            sessionCode = response.session_code.trim().uppercase(),
            participantId = response.participant_id,
            role = response.role
        )
        response
    }

    suspend fun joinSession(sessionCode: String): Result<JoinSessionResponse> = runCatching {
        if (!isConfigured) {
            error("Supabase credentials missing. Please set supabase.anon.key in local.properties.")
        }
        val cleanCode = sessionCode.trim().uppercase()
        Log.i(TAG, "Calling RPC join_session('$cleanCode')...")
        val params = buildJsonObject {
            put("input_session_code", cleanCode)
        }
        val response = client.postgrest.rpc("join_session", params).decodeAs<JoinSessionResponse>()
        Log.i(TAG, "join_session() succeeded: session=${response.session_id}, participant=${response.participant_id}")

        activeSession = ActiveSession(
            sessionId = response.session_id,
            sessionCode = response.session_code.trim().uppercase(),
            participantId = response.participant_id,
            role = response.role
        )
        response
    }

    suspend fun leaveSession(): Result<Unit> = runCatching {
        val session = activeSession ?: return@runCatching
        if (!isConfigured) return@runCatching

        Log.i(TAG, "Calling RPC leave_session(session=${session.sessionId}, participant=${session.participantId})...")
        val params = buildJsonObject {
            put("session_id", session.sessionId)
            put("participant_id", session.participantId)
        }
        client.postgrest.rpc("leave_session", params)
        activeSession = null
        Log.i(TAG, "leave_session() completed.")
    }

    fun clearSession() {
        activeSession = null
    }
}
