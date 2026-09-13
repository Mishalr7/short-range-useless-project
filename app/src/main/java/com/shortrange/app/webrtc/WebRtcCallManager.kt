package com.shortrange.app.webrtc

import android.content.Context
import android.media.AudioManager
import android.util.Log
import com.shortrange.app.webrtc.signaling.IceCandidateModel
import com.shortrange.app.webrtc.signaling.SignalingEvent
import com.shortrange.app.webrtc.signaling.SignalingProvider
import com.shortrange.app.webrtc.signaling.SupabaseSignalingProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.CandidatePairChangeEvent
import org.webrtc.DataChannel
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpReceiver
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Dedicated WebRTC voice call manager for Phase 3.
 *
 * Responsibilities:
 * - Real-time microphone capture with hardware AEC, NS, and AGC.
 * - Unified Plan PeerConnection with Google STUN.
 * - Bidirectional audio streaming over WebRTC.
 * - Decoupled from BLE proximity (audio remains clean).
 * - SignalingProvider integration with Supabase Realtime.
 */
class WebRtcCallManager private constructor(
    private val context: Context,
    private val signalingProvider: SignalingProvider = SupabaseSignalingProvider()
) {
    companion object {
        private const val TAG = "WebRtcCallManager"

        @Volatile
        private var instance: WebRtcCallManager? = null

        fun initialize(context: Context, signaling: SignalingProvider = SupabaseSignalingProvider()): WebRtcCallManager {
            return instance ?: synchronized(this) {
                instance ?: WebRtcCallManager(context.applicationContext, signaling).also { instance = it }
            }
        }

        fun getInstance(): WebRtcCallManager {
            return instance ?: error("WebRtcCallManager must be initialized with Context first.")
        }
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var audioDeviceModule: JavaAudioDeviceModule? = null
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var remoteAudioTrack: AudioTrack? = null

    private val earlyIceCandidates = CopyOnWriteArrayList<IceCandidate>()
    private var isRemoteDescriptionSet = false
    private var isOfferCreated = false

    private var scope = CoroutineScope(Dispatchers.Default + Job())
    private var signalingJob: Job? = null

    private val _callState = MutableStateFlow(CallState.IDLE)
    val callState: StateFlow<CallState> = _callState.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private var currentSessionCode: String = ""
    private var currentParticipantId: String = ""
    private var currentPeerParticipantId: String? = null
    private var isInitiatorRole: Boolean = false

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        try {
            val initOptions = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(initOptions)

            val adm = JavaAudioDeviceModule.builder(context)
                .setUseHardwareAcousticEchoCanceler(true)
                .setUseHardwareNoiseSuppressor(true)
                .createAudioDeviceModule()
            audioDeviceModule = adm

            val options = PeerConnectionFactory.Options()
            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setAudioDeviceModule(adm)
                .createPeerConnectionFactory()

            Log.i(TAG, "PeerConnectionFactory initialized with JavaAudioDeviceModule (AEC/NS enabled)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize PeerConnectionFactory: ${e.message}", e)
        }
    }

    fun startCall(
        sessionCode: String,
        participantId: String,
        peerParticipantId: String?,
        role: String,
        isInitiator: Boolean
    ) {
        Log.i(TAG, "startCall: session=$sessionCode, participant=$participantId, role=$role, isInitiator=$isInitiator")
        endCall() // Clean up any active resources first

        currentSessionCode = sessionCode
        currentParticipantId = participantId
        currentPeerParticipantId = peerParticipantId
        isInitiatorRole = isInitiator
        isRemoteDescriptionSet = false
        isOfferCreated = false
        earlyIceCandidates.clear()

        _callState.value = CallState.CONNECTING

        scope = CoroutineScope(Dispatchers.Default + Job())

        // 1. Configure audio routing
        configureAudio(true)

        // 2. Create audio track & PeerConnection
        createLocalAudioTrack()
        createPeerConnection()

        // 3. Initialize & connect signaling
        signalingProvider.initialize(
            sessionCode = sessionCode,
            participantId = participantId,
            peerParticipantId = peerParticipantId,
            role = role,
            isInitiator = isInitiator
        )

        observeSignalingEvents()

        scope.launch {
            signalingProvider.connect()
        }
    }

    private fun createLocalAudioTrack() {
        val factory = peerConnectionFactory ?: return

        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
        }

        val source = factory.createAudioSource(audioConstraints)
        audioSource = source
        val track = factory.createAudioTrack("ARDAMSa0", source)
        track.setEnabled(!_isMuted.value)
        localAudioTrack = track

        Log.i(TAG, "Local AudioTrack created and enabled: ${!_isMuted.value}")
    }

    private fun createPeerConnection() {
        val factory = peerConnectionFactory ?: return

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: org.webrtc.IceCandidate?) {
                candidate?.let { c ->
                    Log.d(TAG, "Local ICE candidate generated: ${c.sdpMid}")
                    scope.launch {
                        signalingProvider.sendIceCandidate(
                            IceCandidateModel(
                                sdpMid = c.sdpMid,
                                sdpMLineIndex = c.sdpMLineIndex,
                                sdp = c.sdp
                            )
                        )
                    }
                }
            }

            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {
                Log.i(TAG, "WebRTC IceConnectionState: $newState")
                when (newState) {
                    PeerConnection.IceConnectionState.CONNECTED,
                    PeerConnection.IceConnectionState.COMPLETED -> {
                        _callState.value = CallState.CONNECTED
                    }
                    PeerConnection.IceConnectionState.DISCONNECTED -> {
                        Log.w(TAG, "WebRTC IceConnectionState DISCONNECTED")
                    }
                    PeerConnection.IceConnectionState.FAILED -> {
                        Log.e(TAG, "WebRTC IceConnectionState FAILED")
                        _callState.value = CallState.FAILED
                    }
                    else -> Unit
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.i(TAG, "WebRTC PeerConnectionState: $newState")
                when (newState) {
                    PeerConnection.PeerConnectionState.CONNECTED -> _callState.value = CallState.CONNECTED
                    PeerConnection.PeerConnectionState.FAILED -> _callState.value = CallState.FAILED
                    PeerConnection.PeerConnectionState.DISCONNECTED,
                    PeerConnection.PeerConnectionState.CLOSED -> _callState.value = CallState.DISCONNECTED
                    else -> Unit
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is AudioTrack) {
                    Log.i(TAG, "Remote AudioTrack received via Unified Plan")
                    remoteAudioTrack = track
                    track.setEnabled(true)
                    track.setVolume(1.0)
                }
            }

            override fun onSignalingChange(p0: PeerConnection.SignalingState?) {}
            override fun onIceConnectionReceivingChange(p0: Boolean) {}
            override fun onIceGatheringChange(p0: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidatesRemoved(p0: Array<out org.webrtc.IceCandidate>?) {}
            override fun onAddStream(p0: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(p0: org.webrtc.MediaStream?) {}
            override fun onDataChannel(p0: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        // Add local audio track to PeerConnection
        localAudioTrack?.let { track ->
            peerConnection?.addTrack(track, listOf("ARDAMS"))
        }
    }

    private fun observeSignalingEvents() {
        signalingJob?.cancel()
        signalingJob = scope.launch {
            signalingProvider.events.collect { event ->
                when (event) {
                    is SignalingEvent.PeerPresenceJoined -> {
                        currentPeerParticipantId = event.peerParticipantId
                        Log.i(TAG, "Peer presence detected: ${event.peerParticipantId}. isInitiator=$isInitiatorRole, isOfferCreated=$isOfferCreated")
                        if (isInitiatorRole && !isOfferCreated) {
                            createOffer()
                        }
                    }
                    is SignalingEvent.OfferReceived -> {
                        Log.i(TAG, "Offer received from ${event.fromParticipantId}")
                        handleRemoteOffer(event.sdp)
                    }
                    is SignalingEvent.AnswerReceived -> {
                        Log.i(TAG, "Answer received from ${event.fromParticipantId}")
                        handleRemoteAnswer(event.sdp)
                    }
                    is SignalingEvent.IceCandidateReceived -> {
                        handleRemoteIceCandidate(event.candidate)
                    }
                    is SignalingEvent.PeerPresenceLeft -> {
                        Log.w(TAG, "Peer presence left: ${event.peerParticipantId}")
                        _callState.value = CallState.DISCONNECTED
                    }
                    is SignalingEvent.Error -> {
                        Log.e(TAG, "Signaling error: ${event.message}")
                    }
                }
            }
        }
    }

    private fun createOffer() {
        if (isOfferCreated) {
            Log.w(TAG, "createOffer called but offer has already been created. Ignoring duplicate.")
            return
        }
        isOfferCreated = true
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    Log.i(TAG, "Offer SDP created successfully")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "Local description set with Offer")
                            scope.launch {
                                signalingProvider.sendOffer(sdp.description)
                            }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Failed to set local description: $err")
                        }
                    }, sdp)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed to create offer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    private fun handleRemoteOffer(sdpString: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.OFFER, sdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote Offer SDP set successfully")
                isRemoteDescriptionSet = true
                drainEarlyIceCandidates()
                createAnswer()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote offer: $err")
            }
        }, remoteSdp)
    }

    private fun createAnswer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                desc?.let { sdp ->
                    Log.i(TAG, "Answer SDP created successfully")
                    peerConnection?.setLocalDescription(object : SdpObserver {
                        override fun onCreateSuccess(p0: SessionDescription?) {}
                        override fun onSetSuccess() {
                            Log.i(TAG, "Local description set with Answer")
                            scope.launch {
                                signalingProvider.sendAnswer(sdp.description)
                            }
                        }
                        override fun onCreateFailure(p0: String?) {}
                        override fun onSetFailure(err: String?) {
                            Log.e(TAG, "Failed to set local answer description: $err")
                        }
                    }, sdp)
                }
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "Failed to create answer: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, constraints)
    }

    private fun handleRemoteAnswer(sdpString: String) {
        val remoteSdp = SessionDescription(SessionDescription.Type.ANSWER, sdpString)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                Log.i(TAG, "Remote Answer SDP set successfully")
                isRemoteDescriptionSet = true
                drainEarlyIceCandidates()
            }
            override fun onCreateFailure(p0: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "Failed to set remote answer: $err")
            }
        }, remoteSdp)
    }

    private fun handleLocalIceCandidate(candidate: IceCandidate) {
        scope.launch {
            signalingProvider.sendIceCandidate(
                IceCandidateModel(
                    sdpMid = candidate.sdpMid,
                    sdpMLineIndex = candidate.sdpMLineIndex,
                    sdp = candidate.sdp
                )
            )
        }
    }

    private fun handleRemoteIceCandidate(model: IceCandidateModel) {
        val rtcCandidate = IceCandidate(model.sdpMid, model.sdpMLineIndex, model.sdp)
        if (isRemoteDescriptionSet) {
            peerConnection?.addIceCandidate(rtcCandidate)
        } else {
            earlyIceCandidates.add(rtcCandidate)
        }
    }

    private fun drainEarlyIceCandidates() {
        for (candidate in earlyIceCandidates) {
            peerConnection?.addIceCandidate(candidate)
        }
        earlyIceCandidates.clear()
    }

    fun setMuted(muted: Boolean) {
        _isMuted.value = muted
        localAudioTrack?.setEnabled(!muted)
        Log.i(TAG, "Local audio track muted: $muted")
    }

    private fun configureAudio(inCall: Boolean) {
        try {
            if (inCall) {
                audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
                audioManager.isSpeakerphoneOn = true
            } else {
                audioManager.isSpeakerphoneOn = false
                audioManager.mode = AudioManager.MODE_NORMAL
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error configuring audio: ${e.message}")
        }
    }

    fun endCall() {
        Log.i(TAG, "Ending WebRTC call and releasing resources")
        signalingJob?.cancel()
        signalingJob = null

        scope.launch {
            signalingProvider.disconnect()
        }

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error closing PeerConnection: ${e.message}")
        } finally {
            peerConnection = null
        }

        try {
            localAudioTrack?.setEnabled(false)
            localAudioTrack?.dispose()
            audioSource?.dispose()
        } catch (e: Exception) {
            Log.w(TAG, "Error disposing audio tracks: ${e.message}")
        } finally {
            localAudioTrack = null
            audioSource = null
            remoteAudioTrack = null
        }

        configureAudio(false)
        earlyIceCandidates.clear()
        isRemoteDescriptionSet = false
        isOfferCreated = false

        _callState.value = CallState.IDLE
    }
}
