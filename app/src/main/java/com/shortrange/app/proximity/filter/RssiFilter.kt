package com.shortrange.app.proximity.filter

import com.shortrange.app.proximity.model.ProximityZone

/**
 * Deterministic RSSI filtering and hysteresis-based proximity classification.
 *
 * Algorithm:
 * 1. Rolling window median filter (window size = 5) to reject transient multipath spikes/drops.
 * 2. Exponential Moving Average (EMA, alpha = 0.35) for smooth progression.
 * 3. Relative delta calculation: delta = referenceRssi - filteredRssi.
 * 4. Dual-threshold hysteresis band (2.5 dB) to prevent boundary oscillation.
 * 5. Freshness tracking for automatic transition to LOST upon signal disappearance.
 */
class RssiFilter(
    private val windowSize: Int = 5,
    private val alpha: Float = 0.35f,
    private val hysteresisDb: Float = 2.5f,
    private val peerTimeoutMs: Long = 4000L
) {
    private val window = ArrayDeque<Int>(windowSize)
    private var smoothedRssi: Float? = null
    private var currentZone: ProximityZone = ProximityZone.LOST
    private var lastUpdateTimestamp: Long = 0L

    var referenceRssi: Int = DEFAULT_REFERENCE_RSSI
        private set

    companion object {
        const val DEFAULT_REFERENCE_RSSI = -47

        // Nominal threshold deltas (dB drop from calibrated reference)
        private const val THRESHOLD_VERY_CLOSE = 8.0f
        private const val THRESHOLD_CLOSE = 18.0f
        private const val THRESHOLD_DRIFTING = 28.0f
        private const val THRESHOLD_FAR = 38.0f
        private const val THRESHOLD_CRITICAL = 48.0f
    }

    @Synchronized
    fun addSample(rawRssi: Int, timestamp: Long = System.currentTimeMillis()): FilterResult {
        lastUpdateTimestamp = timestamp

        // 1. Maintain sliding window for outlier rejection
        if (window.size >= windowSize) {
            window.removeFirst()
        }
        window.addLast(rawRssi)

        // 2. Median sample
        val median = calculateMedian(window)

        // 3. Exponential Moving Average
        val currentSmoothed = smoothedRssi
        val nextSmoothed = if (currentSmoothed == null) {
            median.toFloat()
        } else {
            (alpha * median) + ((1f - alpha) * currentSmoothed)
        }
        smoothedRssi = nextSmoothed

        // 4. Compute delta relative to calibrated reference
        val filteredInt = nextSmoothed.toInt()
        val delta = (referenceRssi - filteredInt).coerceAtLeast(0).toFloat()

        // 5. Update zone with hysteresis
        currentZone = classifyWithHysteresis(delta, currentZone)

        return FilterResult(
            zone = currentZone,
            filteredRssi = filteredInt,
            rawRssi = rawRssi,
            referenceRssi = referenceRssi,
            deltaRssi = referenceRssi - filteredInt,
            timestamp = timestamp,
            isPeerPresent = true
        )
    }

    @Synchronized
    fun checkTimeout(now: Long = System.currentTimeMillis()): FilterResult? {
        if (lastUpdateTimestamp > 0L && (now - lastUpdateTimestamp) > peerTimeoutMs) {
            if (currentZone != ProximityZone.LOST) {
                currentZone = ProximityZone.LOST
                return FilterResult(
                    zone = ProximityZone.LOST,
                    filteredRssi = smoothedRssi?.toInt() ?: -100,
                    rawRssi = -100,
                    referenceRssi = referenceRssi,
                    deltaRssi = referenceRssi - (smoothedRssi?.toInt() ?: -100),
                    timestamp = now,
                    isPeerPresent = false
                )
            }
        }
        return null
    }

    @Synchronized
    fun calibrate(newReferenceRssi: Int) {
        referenceRssi = newReferenceRssi
    }

    @Synchronized
    fun reset() {
        window.clear()
        smoothedRssi = null
        currentZone = ProximityZone.LOST
        lastUpdateTimestamp = 0L
    }

    private fun calculateMedian(samples: List<Int>): Int {
        val sorted = samples.sorted()
        return if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            (sorted[sorted.size / 2 - 1] + sorted[sorted.size / 2]) / 2
        }
    }

    private fun classifyWithHysteresis(delta: Float, current: ProximityZone): ProximityZone {
        // Evaluate based on current state and directional thresholds
        return when (current) {
            ProximityZone.VERY_CLOSE -> {
                if (delta > THRESHOLD_VERY_CLOSE + hysteresisDb) ProximityZone.CLOSE
                else ProximityZone.VERY_CLOSE
            }
            ProximityZone.CLOSE -> {
                when {
                    delta < THRESHOLD_VERY_CLOSE - hysteresisDb -> ProximityZone.VERY_CLOSE
                    delta > THRESHOLD_CLOSE + hysteresisDb -> ProximityZone.DRIFTING
                    else -> ProximityZone.CLOSE
                }
            }
            ProximityZone.DRIFTING -> {
                when {
                    delta < THRESHOLD_CLOSE - hysteresisDb -> ProximityZone.CLOSE
                    delta > THRESHOLD_DRIFTING + hysteresisDb -> ProximityZone.FAR
                    else -> ProximityZone.DRIFTING
                }
            }
            ProximityZone.FAR -> {
                when {
                    delta < THRESHOLD_DRIFTING - hysteresisDb -> ProximityZone.DRIFTING
                    delta > THRESHOLD_FAR + hysteresisDb -> ProximityZone.CRITICAL
                    else -> ProximityZone.FAR
                }
            }
            ProximityZone.CRITICAL -> {
                when {
                    delta < THRESHOLD_FAR - hysteresisDb -> ProximityZone.FAR
                    delta > THRESHOLD_CRITICAL + hysteresisDb -> ProximityZone.LOST
                    else -> ProximityZone.CRITICAL
                }
            }
            ProximityZone.LOST -> {
                when {
                    delta <= THRESHOLD_VERY_CLOSE -> ProximityZone.VERY_CLOSE
                    delta <= THRESHOLD_CLOSE -> ProximityZone.CLOSE
                    delta <= THRESHOLD_DRIFTING -> ProximityZone.DRIFTING
                    delta <= THRESHOLD_FAR -> ProximityZone.FAR
                    delta <= THRESHOLD_CRITICAL -> ProximityZone.CRITICAL
                    else -> ProximityZone.LOST
                }
            }
        }
    }
}

data class FilterResult(
    val zone: ProximityZone,
    val filteredRssi: Int,
    val rawRssi: Int,
    val referenceRssi: Int,
    val deltaRssi: Int,
    val timestamp: Long,
    val isPeerPresent: Boolean
)
