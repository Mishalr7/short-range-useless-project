package com.shortrange.app.webrtc.signaling

import android.util.Log
import com.shortrange.app.supabase.SupabaseManager
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.broadcastFlow
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.presenceChangeFlow
import io.github.jan.supabase.realtime.presenceDataFlow
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

/**
 * Concrete implementation of SignalingProvider using Supabase Realtime Channels (Presence & Broadcast).
 * Strictly complies with the backend teammate's signaling contract.
 */
class SupabaseSignalingProvider : SignalingProvider {

    companion object {
        private const val TAG = "SupabaseSignaling"
        private const val EVENT_OFFER = "webrtc_offer"
        private const val EVENT_ANSWER = "webrtc_answer"
        private const val EVENT_ICE = "ice_candidate"
    }

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<SignalingEvent> = _events.asSharedFlow()

    private var sessionCode: String = ""
    private var participantId: String = ""
    private var peerParticipantId: String? = null
    private var role: String = ""
    private var isInitiator: Boolean = false

    private var channel: RealtimeChannel? = null
    private var scope = CoroutineScope(Dispatchers.IO + Job())
    private val pendingOutgoingCandidates = mutableListOf<IceCandidateModel>()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    override fun initialize(
        sessionCode: String,
        participantId: String,
        peerParticipantId: String?,
        role: String,
        isInitiator: Boolean
    ) {
        this.sessionCode = sessionCode.trim().uppercase()
        this.participantId = participantId
        this.peerParticipantId = peerParticipantId
        this.role = role
        this.isInitiator = isInitiator
        Log.i(TAG, "Initialized: session=$sessionCode, participant=$participantId, role=$role, isInitiator=$isInitiator")
    }

