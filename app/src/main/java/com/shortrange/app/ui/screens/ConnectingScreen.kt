package com.shortrange.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialPanel
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.InstrumentGrey
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.TechnicalWhite
import com.shortrange.app.ui.theme.TelemetryGreen
import kotlinx.coroutines.delay

@Composable
fun ConnectingScreen(
    onBackClick: () -> Unit,
    onConnected: () -> Unit
) {
    var checkStep by remember { mutableIntStateOf(1) }

    val checkItems = listOf(
        "SIGNALING",
        "PARTICIPANT CONNECTION",
        "PROXIMITY LINK (BLE)",
        "AUDIO INPUT",
        "AUDIO OUTPUT"
    )

    LaunchedEffect(Unit) {
        for (i in 1..checkItems.size) {
            delay(400)
            checkStep = i
        }
        delay(600)
        onConnected()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
            .clickable { onConnected() }
    ) {
        IndustrialHeader(
            title = "VOICE CHANNEL",
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
                    text = "ESTABLISHING COMMUNICATION LINK.",
                    style = IndustrialLabelMono.copy(
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrimaryBlack
                    )
                )

                Spacer(modifier = Modifier.height(28.dp))

                // Checklist Panel
                IndustrialPanel {
                    checkItems.forEachIndexed { index, item ->
                        val isChecked = index < checkStep
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item,
                                style = IndustrialDataMono.copy(
                                    fontSize = 13.sp,
                                    color = if (isChecked) PrimaryBlack else InstrumentGrey
                                )
                            )

                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .background(
                                        if (isChecked) TelemetryGreen else LightBorder,
                                        CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isChecked) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = TechnicalWhite,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Channel Status Box
                IndustrialPanel {
                    Text(
                        text = "CHANNEL STATUS",
                        style = IndustrialLabelMono
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (checkStep >= checkItems.size) "LINK ESTABLISHED" else "INITIALIZING...",
                        style = IndustrialLabelMono.copy(color = PrimaryBlack)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = PrimaryBlack,
                            trackColor = LightBorder,
                            strokeWidth = 3.dp
                        )
                    }
                }
            }

            Text(
                text = "PLEASE REMAIN WITHIN PROXIMITY LIMIT.",
                style = IndustrialLabelMono.copy(fontSize = 11.sp),
                modifier = Modifier.padding(bottom = 12.dp)
            )
        }
    }
}
