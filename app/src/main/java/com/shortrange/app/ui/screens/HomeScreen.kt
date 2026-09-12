package com.shortrange.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.R
import com.shortrange.app.ui.components.IndustrialHeader
import com.shortrange.app.ui.components.IndustrialOutlineButton
import com.shortrange.app.ui.components.IndustrialPanel
import com.shortrange.app.ui.components.IndustrialPrimaryButton
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.TechnicalWhite
import com.shortrange.app.ui.theme.TelemetryGreen

@Composable
fun HomeScreen(
    onCreateSessionClick: () -> Unit,
    onJoinSessionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
    ) {
        // Header
        IndustrialHeader(
            statusText = "ONLINE",
            statusDotColor = TelemetryGreen
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Upper Brand block
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_sr_logo),
                    contentDescription = "Short Range Logo",
                    modifier = Modifier.size(105.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "SHORT RANGE",
                    style = IndustrialDataMono.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp
                    ),
                    color = PrimaryBlack
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "PROXIMITY-DEPENDENT\nCOMMUNICATION SYSTEM",
                    style = IndustrialLabelMono.copy(
                        letterSpacing = 1.1.sp,
                        lineHeight = 15.sp
                    ),
                    color = PrimaryBlack
                )
            }

            // Middle Action Buttons
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                IndustrialPrimaryButton(
                    text = "CREATE SESSION",
                    onClick = onCreateSessionClick,
                    showArrow = true
                )

                IndustrialOutlineButton(
                    text = "JOIN SESSION",
                    onClick = onJoinSessionClick,
                    showArrow = true
                )
            }

            // Lower status panel & specs
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                IndustrialPanel {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "SYSTEM STATUS",
                                style = IndustrialLabelMono
                            )
                            Text(
                                text = "READY",
                                style = IndustrialDataMono,
                                color = TelemetryGreen
                            )
                        }
                        Text(
                            text = "A TELEPHONE\nTHAT WORKS\nWHEN YOU'RE NEAR.",
                            style = IndustrialLabelMono.copy(fontSize = 10.sp, lineHeight = 13.sp),
                            color = PrimaryBlack
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(thickness = 1.dp, color = LightBorder)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "REV 01.0",
                        style = IndustrialLabelMono.copy(fontSize = 10.sp)
                    )
                    Text(
                        text = "TELECOMMUNICATIONS RESEARCH UNIT",
                        style = IndustrialLabelMono.copy(fontSize = 10.sp)
                    )
                }
            }
        }
    }
}
