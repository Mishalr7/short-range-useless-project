package com.shortrange.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shortrange.app.ui.theme.IndustrialDataMono
import com.shortrange.app.ui.theme.IndustrialLabelMono
import com.shortrange.app.ui.theme.IndustrialTelemetryValue
import com.shortrange.app.ui.theme.LightBorder
import com.shortrange.app.ui.theme.PrimaryBlack
import com.shortrange.app.ui.theme.TechnicalWhite

@Composable
fun IndustrialPanel(
    modifier: Modifier = Modifier,
    borderColor: Color = LightBorder,
    borderWidth: Dp = 1.dp,
    backgroundColor: Color = TechnicalWhite,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(borderWidth, borderColor, RoundedCornerShape(2.dp))
            .background(backgroundColor, RoundedCornerShape(2.dp))
            .padding(16.dp),
        content = content
    )
}

@Composable
fun TelemetryCell(
    label: String,
    value: String,
    valueColor: Color = PrimaryBlack,
    modifier: Modifier = Modifier
) {
    IndustrialPanel(
        modifier = modifier,
        borderColor = LightBorder
    ) {
        Text(
            text = label.uppercase(),
            style = IndustrialLabelMono
        )
        Text(
            text = value.uppercase(),
            style = IndustrialDataMono,
            color = valueColor,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
