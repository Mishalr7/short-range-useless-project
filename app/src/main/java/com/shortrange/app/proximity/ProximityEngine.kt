package com.shortrange.app.proximity

import android.content.Context
import com.shortrange.app.proximity.mock.MockProximityProvider
import com.shortrange.app.proximity.model.ProximityTelemetry
import com.shortrange.app.proximity.model.ProximityZone
import com.shortrange.app.proximity.real.RealBleProximityProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Clean architectural facade decoupling the UI and future communications layers from BLE.
 * Handles switching between Real BLE (default) and Mock fallback.
 */
class ProximityEngine private constructor(
    context: Context
) {
    companion object {
        @Volatile
        private var instance: ProximityEngine? = null

        fun initialize(context: Context): ProximityEngine {
            return instance ?: synchronized(this) {
                instance ?: ProximityEngine(context.applicationContext).also { instance = it }
            }
        }

        fun getInstance(): ProximityEngine {
            return instance ?: error("ProximityEngine must be initialized with Application context first")
        }
    }

    private val realProvider = RealBleProximityProvider(context)
    private val mockProvider = MockProximityProvider()

    private var activeProvider: ProximityProvider = realProvider
    private var isSimulating: Boolean = false

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private var relayJob: Job? = null

    private val _telemetry = MutableStateFlow(activeProvider.telemetry.value)
    val telemetry: StateFlow<ProximityTelemetry> = _telemetry.asStateFlow()

    private var currentSessionCode: String? = null

    init {
        startRelay()
    }

    private fun startRelay() {
        relayJob?.cancel()
        relayJob = scope.launch {
            activeProvider.telemetry.collect {
                _telemetry.value = it
            }
        }
    }

    fun start(role: BleRole, sessionCode: String? = null) {
        if (!sessionCode.isNullOrBlank()) {
            currentSessionCode = sessionCode
        }
        activeProvider.start(role, currentSessionCode)
    }

    fun stop() {
        activeProvider.stop()
    }

    fun resetSession() {
        currentSessionCode = null
        activeProvider.stop()
    }

    fun calibrateReference(customRefRssi: Int? = null) {
        activeProvider.calibrateReference(customRefRssi)
    }

    fun setSimulationMode(enable: Boolean) {
        if (isSimulating == enable) return

        val currentRole = BleRole.TRANSCEIVER
        activeProvider.stop()

        isSimulating = enable
        activeProvider = if (enable) mockProvider else realProvider

        startRelay()
        activeProvider.start(currentRole)
    }

    fun isSimulating(): Boolean = isSimulating

    fun setSimulationZone(zone: ProximityZone) {
        if (!isSimulating) {
            setSimulationMode(true)
        }
        mockProvider.setSimulationZone(zone)
    }

    fun checkBluetoothPrerequisites(): BluetoothCheckResult {
        return BluetoothCheckResult(
            hasBleHardware = realProvider.hasBleHardware(),
            isBluetoothEnabled = realProvider.isBluetoothEnabled(),
            hasPermissions = realProvider.hasRequiredPermissions()
        )
    }
}

data class BluetoothCheckResult(
    val hasBleHardware: Boolean,
    val isBluetoothEnabled: Boolean,
    val hasPermissions: Boolean
)