    override suspend fun connect() {
        if (sessionCode.isBlank() || participantId.isBlank()) {
            _events.emit(SignalingEvent.Error("Cannot connect: session or participant ID is blank"))
            return
        }

        disconnect()
        scope = CoroutineScope(Dispatchers.IO + Job())

        val channelTopic = "session:$sessionCode"
        Log.i(TAG, "Connecting to Realtime channel: $channelTopic")

        try {
            val realtime = SupabaseManager.client.realtime

            // 1. Ensure Realtime WebSocket connection is active
            realtime.connect()

            // 2. Create the channel
            val ch = realtime.channel(channelTopic)
            channel = ch

            // 3. Create all Flow references NOW (on this coroutine context).
            //    This registers internal callbacks inside the channel immediately.
            //    Then launch collection via .onEach{}.launchIn(scope).
            //    (Following the official supabase-kt docs pattern exactly.)

            // Presence diff flow (Case A: peer joins/leaves after us)
            ch.presenceChangeFlow().onEach { diff ->
                diff.joins.values.forEach { rawData ->
                    try {
                        val presence = json.decodeFromJsonElement<PresencePayload>(rawData.state)
                        if (presence.participant_id != participantId) {
                            Log.i(TAG, "Peer presence joined (diff): ${presence.participant_id} (${presence.role})")
                            peerParticipantId = presence.participant_id
                            flushPendingCandidates()
                            _events.emit(
                                SignalingEvent.PeerPresenceJoined(
                                    peerParticipantId = presence.participant_id,
                                    peerRole = presence.role
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse presence join: ${e.message}")
                    }
                }

                diff.leaves.values.forEach { rawData ->
                    try {
                        val presence = json.decodeFromJsonElement<PresencePayload>(rawData.state)
                        if (presence.participant_id != participantId) {
                            Log.i(TAG, "Peer presence left: ${presence.participant_id}")
                            _events.emit(
                                SignalingEvent.PeerPresenceLeft(
                                    peerParticipantId = presence.participant_id
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse presence leave: ${e.message}")
                    }
                }
            }.launchIn(scope)

            // Presence data flow (Case B: peer already present when we join)
            ch.presenceDataFlow<PresencePayload>().onEach { presences ->
                for (presence in presences) {
                    if (presence.participant_id != participantId) {
                        Log.i(TAG, "Peer presence detected in sync: ${presence.participant_id} (${presence.role})")
                        peerParticipantId = presence.participant_id
                        flushPendingCandidates()
                        _events.emit(
                            SignalingEvent.PeerPresenceJoined(
                                peerParticipantId = presence.participant_id,
                                peerRole = presence.role
                            )
                        )
                    }
                }
            }.launchIn(scope)

            // Broadcast: webrtc_offer
            ch.broadcastFlow<BroadcastEnvelope>(EVENT_OFFER).onEach { env ->
                if (env.from == participantId) return@onEach
                if (env.to != participantId) return@onEach

                try {
                    val sdpPayload = json.decodeFromJsonElement<SdpPayload>(env.data)
                    peerParticipantId = env.from
                    flushPendingCandidates()
                    Log.i(TAG, "Received webrtc_offer from ${env.from}")
                    _events.emit(
                        SignalingEvent.OfferReceived(
                            sdp = sdpPayload.sdp,
                            fromParticipantId = env.from
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse webrtc_offer payload: ${e.message}", e)
                }
            }.launchIn(scope)

            // Broadcast: webrtc_answer
            ch.broadcastFlow<BroadcastEnvelope>(EVENT_ANSWER).onEach { env ->
                if (env.from == participantId) return@onEach
                if (env.to != participantId) return@onEach

                try {
                    val sdpPayload = json.decodeFromJsonElement<SdpPayload>(env.data)
                    peerParticipantId = env.from
                    flushPendingCandidates()
                    Log.i(TAG, "Received webrtc_answer from ${env.from}")
                    _events.emit(
                        SignalingEvent.AnswerReceived(
                            sdp = sdpPayload.sdp,
                            fromParticipantId = env.from
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse webrtc_answer payload: ${e.message}", e)
                }
            }.launchIn(scope)

            // Broadcast: ice_candidate
            ch.broadcastFlow<BroadcastEnvelope>(EVENT_ICE).onEach { env ->
                if (env.from == participantId) return@onEach
                if (env.to != participantId) return@onEach

                try {
                    val icePayload = json.decodeFromJsonElement<IceCandidatePayload>(env.data)
                    Log.d(TAG, "Received ice_candidate from ${env.from}")
                    _events.emit(
                        SignalingEvent.IceCandidateReceived(
                            candidate = IceCandidateModel(
                                sdpMid = icePayload.sdpMid,
                                sdpMLineIndex = icePayload.sdpMLineIndex,
                                sdp = icePayload.candidate
                            ),
                            fromParticipantId = env.from
                        )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to parse ice_candidate payload: ${e.message}", e)
                }
            }.launchIn(scope)

            // 4. Subscribe — blocks until channel reaches SUBSCRIBED.
            //    All flow callbacks above are already registered synchronously.
            ch.subscribe(blockUntilSubscribed = true)
            Log.i(TAG, "Realtime channel SUBSCRIBED: $channelTopic")

            // 5. Track Presence (safe: channel is SUBSCRIBED)
            val presenceJson = json.encodeToJsonElement(PresencePayload(participant_id = participantId, role = role)).jsonObject
            ch.track(presenceJson)
            Log.i(TAG, "Presence tracked for participant: $participantId ($role)")

        } catch (e: Exception) {
            Log.e(TAG, "Realtime connect error: ${e.message}", e)
            _events.emit(SignalingEvent.Error("Realtime connection error: ${e.message}"))
        }
    }

    override suspend fun sendOffer(sdp: String) {
        val target = peerParticipantId ?: return
        val ch = channel ?: return
        Log.i(TAG, "Broadcasting webrtc_offer to $target")

        val envelope = BroadcastEnvelope(
            from = participantId,
            to = target,
            data = json.encodeToJsonElement(SdpPayload(type = "offer", sdp = sdp)).jsonObject
        )
        ch.broadcast(EVENT_OFFER, json.encodeToJsonElement(envelope).jsonObject)
    }

    override suspend fun sendAnswer(sdp: String) {
        val target = peerParticipantId ?: return
        val ch = channel ?: return
        Log.i(TAG, "Broadcasting webrtc_answer to $target")

        val envelope = BroadcastEnvelope(
            from = participantId,
            to = target,
            data = json.encodeToJsonElement(SdpPayload(type = "answer", sdp = sdp)).jsonObject
        )
        ch.broadcast(EVENT_ANSWER, json.encodeToJsonElement(envelope).jsonObject)
    }

    override suspend fun sendIceCandidate(candidate: IceCandidateModel) {
        val target = peerParticipantId
        val ch = channel
        if (target == null || ch == null) {
            Log.d(TAG, "Buffering outgoing ICE candidate (target=$target, channelReady=${ch != null})")
            pendingOutgoingCandidates.add(candidate)
            return
        }
        Log.d(TAG, "Broadcasting ice_candidate to $target")

        val envelope = BroadcastEnvelope(
            from = participantId,
            to = target,
            data = json.encodeToJsonElement(
                IceCandidatePayload(
                    candidate = candidate.sdp,
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex
                )
            ).jsonObject
        )
        ch.broadcast(EVENT_ICE, json.encodeToJsonElement(envelope).jsonObject)
    }

    private suspend fun flushPendingCandidates() {
        if (pendingOutgoingCandidates.isEmpty()) return
        val target = peerParticipantId ?: return
        val ch = channel ?: return
        Log.i(TAG, "Flushing ${pendingOutgoingCandidates.size} buffered ICE candidates to $target")
        val candidates = ArrayList(pendingOutgoingCandidates)
        pendingOutgoingCandidates.clear()
        for (candidate in candidates) {
            sendIceCandidate(candidate)
        }
    }

    override suspend fun disconnect() {
        Log.i(TAG, "Disconnecting Realtime channel")
        try {
            pendingOutgoingCandidates.clear()
            channel?.let { ch ->
                SupabaseManager.client.realtime.removeChannel(ch)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error removing channel: ${e.message}")
        } finally {
            channel = null
        }
    }
}
