package com.shortrange.app.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shortrange.app.R
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.TechnicalWhite
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    LaunchedEffect(Unit) {
        delay(2000)
        onTimeout()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TechnicalWhite)
            .clickable { onTimeout() }
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top status row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = "SR/01", style = IndustrialLabelMono, color = PrimaryBlack)
            Text(text = "v1.0.0", style = IndustrialLabelMono, color = PrimaryBlack)
        }

        // Center Branding
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.ic_sr_logo),
                contentDescription = "Short Range Logo",
                modifier = Modifier
                    .width(160.dp)
                    .height(104.dp)
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "SHORT RANGE",
                style = IndustrialDataMono.copy(
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.5.sp
                ),
                color = PrimaryBlack
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "PROXIMITY COMMUNICATION SYSTEM",
                style = IndustrialLabelMono.copy(letterSpacing = 1.2.sp),
                color = PrimaryBlack
            )

            Spacer(modifier = Modifier.height(44.dp))

            Text(
                text = "A TELEPHONE\nTHAT WORKS\nWHEN YOU'RE NEAR.",
                style = IndustrialLabelMono.copy(
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = PrimaryBlack
            )
        }

        // Bottom progress & unit credit
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .height(4.dp),
                color = PrimaryBlack,
                trackColor = LightBorder
            )

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "INITIALIZING...",
                style = IndustrialLabelMono,
                color = PrimaryBlack
            )

            Spacer(modifier = Modifier.height(24.dp))
            HorizontalDivider(thickness = 1.dp, color = LightBorder)
            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "TELECOMMUNICATIONS RESEARCH UNIT",
                    style = IndustrialLabelMono.copy(fontSize = 10.sp),
                    color = PrimaryBlack
                )
                Text(
                    text = "EST. 2024",
                    style = IndustrialLabelMono.copy(fontSize = 10.sp),
                    color = PrimaryBlack
                )
            }
        }
    }
}
