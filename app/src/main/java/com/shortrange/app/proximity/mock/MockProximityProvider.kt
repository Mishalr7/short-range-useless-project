package com.shortrange.app.proximity.mock

import com.shortrange.app.proximity.BleRole
import com.shortrange.app.proximity.ProximityProvider
import com.shortrange.app.proximity.model.ProximityTelemetry
import com.shortrange.app.proximity.model.ProximityZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Deterministic mock proximity provider for testing, emulation, and hackathon presentation fallback.
 */
class MockProximityProvider : ProximityProvider {

    private var currentReferenceRssi: Int = -47
    private var isCalibrated: Boolean = false

    private val _telemetry = MutableStateFlow(
        createTelemetryForZone(ProximityZone.VERY_CLOSE, currentReferenceRssi, isCalibrated)
    )
    override val telemetry: StateFlow<ProximityTelemetry> = _telemetry.asStateFlow()

    override fun start(role: BleRole, sessionCode: String?) {
        _telemetry.value = _telemetry.value.copy(
            statusMessage = "SIMULATOR ACTIVE (${role.name})"
        )
    }

    override fun stop() {
        isCalibrated = false
        _telemetry.value = _telemetry.value.copy(
            isCalibrated = false,
            statusMessage = "SIMULATOR STOPPED"
        )
    }

    override fun calibrateReference(customRefRssi: Int?) {
        currentReferenceRssi = customRefRssi ?: -47
        isCalibrated = true
        _telemetry.value = _telemetry.value.copy(
            referenceRssi = currentReferenceRssi,
            isCalibrated = true,
            statusMessage = "REFERENCE ESTABLISHED ($currentReferenceRssi dBm)"
        )
    }

    override fun setSimulationZone(zone: ProximityZone) {
        _telemetry.value = createTelemetryForZone(zone, currentReferenceRssi, isCalibrated)
    }

    override fun isSimulated(): Boolean = true

    private fun createTelemetryForZone(
        zone: ProximityZone,
        refRssi: Int,
        calibrated: Boolean
    ): ProximityTelemetry {
        val (filteredRssi, rawRssi, isPresent) = when (zone) {
            ProximityZone.VERY_CLOSE -> Triple(refRssi - 3, refRssi - 2, true)
            ProximityZone.CLOSE -> Triple(refRssi - 12, refRssi - 11, true)
            ProximityZone.DRIFTING -> Triple(refRssi - 22, refRssi - 24, true)
            ProximityZone.FAR -> Triple(refRssi - 33, refRssi - 35, true)
            ProximityZone.CRITICAL -> Triple(refRssi - 43, refRssi - 45, true)
            ProximityZone.LOST -> Triple(-100, -100, false)
        }

        return ProximityTelemetry(
            zone = zone,
            filteredRssi = filteredRssi,
            rawRssi = rawRssi,
            referenceRssi = refRssi,
            deltaRssi = refRssi - filteredRssi,
            lastSeenTimestamp = if (isPresent) System.currentTimeMillis() else 0L,
            isCalibrated = calibrated,
            isPeerPresent = isPresent,
            isSimulated = true,
            statusMessage = "SIMULATING ${zone.name}"
        )
    }
}
