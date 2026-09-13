package com.shortrange.app.proximity

import com.shortrange.app.proximity.model.ProximityTelemetry
import com.shortrange.app.proximity.model.ProximityZone
import kotlinx.coroutines.flow.StateFlow

enum class BleRole {
    ADVERTISER,
    SCANNER,
    TRANSCEIVER
}

interface ProximityProvider {
    val telemetry: StateFlow<ProximityTelemetry>
    fun start(role: BleRole, sessionCode: String? = null)
    fun stop()
    fun calibrateReference(customRefRssi: Int? = null)
    fun setSimulationZone(zone: ProximityZone)
    fun isSimulated(): Boolean
}
