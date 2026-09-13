package com.shortrange.app.supabase

import kotlinx.serialization.Serializable

@Serializable
data class CreateSessionResponse(
    val session_id: String,
    val session_code: String,
    val participant_id: String,
    val role: String,
    val expires_at: String? = null
)

@Serializable
data class JoinSessionResponse(
    val session_id: String,
    val session_code: String,
    val participant_id: String,
    val role: String,
    val status: String? = null
)

data class ActiveSession(
    val sessionId: String,
    val sessionCode: String,
    val participantId: String,
    val role: String, // "HOST" or "GUEST"
    var peerParticipantId: String? = null
) {
    val isHost: Boolean get() = role.equals("HOST", ignoreCase = true)
}
