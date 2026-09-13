package com.shortrange.app.webrtc.audio

import android.util.Log
import com.shortrange.app.proximity.model.ProximityZone
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.webrtc.AudioTrack

/**
 * Deterministic audio degradation controller for WebRTC audio playback.
 * Connects CommunicationQuality to the physical WebRTC audio track without
 * damaging the underlying PeerConnection or ICE transport.
 */
class AudioDegradationEngine {

    companion object {
        private const val TAG = "AudioDegradation"
    }

    private var remoteAudioTrack: AudioTrack? = null
    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var degradationJob: Job? = null

    private val _currentQuality = MutableStateFlow(CommunicationPolicy.evaluate(ProximityZone.VERY_CLOSE))
    val currentQuality: StateFlow<CommunicationQuality> = _currentQuality.asStateFlow()

    @Synchronized
    fun attachRemoteAudioTrack(track: AudioTrack?) {
        remoteAudioTrack = track
        Log.i(TAG, "Attached remote AudioTrack: id=${track?.id()}, enabled=${track?.enabled()}")
        applyQuality(_currentQuality.value)
    }

    @Synchronized
    fun updateProximityZone(zone: ProximityZone) {
        val newQuality = CommunicationPolicy.evaluate(zone)
        if (_currentQuality.value != newQuality) {
            Log.i(TAG, "Quality transition: ${_currentQuality.value.qualityScore} -> ${newQuality.qualityScore} (${zone.name})")
            _currentQuality.value = newQuality
            applyQuality(newQuality)
        }
    }

    @Synchronized
    private fun applyQuality(quality: CommunicationQuality) {
        degradationJob?.cancel()
        degradationJob = null

        val track = remoteAudioTrack ?: return

        when (quality.qualityScore) {
            100 -> {
                // Clean normal voice: no intentional degradation
                try {
                    track.setEnabled(true)
                    track.setVolume(1.0)
                } catch (e: Exception) {
                    Log.w(TAG, "Error applying clean audio: ${e.message}")
                }
            }
            80, 60, 30, 10 -> {
                // Controlled deterministic dropout / frame interruption cycle
                degradationJob = scope.launch {
                    val onMs = quality.onPeriodMs
                    val offMs = quality.offPeriodMs
                    try {
                        track.setVolume(1.0)
                        while (isActive) {
                            track.setEnabled(true)
                            delay(onMs)
                            if (!isActive) break
                            track.setEnabled(false)
                            delay(offMs)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Degradation loop exception: ${e.message}")
                    } finally {
                        // If job was cancelled due to recovery to 100, ensure track is re-enabled
                        if (_currentQuality.value.qualityScore == 100) {
                            try {
                                track.setEnabled(true)
                            } catch (ignored: Exception) {}
                        }
                    }
                }
            }
            0 -> {
                // Communication Lost: silence audio completely
                try {
                    track.setEnabled(false)
                    track.setVolume(0.0)
                } catch (e: Exception) {
                    Log.w(TAG, "Error silencing audio for LOST state: ${e.message}")
                }
            }
        }
    }

    @Synchronized
    fun reset() {
        degradationJob?.cancel()
        degradationJob = null
        remoteAudioTrack?.let { track ->
            try {
                track.setEnabled(true)
                track.setVolume(1.0)
            } catch (e: Exception) {
                Log.w(TAG, "Error resetting track: ${e.message}")
            }
        }
        remoteAudioTrack = null
        _currentQuality.value = CommunicationPolicy.evaluate(ProximityZone.VERY_CLOSE)
    }
}
