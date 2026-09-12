package com.shortrange.app.model

enum class CallQualityState {
    GOOD,
    DEGRADING,
    CRITICAL
}

enum class FaultType(
    val code: String,
    val title: String,
    val message: String,
    val recommendation: String,
    val isProximityIssue: Boolean
) {
    PROXIMITY_FAULT_04(
        code = "FAULT 04",
        title = "COMMUNICATION LOST",
        message = "PROXIMITY LIMIT EXCEEDED. COMMUNICATION CHANNEL TERMINATED.",
        recommendation = "REDUCE SEPARATION.",
        isProximityIssue = true
    ),
    NETWORK_FAULT_02(
        code = "FAULT 02",
        title = "COMMUNICATION LOST",
        message = "VOICE CHANNEL FAILURE. CHECK NETWORK CONNECTION (FAULT 02).",
        recommendation = "THIS DOES NOT APPEAR TO BE A PROXIMITY ISSUE.",
        isProximityIssue = false
    )
}

data class TelemetryData(
    val integrity: Int,
    val proximityText: String,
    val audioText: String,
    val latencyMs: Int,
    val frameLossPct: Int,
    val separationRatio: Float // 0.0f (together) to 1.0f (exceeded)
)

object MockTelemetryProvider {
    val Good = TelemetryData(
        integrity = 100,
        proximityText = "VERY CLOSE",
        audioText = "GOOD",
        latencyMs = 42,
        frameLossPct = 0,
        separationRatio = 0.15f
    )

    val Degrading = TelemetryData(
        integrity = 61,
        proximityText = "DRIFTING",
        audioText = "DEGRADED",
        latencyMs = 184,
        frameLossPct = 12,
        separationRatio = 0.55f
    )

    val Critical = TelemetryData(
        integrity = 9,
        proximityText = "CRITICAL",
        audioText = "SEVERELY DEGRADED",
        latencyMs = 420,
        frameLossPct = 67,
        separationRatio = 0.90f
    )
}
