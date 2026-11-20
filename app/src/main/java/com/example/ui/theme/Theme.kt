package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val QuantFXDarkColorScheme = darkColorScheme(
    primary = EmeraldBull,
    onPrimary = ObsidianCanvas,
    primaryContainer = EmeraldBullDim,
    onPrimaryContainer = EmeraldBull,
    secondary = CyanAlgo,
    onSecondary = ObsidianCanvas,
    secondaryContainer = CyanAlgoDim,
    onSecondaryContainer = CyanAlgo,
    tertiary = AmberWarn,
    onTertiary = ObsidianCanvas,
    error = CrimsonBear,
    onError = TextPrimary,
    errorContainer = CrimsonBearDim,
    onErrorContainer = CrimsonBear,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = SlateSurface,
    onSurface = TextPrimary,
    surfaceVariant = SlateElevated,
    onSurfaceVariant = TextSecondary,
    outline = HairlineBorder,
    outlineVariant = SlateCardHover
)

private val QuantFXShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun QuantFXTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = QuantFXDarkColorScheme,
        typography = QuantFXTypography,
        shapes = QuantFXShapes,
        content = content
    )
}
