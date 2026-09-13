package com.shortrange.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.model.FaultType
import com.shortrange.app.proximity.ProximityEngine
import com.shortrange.app.proximity.model.ProximityZone
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialPanel
import com.shortrange.app.ui.components.ProximityDiagram
import com.shortrange.app.ui.components.TelemetryCell
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.IndustrialTelemetryValue
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.SignalRed
import com.shortrange.app.ui.theme.TechnicalWhite
import com.shortrange.app.ui.theme.TelemetryAmber
import com.shortrange.app.ui.theme.TelemetryGreen
import kotlinx.coroutines.delay

@Composable
fun ActiveCallScreen(
    onEndCallClick: () -> Unit,
    onFaultOccurred: (FaultType) -> Unit
) {
    val proximityEngine = ProximityEngine.getInstance()
    val proximityTelemetry by proximityEngine.telemetry.collectAsState()

    val webrtcCallManager = com.shortrange.app.webrtc.WebRtcCallManager.getInstance()
    val webrtcCallState by webrtcCallManager.callState.collectAsState()

    var isMuted by remember { mutableStateOf(false) }
    var callSeconds by remember { mutableIntStateOf(0) }

    // Call duration timer
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    var hasEstablishedConnection by remember { mutableStateOf(false) }

    // Synchronize proximity zone to WebRTC audio degradation
    LaunchedEffect(proximityTelemetry.zone) {
        webrtcCallManager.updateProximityZone(proximityTelemetry.zone)
    }

    // Monitor WebRTC connection failure
    LaunchedEffect(webrtcCallState) {
        if (webrtcCallState == com.shortrange.app.webrtc.CallState.FAILED) {
            onFaultOccurred(FaultType.NETWORK_FAULT_02)
        }
    }

    // Monitor Proximity Lost transition once call has been active
    LaunchedEffect(webrtcCallState, proximityTelemetry.zone, proximityTelemetry.isPeerPresent) {
        if (webrtcCallState == com.shortrange.app.webrtc.CallState.CONNECTED && (proximityTelemetry.isPeerPresent || proximityEngine.isSimulating())) {
            hasEstablishedConnection = true
        }
        if (hasEstablishedConnection && proximityTelemetry.zone == ProximityZone.LOST) {
            onFaultOccurred(FaultType.PROXIMITY_FAULT_04)
        }
    }

    val isAcquiring = webrtcCallState == com.shortrange.app.webrtc.CallState.CONNECTING || !proximityTelemetry.isPeerPresent

    // Map ProximityZone to visual UI telemetry per Phase 4 CommunicationPolicy
    val (integrityPct, proximityLabel, audioLabel, separationRatio, integrityColor) = when {
        isAcquiring -> Quintuple(100, "ACQUIRING LINK...", "INITIALIZING", 0.15f, TelemetryGreen)
        proximityTelemetry.zone == ProximityZone.VERY_CLOSE -> Quintuple(100, "VERY CLOSE", "GOOD", 0.15f, TelemetryGreen)
        proximityTelemetry.zone == ProximityZone.CLOSE -> Quintuple(80, "CLOSE", "SLIGHT DEGRADATION", 0.35f, TelemetryGreen)
        proximityTelemetry.zone == ProximityZone.DRIFTING -> Quintuple(60, "DRIFTING", "DEGRADED", 0.55f, TelemetryAmber)
        proximityTelemetry.zone == ProximityZone.FAR -> Quintuple(30, "FAR", "HEAVY DEGRADATION", 0.72f, TelemetryAmber)
        proximityTelemetry.zone == ProximityZone.CRITICAL -> Quintuple(10, "CRITICAL", "SEVERELY DEGRADED", 0.90f, SignalRed)
        else -> Quintuple(0, "LOST", "COMMUNICATION LOST", 0.95f, SignalRed)
    }

    val isCritical = proximityTelemetry.zone == ProximityZone.CRITICAL

    val formattedTime = String.format(
        "%02d:%02d:%02d",
        callSeconds / 3600,
        (callSeconds % 3600) / 60,
        callSeconds % 60
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
    ) {
        IndustrialHeader(
            title = "VOICE CHANNEL",
            statusText = "SR/01"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Call status and timer header row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(TelemetryGreen, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ACTIVE",
                            style = IndustrialDataMono.copy(fontSize = 12.sp)
                        )
                    }

                    Text(
                        text = formattedTime,
                        style = IndustrialDataMono.copy(fontSize = 12.sp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Communication Integrity Card
                IndustrialPanel(
                    borderColor = if (isCritical) SignalRed else LightBorder,
                    borderWidth = if (isCritical) 1.5.dp else 1.dp,
                    backgroundColor = if (isCritical) SignalRed.copy(alpha = 0.04f) else TechnicalWhite
                ) {
                    Text(
                        text = "COMMUNICATION INTEGRITY",
                        style = IndustrialLabelMono.copy(
                            color = if (isCritical) SignalRed else PrimaryBlack
                        )
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "$integrityPct%",
                        style = IndustrialTelemetryValue.copy(
                            color = if (isCritical) SignalRed else PrimaryBlack
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Integrity bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(LightBorder)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((integrityPct / 100f).coerceIn(0f, 1f))
                                .height(6.dp)
                                .background(integrityColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Proximity diagram
                    ProximityDiagram(
                        separationRatio = separationRatio,
                        lineColor = integrityColor
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2x2 Telemetry Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryCell(
                        label = "PROXIMITY",
                        value = proximityLabel,
                        valueColor = integrityColor,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCell(
                        label = "AUDIO",
                        value = audioLabel,
                        valueColor = integrityColor,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TelemetryCell(
                        label = "SIGNAL (RSSI)",
                        value = "${proximityTelemetry.filteredRssi} DBM",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCell(
                        label = "SEPARATION",
                        value = "+${proximityTelemetry.deltaRssi.coerceAtLeast(0)} DB",
                        modifier = Modifier.weight(1f)
                    )
                }

                // Critical warning alert box if in critical state
                if (isCritical) {
                    Spacer(modifier = Modifier.height(12.dp))
                    IndustrialPanel(
                        borderColor = SignalRed,
                        backgroundColor = SignalRed.copy(alpha = 0.05f)
                    ) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = SignalRed,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PROXIMITY LIMIT APPROACHING.\nCOMMUNICATION WILL TERMINATE IF SEPARATION INCREASES.",
                                style = IndustrialLabelMono.copy(
                                    fontSize = 10.sp,
                                    lineHeight = 13.sp,
                                    color = SignalRed
                                )
                            )
                        }
                    }
                }
            }

            // Bottom Actions & State Simulator controls
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // Call Control Buttons (Mute & End Call)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val newMute = !isMuted
                            isMuted = newMute
                            webrtcCallManager.setMuted(newMute)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(2.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, PrimaryBlack)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = null,
                            tint = PrimaryBlack,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isMuted) "UNMUTE" else "MUTE",
                            style = IndustrialDataMono.copy(fontSize = 12.sp),
                            color = PrimaryBlack
                        )
                    }

                    Button(
                        onClick = {
                            webrtcCallManager.endCall()
                            onEndCallClick()
                        },
                        modifier = Modifier
                            .weight(1.4f)
                            .height(50.dp),
                        shape = RoundedCornerShape(2.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SignalRed)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CallEnd,
                            contentDescription = null,
                            tint = TechnicalWhite,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "END CALL",
                            style = IndustrialDataMono.copy(fontSize = 12.sp),
                            color = TechnicalWhite
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Interactive Proximity Simulator Bar (for Phase 2 testing & demonstration fallback)
                IndustrialPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "PROXIMITY ENGINE: ${proximityTelemetry.statusMessage}",
                            style = IndustrialLabelMono.copy(fontSize = 9.sp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TestStateButton(
                            title = "V.CLOSE",
                            isSelected = proximityTelemetry.zone == ProximityZone.VERY_CLOSE,
                            modifier = Modifier.weight(1f)
                        ) { proximityEngine.setSimulationZone(ProximityZone.VERY_CLOSE) }

                        TestStateButton(
                            title = "CLOSE",
                            isSelected = proximityTelemetry.zone == ProximityZone.CLOSE,
                            modifier = Modifier.weight(1f)
                        ) { proximityEngine.setSimulationZone(ProximityZone.CLOSE) }

                        TestStateButton(
                            title = "DRIFT",
                            isSelected = proximityTelemetry.zone == ProximityZone.DRIFTING,
                            modifier = Modifier.weight(1f)
                        ) { proximityEngine.setSimulationZone(ProximityZone.DRIFTING) }

                        TestStateButton(
                            title = "FAR",
                            isSelected = proximityTelemetry.zone == ProximityZone.FAR,
                            modifier = Modifier.weight(1f)
                        ) { proximityEngine.setSimulationZone(ProximityZone.FAR) }

                        TestStateButton(
                            title = "CRIT",
                            isSelected = proximityTelemetry.zone == ProximityZone.CRITICAL,
                            modifier = Modifier.weight(1f)
                        ) { proximityEngine.setSimulationZone(ProximityZone.CRITICAL) }

                        TestStateButton(
                            title = "LOST",
                            isSelected = proximityTelemetry.zone == ProximityZone.LOST,
                            modifier = Modifier.weight(1f)
                        ) { proximityEngine.setSimulationZone(ProximityZone.LOST) }
                    }
                }
            }
        }
    }
}

private data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

@Composable
private fun TestStateButton(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(30.dp)
            .background(if (isSelected) PrimaryBlack else LightBorder, RoundedCornerShape(2.dp))
            .border(1.dp, PrimaryBlack, RoundedCornerShape(2.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = IndustrialLabelMono.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (isSelected) TechnicalWhite else PrimaryBlack
            )
        )
    }
}
