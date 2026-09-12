package com.shortrange.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PrimaryBlack,
    onPrimary = TechnicalWhite,
    primaryContainer = CardSurface,
    onPrimaryContainer = PrimaryBlack,
    secondary = InstrumentGrey,
    onSecondary = TechnicalWhite,
    background = TechnicalWhite,
    onBackground = PrimaryBlack,
    surface = TechnicalWhite,
    onSurface = PrimaryBlack,
    error = SignalRed,
    onError = TechnicalWhite,
    outline = LightBorder
)

// Industrial design uses square / 0-2dp corner geometry
val IndustrialShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(2.dp),
    medium = RoundedCornerShape(2.dp),
    large = RoundedCornerShape(4.dp),
    extraLarge = RoundedCornerShape(4.dp)
)

@Composable
fun ShortRangeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography = Typography,
        shapes = IndustrialShapes,
        content = content
    )
}
