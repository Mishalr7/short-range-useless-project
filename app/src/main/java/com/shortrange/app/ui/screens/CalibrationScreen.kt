package com.shortrange.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialPanel
import com.shortrange.app.ui.components.IndustrialPrimaryButton
import com.shortrange.app.ui.components.SegmentedMeter
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.TechnicalWhite
import com.shortrange.app.ui.theme.TelemetryGreen

@Composable
fun CalibrationScreen(
    onBackClick: () -> Unit,
    onEstablishVoiceChannel: () -> Unit
) {
    var isCalibrated by remember { mutableStateOf(false) }
    var activeSegments by remember { mutableIntStateOf(11) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
    ) {
        IndustrialHeader(
            title = "PROXIMITY CALIBRATION",
            onBackClick = onBackClick,
            statusText = "SR/01"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "PLACE BOTH DEVICES TOGETHER\nTO ESTABLISH REFERENCE.",
                    style = IndustrialLabelMono.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack,
                        lineHeight = 18.sp
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Upper Signal Strength Panel
                IndustrialPanel {
                    if (isCalibrated) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "REFERENCE ESTABLISHED",
                                style = IndustrialLabelMono.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = PrimaryBlack
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .background(TelemetryGreen, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = TechnicalWhite,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "REFERENCE SIGNAL",
                            style = IndustrialLabelMono
                        )
                        Text(
                            text = "-47 dBm (STABLE)",
                            style = IndustrialDataMono.copy(fontSize = 13.sp),
                            color = PrimaryBlack
                        )
                    } else {
                        Text(
                            text = "SIGNAL STRENGTH",
                            style = IndustrialLabelMono
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    SegmentedMeter(
                        activeSegments = if (isCalibrated) 20 else activeSegments,
                        totalSegments = 20,
                        activeColor = PrimaryBlack
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    if (!isCalibrated) {
                        Text(
                            text = "STABILIZING...",
                            style = IndustrialLabelMono
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Lower Reference Status Panel
                IndustrialPanel {
                    if (isCalibrated) {
                        Text(
                            text = "CALIBRATION COMPLETE.",
                            style = IndustrialDataMono.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            ),
                            color = PrimaryBlack
                        )
                    } else {
                        Text(
                            text = "REFERENCE STATUS",
                            style = IndustrialLabelMono
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "MEASURING...",
                            style = IndustrialLabelMono.copy(color = PrimaryBlack)
                        )
                    }
                }
            }

            // Bottom Actions & Guidance
            Column {
                if (isCalibrated) {
                    IndustrialPrimaryButton(
                        text = "ESTABLISH VOICE CHANNEL",
                        onClick = onEstablishVoiceChannel
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "DEVICES ARE IN REFERENCE PROXIMITY.",
                        style = IndustrialLabelMono.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                } else {
                    IndustrialPrimaryButton(
                        text = "CALIBRATE REFERENCE",
                        onClick = {
                            isCalibrated = true
                            activeSegments = 20
                        }
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "KEEP DEVICES CLOSE AND STILL UNTIL STABLE.",
                        style = IndustrialLabelMono.copy(fontSize = 11.sp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }
        }
    }
}
