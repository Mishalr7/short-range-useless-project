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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.model.FaultType
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialPanel
import com.shortrange.app.ui.components.IndustrialPrimaryButton
import com.shortrange.app.ui.components.ProximityDiagram
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.SignalRed
import com.shortrange.app.ui.theme.TechnicalWhite

@Composable
fun CommunicationLostScreen(
    faultType: FaultType,
    onReturnHomeClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
    ) {
        IndustrialHeader(
            title = faultType.code,
            onBackClick = onReturnHomeClick,
            statusText = "SR/01"
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                // Prominent Signal Red Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SignalRed, RoundedCornerShape(2.dp))
                        .padding(vertical = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "COMMUNICATION LOST",
                        style = IndustrialDataMono.copy(
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TechnicalWhite,
                            letterSpacing = 1.2.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                if (faultType.isProximityIssue) {
                    // Severed Proximity Diagram
                    ProximityDiagram(
                        separationRatio = 0.85f,
                        isSevered = true
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Text(
                        text = "PROXIMITY LIMIT EXCEEDED.\nCOMMUNICATION CHANNEL TERMINATED.",
                        style = IndustrialLabelMono.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryBlack
                        )
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Recommendation panel
                    IndustrialPanel(
                        borderColor = SignalRed.copy(alpha = 0.5f),
                        backgroundColor = SignalRed.copy(alpha = 0.04f)
                    ) {
                        Text(
                            text = "RECOMMENDATION",
                            style = IndustrialLabelMono.copy(fontSize = 10.sp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = faultType.recommendation,
                            style = IndustrialDataMono.copy(
                                fontSize = 13.sp,
                                color = SignalRed
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "CAUSE",
                        style = IndustrialLabelMono.copy(fontSize = 10.sp)
                    )
                    Text(
                        text = "PROXIMITY VIOLATION (${faultType.code})",
                        style = IndustrialDataMono.copy(fontSize = 12.sp)
                    )
                } else {
                    // Network failure graphic
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = "No Network Connection",
                            tint = SignalRed,
                            modifier = Modifier.size(64.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "VOICE CHANNEL FAILURE\nCHECK NETWORK CONNECTION (${faultType.code})",
                        style = IndustrialDataMono.copy(
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = PrimaryBlack
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    IndustrialPanel(
                        borderColor = SignalRed.copy(alpha = 0.3f),
                        backgroundColor = SignalRed.copy(alpha = 0.04f)
                    ) {
                        Text(
                            text = faultType.recommendation,
                            style = IndustrialLabelMono.copy(
                                fontSize = 11.sp,
                                color = SignalRed,
                                lineHeight = 15.sp
                            )
                        )
                    }
                }
            }

            IndustrialPrimaryButton(
                text = "RETURN HOME",
                onClick = onReturnHomeClick
            )
        }
    }
}
