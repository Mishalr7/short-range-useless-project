package com.shortrange.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.shortrange.app.ui.theme.InactiveMeter
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.SignalRed
import com.shortrange.app.ui.theme.TelemetryAmber
import com.shortrange.app.ui.theme.TelemetryGreen

@Composable
fun SegmentedMeter(
    activeSegments: Int,
    totalSegments: Int = 20,
    modifier: Modifier = Modifier,
    activeColor: Color = PrimaryBlack,
    inactiveColor: Color = InactiveMeter
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        for (i in 0 until totalSegments) {
            val isActive = i < activeSegments
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (isActive) activeColor else inactiveColor)
            )
        }
    }
}
