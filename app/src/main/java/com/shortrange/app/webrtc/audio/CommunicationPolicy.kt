package com.shortrange.app.webrtc.audio

import com.shortrange.app.proximity.model.ProximityZone

/**
 * Represents the communication channel quality computed from proximity.
 *
 * Quality levels per SHORT RANGE spec:
 * 100: Clean normal voice. No intentional degradation.
 *  80: Slight degradation. Occasional brief frame dropout.
 *  60: Noticeable degradation. Intermittent audio interruption.
 *  30: Heavy degradation. Frequent short interruptions / dropouts.
 *  10: Severe degradation. Barely understandable fragments.
 *   0: Communication Lost. Audio disabled.
 */
data class CommunicationQuality(
    val qualityScore: Int,
    val zone: ProximityZone,
    val label: String,
    val onPeriodMs: Long,
    val offPeriodMs: Long
)

object CommunicationPolicy {

    fun evaluate(zone: ProximityZone): CommunicationQuality {
        return when (zone) {
            ProximityZone.VERY_CLOSE -> CommunicationQuality(
                qualityScore = 100,
                zone = zone,
                label = "NORMAL",
                onPeriodMs = Long.MAX_VALUE,
                offPeriodMs = 0L
            )
            ProximityZone.CLOSE -> CommunicationQuality(
                qualityScore = 80,
                zone = zone,
                label = "SLIGHT DEGRADATION",
                onPeriodMs = 900L,
                offPeriodMs = 70L
            )
            ProximityZone.DRIFTING -> CommunicationQuality(
                qualityScore = 60,
                zone = zone,
                label = "NOTICEABLE DEGRADATION",
                onPeriodMs = 420L,
                offPeriodMs = 160L
            )
            ProximityZone.FAR -> CommunicationQuality(
                qualityScore = 30,
                zone = zone,
                label = "HEAVY DEGRADATION",
                onPeriodMs = 160L,
                offPeriodMs = 180L
            )
            ProximityZone.CRITICAL -> CommunicationQuality(
                qualityScore = 10,
                zone = zone,
                label = "SEVERE DEGRADATION",
                onPeriodMs = 60L,
                offPeriodMs = 220L
            )
            ProximityZone.LOST -> CommunicationQuality(
                qualityScore = 0,
                zone = zone,
                label = "COMMUNICATION LOST",
                onPeriodMs = 0L,
                offPeriodMs = Long.MAX_VALUE
            )
        }
    }
}
