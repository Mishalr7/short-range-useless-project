package com.shortrange.app.proximity.real

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import com.shortrange.app.proximity.BleRole
import com.shortrange.app.proximity.ProximityProvider
import com.shortrange.app.proximity.filter.RssiFilter
import com.shortrange.app.proximity.model.ProximityTelemetry
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
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlin.random.Random

/**
 * Native Android BLE Proximity implementation.
 *
 * Dedicated SHORT RANGE 16-bit Service UUID:
 * 00005201-0000-1000-8000-00805F9B34FB (16-bit alias: 0x5201, "SR01")
 * Guarantees compatibility with legacy BLE advertising 31-byte limit.
 */
class RealBleProximityProvider(
    private val context: Context,
    private val filter: RssiFilter = RssiFilter()
) : ProximityProvider {

    companion object {
        private const val TAG = "ShortRangeBle"
        val SHORT_RANGE_SERVICE_UUID: UUID =
            UUID.fromString("00005201-0000-1000-8000-00805F9B34FB")
        val PARCEL_SERVICE_UUID = ParcelUuid(SHORT_RANGE_SERVICE_UUID)
    }

    private val bluetoothManager: BluetoothManager? =
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var advertiser: BluetoothLeAdvertiser? = null
    private var scanner: BluetoothLeScanner? = null

    private var advertiseCallback: AdvertiseCallback? = null
    private var scanCallback: ScanCallback? = null

    private var scope = CoroutineScope(Dispatchers.Default + Job())
    private var timeoutJob: Job? = null

    private var targetSessionCode: String? = null
    private var currentRole: BleRole? = null
    private var isCalibrated: Boolean = false

    // Unique 1-byte token to detect and drop own loopback packets on devices that deliver them
    private var localDeviceToken: Byte = Random.nextInt(1, 255).toByte()

    private val _telemetry = MutableStateFlow(
        ProximityTelemetry(
            zone = ProximityZone.LOST,
            filteredRssi = -100,
            rawRssi = -100,
            referenceRssi = filter.referenceRssi,
            deltaRssi = 53,
            lastSeenTimestamp = 0L,
            isCalibrated = false,
            isPeerPresent = false,
            isSimulated = false,
            statusMessage = "BLE READY"
        )
    )
    override val telemetry: StateFlow<ProximityTelemetry> = _telemetry.asStateFlow()

    override fun start(role: BleRole, sessionCode: String?) {
        val effectiveCode = sessionCode ?: targetSessionCode

        // If already actively running in the desired role and session, avoid resetting hardware radio
        if (currentRole == role && targetSessionCode == effectiveCode && (advertiser != null || scanner != null)) {
            Log.d(TAG, "BLE already active as $role for session '$effectiveCode'; maintaining active stream.")
            return
        }

        targetSessionCode = effectiveCode
        currentRole = role
        localDeviceToken = Random.nextInt(1, 255).toByte()

        if (!hasBleHardware()) {
            Log.e(TAG, "BLE hardware unavailable on this device")
            _telemetry.value = _telemetry.value.copy(
                statusMessage = "BLE HARDWARE UNAVAILABLE"
            )
            return
        }

        if (!isBluetoothEnabled()) {
            Log.e(TAG, "Bluetooth is disabled")
            _telemetry.value = _telemetry.value.copy(
                statusMessage = "BLUETOOTH DISABLED"
            )
            return
        }

        if (!hasRequiredPermissions()) {
            Log.e(TAG, "Bluetooth permissions not granted")
            _telemetry.value = _telemetry.value.copy(
                statusMessage = "BLUETOOTH PERMISSIONS REQUIRED"
            )
            return
        }

        // Clean up previous radios before re-starting if role/session changed
        stopAdvertising()
        stopScanning()
        timeoutJob?.cancel()
        timeoutJob = null

        scope = CoroutineScope(Dispatchers.Default + Job())
        startTimeoutMonitor()

        Log.i(TAG, "Starting BLE provider: role=$role, session='$effectiveCode', localToken=$localDeviceToken")

        when (role) {
            BleRole.ADVERTISER -> startAdvertising(effectiveCode)
            BleRole.SCANNER -> startScanning(effectiveCode)
            BleRole.TRANSCEIVER -> {
                startAdvertising(effectiveCode)
                startScanning(effectiveCode)
            }
        }
    }

    override fun stop() {
        Log.i(TAG, "Stopping BLE provider and resetting state")
        timeoutJob?.cancel()
        timeoutJob = null

        stopAdvertising()
        stopScanning()

        filter.reset()
        isCalibrated = false
        currentRole = null

        _telemetry.value = _telemetry.value.copy(
            zone = ProximityZone.LOST,
            filteredRssi = -100,
            rawRssi = -100,
            referenceRssi = filter.referenceRssi,
            deltaRssi = 53,
            lastSeenTimestamp = 0L,
            isCalibrated = false,
            isPeerPresent = false,
            statusMessage = "BLE STOPPED"
        )
    }

    override fun calibrateReference(customRefRssi: Int?) {
        val currentTelemetry = _telemetry.value
        val newRef = customRefRssi ?: if (currentTelemetry.isPeerPresent && currentTelemetry.filteredRssi > -95) {
            currentTelemetry.filteredRssi
        } else {
            Log.w(TAG, "Calibration rejected: no real peer signal present (isPeerPresent=${currentTelemetry.isPeerPresent}, filteredRssi=${currentTelemetry.filteredRssi})")
            return
        }

        filter.calibrate(newRef)
        isCalibrated = true

        val filterResult = filter.addSample(currentTelemetry.rawRssi)

        _telemetry.value = currentTelemetry.copy(
            zone = filterResult.zone,
            referenceRssi = newRef,
            deltaRssi = filterResult.deltaRssi,
            isCalibrated = true,
            statusMessage = "REFERENCE CALIBRATED ($newRef dBm)"
        )
        Log.i(TAG, "Reference calibrated to $newRef dBm")
    }

    override fun setSimulationZone(zone: ProximityZone) {
        // Real provider handles real signals; delegates simulated override if explicitly called
        _telemetry.value = _telemetry.value.copy(
            zone = zone,
            isSimulated = true
        )
    }

    override fun isSimulated(): Boolean = false

    // ==========================================
    // Advertising
    // ==========================================

    private fun startAdvertising(sessionCode: String?) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e(TAG, "BLUETOOTH_ADVERTISE permission denied")
            _telemetry.value = _telemetry.value.copy(statusMessage = "ADVERTISE PERMISSION DENIED")
            return
        }

        advertiser = bluetoothAdapter?.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BluetoothLeAdvertiser is unavailable")
            _telemetry.value = _telemetry.value.copy(statusMessage = "BLE ADVERTISER UNAVAILABLE")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .setTimeout(0)
            .build()

        val dataBuilder = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(PARCEL_SERVICE_UUID)

        // Session code payload: 7-character session string + 1-byte device token = 8 bytes
        val sessionStr = (sessionCode ?: "SR-0000").trim()
        val sessionBytes = sessionStr.toByteArray(StandardCharsets.UTF_8)
        val payload = ByteArray(sessionBytes.size + 1)
        System.arraycopy(sessionBytes, 0, payload, 0, sessionBytes.size)
        payload[payload.size - 1] = localDeviceToken

        dataBuilder.addServiceData(PARCEL_SERVICE_UUID, payload)

        // Payload mathematical breakdown:
        // - Flags: 3 bytes (0x02, 0x01, 0x06)
        // - 16-bit Service UUID list: 4 bytes (len 0x03, type 0x03, UUID 0x01, 0x52)
        // - 16-bit Service Data: 12 bytes (len 0x0B, type 0x16, UUID 0x01, 0x52, payload 8 bytes)
        // Total = 19 bytes <= 31 bytes legacy BLE advertisement limit.

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.i(TAG, "BLE advertising started successfully for session '$sessionStr' (19 bytes payload)")
                _telemetry.value = _telemetry.value.copy(
                    statusMessage = "ADVERTISING ($sessionStr)"
                )
            }

            override fun onStartFailure(errorCode: Int) {
                val errorDescription = when (errorCode) {
                    ADVERTISE_FAILED_DATA_TOO_LARGE -> "DATA_TOO_LARGE"
                    ADVERTISE_FAILED_TOO_MANY_ADVERTISERS -> "TOO_MANY_ADVERTISERS"
                    ADVERTISE_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                    ADVERTISE_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    ADVERTISE_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                    else -> "ERROR_CODE_$errorCode"
                }
                Log.e(TAG, "BLE advertising failed: $errorDescription (code $errorCode)")
                _telemetry.value = _telemetry.value.copy(
                    statusMessage = "ADVERTISE FAILED: $errorDescription"
                )
            }
        }

        try {
            advertiser?.startAdvertising(settings, dataBuilder.build(), advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on startAdvertising", e)
            _telemetry.value = _telemetry.value.copy(statusMessage = "SECURITY EXCEPTION ON ADVERTISE")
        } catch (e: Exception) {
            Log.e(TAG, "Exception on startAdvertising", e)
            _telemetry.value = _telemetry.value.copy(statusMessage = "ADVERTISE ERROR: ${e.message}")
        }
    }

    private fun stopAdvertising() {
        try {
            if (advertiseCallback != null && advertiser != null) {
                advertiser?.stopAdvertising(advertiseCallback)
                Log.i(TAG, "BLE advertising stopped")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on stopAdvertising: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Exception on stopAdvertising: ${e.message}")
        } finally {
            advertiseCallback = null
            advertiser = null
        }
    }

    // ==========================================
    // Scanning
    // ==========================================

    private fun startScanning(sessionCode: String?) {
        if (!hasPermission(Manifest.permission.BLUETOOTH_SCAN) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Log.e(TAG, "BLUETOOTH_SCAN permission denied")
            _telemetry.value = _telemetry.value.copy(statusMessage = "SCAN PERMISSION DENIED")
            return
        }

        scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is unavailable")
            _telemetry.value = _telemetry.value.copy(statusMessage = "BLE SCANNER UNAVAILABLE")
            return
        }

        // Exact filter targeting SHORT RANGE 16-bit Service UUID
        val scanFilter = ScanFilter.Builder()
            .setServiceUuid(PARCEL_SERVICE_UUID)
            .build()

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0L)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.let { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                val errorDescription = when (errorCode) {
                    SCAN_FAILED_ALREADY_STARTED -> "ALREADY_STARTED"
                    SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "REGISTRATION_FAILED"
                    SCAN_FAILED_INTERNAL_ERROR -> "INTERNAL_ERROR"
                    SCAN_FAILED_FEATURE_UNSUPPORTED -> "FEATURE_UNSUPPORTED"
                    else -> "ERROR_CODE_$errorCode"
                }
                Log.e(TAG, "BLE scan failed: $errorDescription (code $errorCode)")
                _telemetry.value = _telemetry.value.copy(
                    statusMessage = "SCAN FAILED: $errorDescription"
                )
            }
        }

        try {
            scanner?.startScan(listOf(scanFilter), settings, scanCallback)
            Log.i(TAG, "BLE scanning started successfully for SR/01 (filter UUID=$PARCEL_SERVICE_UUID)")
            _telemetry.value = _telemetry.value.copy(
                statusMessage = "SCANNING FOR SR/01 PEER"
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException on startScan", e)
            _telemetry.value = _telemetry.value.copy(statusMessage = "SECURITY EXCEPTION ON SCAN")
        } catch (e: Exception) {
            Log.e(TAG, "Exception on startScan", e)
            _telemetry.value = _telemetry.value.copy(statusMessage = "SCAN ERROR: ${e.message}")
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val record = result.scanRecord ?: return

        // Verify service UUID
        val serviceUuids = record.serviceUuids
        val isShortRange = serviceUuids?.any { it == PARCEL_SERVICE_UUID } == true
        if (!isShortRange) return

        val serviceData = record.getServiceData(PARCEL_SERVICE_UUID) ?: return

        // Extract session code and sender token
        val targetCode = targetSessionCode?.trim()
        if (serviceData.isNotEmpty()) {
            val hasToken = serviceData.size > 1
            val senderToken = if (hasToken) serviceData[serviceData.size - 1] else null
            val codeBytes = if (hasToken) serviceData.copyOfRange(0, serviceData.size - 1) else serviceData
            val packetCode = String(codeBytes, StandardCharsets.UTF_8).trim()

            // Discard own loopback packet if hardware delivers it
            if (senderToken != null && senderToken == localDeviceToken) {
                return
            }

            // Session code filtering
            if (!targetCode.isNullOrBlank() && !packetCode.equals(targetCode, ignoreCase = true)) {
                return
            }
        } else if (!targetCode.isNullOrBlank()) {
            return
        }

        val rawRssi = result.rssi
        val filterResult = filter.addSample(rawRssi)

        Log.d(TAG, "Peer advertisement received: raw=$rawRssi, filtered=${filterResult.filteredRssi}, ref=${filterResult.referenceRssi}, zone=${filterResult.zone}")

        _telemetry.value = ProximityTelemetry(
            zone = filterResult.zone,
            filteredRssi = filterResult.filteredRssi,
            rawRssi = filterResult.rawRssi,
            referenceRssi = filterResult.referenceRssi,
            deltaRssi = filterResult.deltaRssi,
            lastSeenTimestamp = filterResult.timestamp,
            isCalibrated = isCalibrated,
            isPeerPresent = true,
            isSimulated = false,
            statusMessage = "SIGNAL ACTIVE (${filterResult.zone.label})"
        )
    }

    private fun stopScanning() {
        try {
            if (scanCallback != null && scanner != null) {
                scanner?.stopScan(scanCallback)
                Log.i(TAG, "BLE scanning stopped")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException on stopScanning: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "Exception on stopScanning: ${e.message}")
        } finally {
            scanCallback = null
            scanner = null
        }
    }

    // ==========================================
    // Timeout Monitor
    // ==========================================

    private fun startTimeoutMonitor() {
        timeoutJob?.cancel()
        timeoutJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val timeoutResult = filter.checkTimeout()
                if (timeoutResult != null) {
                    Log.w(TAG, "Peer silence timeout exceeded; zone transitioned to LOST")
                    _telemetry.value = _telemetry.value.copy(
                        zone = ProximityZone.LOST,
                        isPeerPresent = false,
                        statusMessage = "PEER LOST (TIMEOUT)"
                    )
                }
            }
        }
    }

    // ==========================================
    // System & Permission Checks
    // ==========================================

    fun hasBleHardware(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun hasRequiredPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            hasPermission(Manifest.permission.BLUETOOTH_SCAN) &&
                    hasPermission(Manifest.permission.BLUETOOTH_ADVERTISE) &&
                    hasPermission(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            hasPermission(Manifest.permission.BLUETOOTH) &&
                    hasPermission(Manifest.permission.BLUETOOTH_ADMIN)
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
}
