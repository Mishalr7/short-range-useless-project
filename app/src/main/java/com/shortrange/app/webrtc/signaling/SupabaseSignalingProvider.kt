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
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

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

        private fun extractCandidateType(sdp: String): String {
            val match = Regex("""\btyp\s+(\w+)""").find(sdp)
            return match?.groupValues?.get(1)?.lowercase() ?: "unknown"
        }

        private fun JsonObject.getStringOrNull(key: String): String? {
            return (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }

        private fun JsonObject.getIntOrNull(key: String): Int? {
            return (this[key] as? JsonPrimitive)?.content?.toIntOrNull()
        }
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
        this.participantId = participantId.trim()
        this.peerParticipantId = peerParticipantId?.trim()
        this.role = role.trim()
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

            // 2. Create the channel with explicit presence key and broadcast config
            val ch = realtime.channel(channelTopic) {
                presence {
                    key = participantId
                }
                broadcast {
                    receiveOwnBroadcasts = false
                    acknowledgeBroadcasts = true
                }
            }
            channel = ch

            // 3. Register flow collectors before subscribing

            // Presence diff flow: detect peer joins & leaves
            ch.presenceChangeFlow().onEach { diff ->
                diff.joins.forEach { (rawKey, rawData) ->
                    try {
                        val key = rawKey.toString()
                        val stateParticipantId = rawData.state.getStringOrNull("participant_id")
                            ?: rawData.state.getStringOrNull("participantId")
                        val stateRole = rawData.state.getStringOrNull("role") ?: "PEER"

                        val peerId = when {
                            !stateParticipantId.isNullOrBlank() && !stateParticipantId.equals(participantId, ignoreCase = true) -> stateParticipantId
                            key.isNotBlank() && !key.equals(participantId, ignoreCase = true) -> key
                            else -> null
                        }

                        if (peerId != null) {
                            Log.i(TAG, "Peer presence joined: $peerId ($stateRole)")
                            peerParticipantId = peerId
                            flushPendingCandidates()
                            _events.emit(
                                SignalingEvent.PeerPresenceJoined(
                                    peerParticipantId = peerId,
                                    peerRole = stateRole
                                )
                            )
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to parse presence join: ${e.message}")
                    }
                }

                diff.leaves.forEach { (rawKey, rawData) ->
                    val key = rawKey.toString()
                    val stateParticipantId = rawData.state.getStringOrNull("participant_id")
                    val peerId = stateParticipantId ?: key
                    if (peerId.isNotBlank() && !peerId.equals(participantId, ignoreCase = true)) {
                        Log.w(TAG, "Peer presence left: $peerId")
                        _events.emit(
                            SignalingEvent.PeerPresenceLeft(
                                peerParticipantId = peerId
                            )
                        )
                    }
                }
            }.launchIn(scope)

            // Presence data flow (peer already present in channel)
            ch.presenceDataFlow<JsonObject>().onEach { presences ->
                for (presence in presences) {
                    val pId = presence.getStringOrNull("participant_id") ?: presence.getStringOrNull("participantId")
                    val pRole = presence.getStringOrNull("role") ?: "PEER"
                    if (!pId.isNullOrBlank() && !pId.equals(participantId, ignoreCase = true)) {
                        Log.i(TAG, "Peer presence detected in sync: $pId ($pRole)")
                        peerParticipantId = pId
                        flushPendingCandidates()
                        _events.emit(
                            SignalingEvent.PeerPresenceJoined(
                                peerParticipantId = pId,
                                peerRole = pRole
                            )
                        )
                    }
                }
            }.launchIn(scope)

            // Broadcast: webrtc_offer (using JsonObject to avoid any silent deserialization failure)
            ch.broadcastFlow<JsonObject>(EVENT_OFFER).onEach { obj ->
                try {
                    val from = obj.getStringOrNull("from") ?: return@onEach
                    val to = obj.getStringOrNull("to") ?: ""
                    val data = obj["data"]?.jsonObject ?: return@onEach

                    Log.d(TAG, "RAW_BROADCAST: $EVENT_OFFER from=$from to=$to")

                    if (from.trim().equals(participantId.trim(), ignoreCase = true)) return@onEach
                    if (to.isNotBlank() && !to.trim().equals(participantId.trim(), ignoreCase = true)) {
                        Log.d(TAG, "Ignoring $EVENT_OFFER meant for $to (my id is $participantId)")
                        return@onEach
                    }

                    val sdp = data.getStringOrNull("sdp") ?: ""
                    if (sdp.isNotBlank()) {
                        Log.i(TAG, "OFFER_RECEIVED: from=$from, to=$to")
                        peerParticipantId = from
                        flushPendingCandidates()
                        _events.emit(
                            SignalingEvent.OfferReceived(
                                sdp = sdp,
                                fromParticipantId = from
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing $EVENT_OFFER: ${e.message}", e)
                }
            }.launchIn(scope)

            // Broadcast: webrtc_answer
            ch.broadcastFlow<JsonObject>(EVENT_ANSWER).onEach { obj ->
                try {
                    val from = obj.getStringOrNull("from") ?: return@onEach
                    val to = obj.getStringOrNull("to") ?: ""
                    val data = obj["data"]?.jsonObject ?: return@onEach

                    Log.d(TAG, "RAW_BROADCAST: $EVENT_ANSWER from=$from to=$to")

                    if (from.trim().equals(participantId.trim(), ignoreCase = true)) return@onEach
                    if (to.isNotBlank() && !to.trim().equals(participantId.trim(), ignoreCase = true)) {
                        Log.d(TAG, "Ignoring $EVENT_ANSWER meant for $to (my id is $participantId)")
                        return@onEach
                    }

                    val sdp = data.getStringOrNull("sdp") ?: ""
                    if (sdp.isNotBlank()) {
                        Log.i(TAG, "ANSWER_RECEIVED: from=$from, to=$to")
                        peerParticipantId = from
                        flushPendingCandidates()
                        _events.emit(
                            SignalingEvent.AnswerReceived(
                                sdp = sdp,
                                fromParticipantId = from
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing $EVENT_ANSWER: ${e.message}", e)
                }
            }.launchIn(scope)

            // Broadcast: ice_candidate
            ch.broadcastFlow<JsonObject>(EVENT_ICE).onEach { obj ->
                try {
                    val from = obj.getStringOrNull("from") ?: return@onEach
                    val to = obj.getStringOrNull("to") ?: ""
                    val data = obj["data"]?.jsonObject ?: return@onEach

                    if (from.trim().equals(participantId.trim(), ignoreCase = true)) return@onEach
                    if (to.isNotBlank() && !to.trim().equals(participantId.trim(), ignoreCase = true)) {
                        return@onEach
                    }

                    val candidate = data.getStringOrNull("candidate") ?: ""
                    val sdpMid = data.getStringOrNull("sdpMid")
                    val sdpMLineIndex = data.getIntOrNull("sdpMLineIndex") ?: 0

                    if (candidate.isNotBlank()) {
                        val type = extractCandidateType(candidate)
                        Log.i(TAG, "ICE_CANDIDATE_RECEIVED: from=$from, mid=$sdpMid, type=$type")
                        _events.emit(
                            SignalingEvent.IceCandidateReceived(
                                candidate = IceCandidateModel(
                                    sdpMid = sdpMid,
                                    sdpMLineIndex = sdpMLineIndex,
                                    sdp = candidate
                                ),
                                fromParticipantId = from
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing $EVENT_ICE: ${e.message}", e)
                }
            }.launchIn(scope)

            // 4. Subscribe — blocks until channel reaches SUBSCRIBED or times out
            val subscribed = withTimeoutOrNull(10000L) {
                ch.subscribe(blockUntilSubscribed = true)
                true
            } ?: false

            if (subscribed) {
                Log.i(TAG, "Realtime channel SUBSCRIBED: $channelTopic")
            } else {
                Log.w(TAG, "Channel subscribe timed out for $channelTopic, falling back to non-blocking subscribe")
                ch.subscribe(blockUntilSubscribed = false)
            }

            // 5. Track Presence
            val presenceJson = buildJsonObject {
                put("participant_id", participantId)
                put("role", role)
            }
            ch.track(presenceJson)
            Log.i(TAG, "Presence tracked for participant: $participantId ($role)")

        } catch (e: Exception) {
            Log.e(TAG, "Realtime connect error: ${e.message}", e)
            _events.emit(SignalingEvent.Error("Realtime connection error: ${e.message}"))
        }
    }

    override suspend fun sendOffer(sdp: String) {
        val ch = channel ?: return
        val target = peerParticipantId ?: ""
        Log.i(TAG, "OFFER_SENT: to=$target, sdpLength=${sdp.length}")

        val envelope = buildJsonObject {
            put("from", participantId)
            put("to", target)
            put("data", buildJsonObject {
                put("type", "offer")
                put("sdp", sdp)
            })
        }
        ch.broadcast(EVENT_OFFER, envelope)
    }

    override suspend fun sendAnswer(sdp: String) {
        val ch = channel ?: return
        val target = peerParticipantId ?: ""
        Log.i(TAG, "ANSWER_SENT: to=$target, sdpLength=${sdp.length}")

        val envelope = buildJsonObject {
            put("from", participantId)
            put("to", target)
            put("data", buildJsonObject {
                put("type", "answer")
                put("sdp", sdp)
            })
        }
        ch.broadcast(EVENT_ANSWER, envelope)
    }

    override suspend fun sendIceCandidate(candidate: IceCandidateModel) {
        val target = peerParticipantId
        val ch = channel
        if (target == null || ch == null) {
            Log.d(TAG, "Buffering outgoing ICE candidate (target=$target, channelReady=${ch != null})")
            pendingOutgoingCandidates.add(candidate)
            return
        }

        val type = extractCandidateType(candidate.sdp)
        Log.i(TAG, "ICE_CANDIDATE_SENT: to=$target, mid=${candidate.sdpMid}, type=$type")

        val envelope = buildJsonObject {
            put("from", participantId)
            put("to", target)
            put("data", buildJsonObject {
                put("candidate", candidate.sdp)
                if (candidate.sdpMid != null) put("sdpMid", candidate.sdpMid)
                put("sdpMLineIndex", candidate.sdpMLineIndex)
            })
        }
        ch.broadcast(EVENT_ICE, envelope)
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
