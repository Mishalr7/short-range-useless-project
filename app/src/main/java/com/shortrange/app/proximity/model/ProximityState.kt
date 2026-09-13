package com.shortrange.app.proximity.model

/**
 * The six discrete proximity states defined by the SHORT RANGE specification.
 * Thresholds are relative to the calibrated reference signal.
 */
enum class ProximityZone(val label: String) {
    VERY_CLOSE("VERY CLOSE"),
    CLOSE("CLOSE"),
    DRIFTING("DRIFTING"),
    FAR("FAR"),
    CRITICAL("CRITICAL"),
    LOST("LOST")
}

/**
 * Snapshot of the current proximity state, filtered RSSI, and telemetry metadata.
 */
data class ProximityTelemetry(
    val zone: ProximityZone = ProximityZone.LOST,
    val filteredRssi: Int = -100,
    val rawRssi: Int = -100,
    val referenceRssi: Int = -47,
    val deltaRssi: Int = 53,
    val lastSeenTimestamp: Long = 0L,
    val isCalibrated: Boolean = false,
    val isPeerPresent: Boolean = false,
    val isSimulated: Boolean = false,
    val statusMessage: String = "IDLE"
)
