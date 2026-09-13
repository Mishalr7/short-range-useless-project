package com.shortrange.app.webrtc.signaling

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PresencePayload(
    val participant_id: String,
    val role: String
)

@Serializable
data class BroadcastEnvelope(
    val from: String,
    val to: String,
    val data: JsonObject
)

@Serializable
data class SdpPayload(
    val type: String,
    val sdp: String
)

@Serializable
data class IceCandidatePayload(
    val candidate: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0
)

data class IceCandidateModel(
    val sdpMid: String?,
    val sdpMLineIndex: Int,
    val sdp: String
)
