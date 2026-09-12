package com.shortrange.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.unit.dp
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.SignalRed

@Composable
fun ProximityDiagram(
    separationRatio: Float, // 0.0f = touching, 1.0f = lost limit
    isSevered: Boolean = false,
    lineColor: Color = PrimaryBlack,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(40.dp)) {
            val startX = 36.dp.toPx()
            val endX = size.width - 36.dp.toPx()
            val midY = size.height / 2f

            // Baseline light reference rail
            drawLine(
                color = LightBorder,
                start = Offset(startX, midY),
                end = Offset(endX, midY),
                strokeWidth = 2.dp.toPx()
            )

            // Active connection line from A to B's position
            val bPosRatio = separationRatio.coerceIn(0.12f, 0.95f)
            val currentBX = startX + (endX - startX) * bPosRatio

            if (isSevered) {
                // Severed dashed red line
                drawLine(
                    color = SignalRed,
                    start = Offset(startX, midY),
                    end = Offset(currentBX, midY),
                    strokeWidth = 3.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                )
            } else {
                // Solid connecting line
                drawLine(
                    color = lineColor,
                    start = Offset(startX, midY),
                    end = Offset(currentBX, midY),
                    strokeWidth = 3.dp.toPx()
                )
            }

            // Fixed Node A circle
            drawCircle(
                color = if (isSevered) SignalRed else PrimaryBlack,
                radius = 6.dp.toPx(),
                center = Offset(startX, midY)
            )

            if (isSevered) {
                // Red "X" for severed node B
                val xSize = 8.dp.toPx()
                drawLine(
                    color = SignalRed,
                    start = Offset(currentBX - xSize, midY - xSize),
                    end = Offset(currentBX + xSize, midY + xSize),
                    strokeWidth = 3.5.dp.toPx()
                )
                drawLine(
                    color = SignalRed,
                    start = Offset(currentBX - xSize, midY + xSize),
                    end = Offset(currentBX + xSize, midY - xSize),
                    strokeWidth = 3.5.dp.toPx()
                )
            } else {
                // Active Node B circle
                drawCircle(
                    color = lineColor,
                    radius = 6.dp.toPx(),
                    center = Offset(currentBX, midY)
                )
            }
        }

        // Labels A and B
        Text(
            text = "A",
            style = IndustrialDataMono,
            color = if (isSevered) SignalRed else PrimaryBlack,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = "B",
            style = IndustrialDataMono,
            color = if (isSevered) SignalRed else PrimaryBlack,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}
