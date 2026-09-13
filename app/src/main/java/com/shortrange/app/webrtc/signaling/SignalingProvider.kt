package com.shortrange.app.webrtc.signaling

import kotlinx.coroutines.flow.SharedFlow

/**
 * Clean signaling abstraction decoupling WebRtcCallManager from concrete backend signaling implementations.
 */
interface SignalingProvider {
    val events: SharedFlow<SignalingEvent>

    fun initialize(
        sessionCode: String,
        participantId: String,
        peerParticipantId: String?,
        role: String,
        isInitiator: Boolean
    )

    suspend fun connect()
    suspend fun sendOffer(sdp: String)
    suspend fun sendAnswer(sdp: String)
    suspend fun sendIceCandidate(candidate: IceCandidateModel)
    suspend fun disconnect()
}

sealed interface SignalingEvent {
    data class PeerPresenceJoined(val peerParticipantId: String, val peerRole: String) : SignalingEvent
    data class PeerPresenceLeft(val peerParticipantId: String) : SignalingEvent
    data class OfferReceived(val sdp: String, val fromParticipantId: String) : SignalingEvent
    data class AnswerReceived(val sdp: String, val fromParticipantId: String) : SignalingEvent
    data class IceCandidateReceived(val candidate: IceCandidateModel, val fromParticipantId: String) : SignalingEvent
    data class Error(val message: String) : SignalingEvent
}
