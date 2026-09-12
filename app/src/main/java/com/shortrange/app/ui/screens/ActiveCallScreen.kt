package com.shortrange.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.model.CallQualityState
import com.shortrange.app.model.FaultType
import com.shortrange.app.model.MockTelemetryProvider
import com.shortrange.app.ui.components.IndustrialDangerButton
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialOutlineButton
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
    var qualityState by remember { mutableStateOf(CallQualityState.GOOD) }
    var isMuted by remember { mutableStateOf(false) }
    var callSeconds by remember { mutableIntStateOf(137) } // 00:02:17

    // Timer simulation
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            callSeconds++
        }
    }

    val telemetry = when (qualityState) {
        CallQualityState.GOOD -> MockTelemetryProvider.Good
        CallQualityState.DEGRADING -> MockTelemetryProvider.Degrading
        CallQualityState.CRITICAL -> MockTelemetryProvider.Critical
    }

    val integrityColor = when (qualityState) {
        CallQualityState.GOOD -> TelemetryGreen
        CallQualityState.DEGRADING -> TelemetryAmber
        CallQualityState.CRITICAL -> SignalRed
    }

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
                val isCritical = qualityState == CallQualityState.CRITICAL
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
                        text = "${telemetry.integrity}%",
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
                                .fillMaxWidth(telemetry.integrity / 100f)
                                .height(6.dp)
                                .background(integrityColor)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Proximity diagram
                    ProximityDiagram(
                        separationRatio = telemetry.separationRatio,
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
                        value = telemetry.proximityText,
                        valueColor = integrityColor,
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCell(
                        label = "AUDIO",
                        value = telemetry.audioText,
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
                        label = "LATENCY",
                        value = "+${telemetry.latencyMs} MS",
                        modifier = Modifier.weight(1f)
                    )
                    TelemetryCell(
                        label = "FRAME LOSS",
                        value = "${telemetry.frameLossPct}%",
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
                        onClick = { isMuted = !isMuted },
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
                        onClick = onEndCallClick,
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

                // Interactive Proximity Simulator Bar (for Phase 1 UI validation)
                IndustrialPanel {
                    Text(
                        text = "TEST CONTROLS — SIMULATE DISTANCE STATE",
                        style = IndustrialLabelMono.copy(fontSize = 9.sp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        TestStateButton(
                            title = "GOOD",
                            isSelected = qualityState == CallQualityState.GOOD,
                            modifier = Modifier.weight(1f)
                        ) { qualityState = CallQualityState.GOOD }

                        TestStateButton(
                            title = "DRIFT",
                            isSelected = qualityState == CallQualityState.DEGRADING,
                            modifier = Modifier.weight(1f)
                        ) { qualityState = CallQualityState.DEGRADING }

                        TestStateButton(
                            title = "CRIT",
                            isSelected = qualityState == CallQualityState.CRITICAL,
                            modifier = Modifier.weight(1f)
                        ) { qualityState = CallQualityState.CRITICAL }

                        TestStateButton(
                            title = "FAULT 04",
                            isSelected = false,
                            modifier = Modifier.weight(1.3f)
                        ) { onFaultOccurred(FaultType.PROXIMITY_FAULT_04) }

                        TestStateButton(
                            title = "FAULT 02",
                            isSelected = false,
                            modifier = Modifier.weight(1.3f)
                        ) { onFaultOccurred(FaultType.NETWORK_FAULT_02) }
                    }
                }
            }
        }
    }
}

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
