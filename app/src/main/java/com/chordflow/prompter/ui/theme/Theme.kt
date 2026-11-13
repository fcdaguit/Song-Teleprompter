package com.chordflow.prompter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Pure black stage background. Nothing in this app is ever lighter than this. */
val StageBlack = Color(0xFF000000)

/** Primary phosphor green, as specified. */
val PhosphorGreen = Color(0xFF00FF41)

/** Brighter accent for the active lyric line. */
val PhosphorBright = Color(0xFF39FF14)

/** Dimmed green for inactive lines, labels and borders. */
val PhosphorDim = Color(0xFF127A2C)

/** Mid green for secondary text that still has to be readable from a stage. */
val PhosphorMid = Color(0xFF17B33C)

/** Near-black panel fill, used sparingly for HUD chrome. */
val PanelBlack = Color(0xFF060806)

private val ChordFlowColorScheme = darkColorScheme(
    primary = PhosphorGreen,
    onPrimary = StageBlack,
    primaryContainer = PanelBlack,
    onPrimaryContainer = PhosphorGreen,
    secondary = PhosphorMid,
    onSecondary = StageBlack,
    secondaryContainer = PanelBlack,
    onSecondaryContainer = PhosphorGreen,
    tertiary = PhosphorBright,
    onTertiary = StageBlack,
    background = StageBlack,
    onBackground = PhosphorGreen,
    surface = StageBlack,
    onSurface = PhosphorGreen,
    surfaceVariant = PanelBlack,
    onSurfaceVariant = PhosphorMid,
    outline = PhosphorDim,
    outlineVariant = PhosphorDim,
    error = Color(0xFFFF5C4D),
    onError = StageBlack,
    scrim = StageBlack
)

private val mono = FontFamily.Monospace

private val ChordFlowTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp
    ),
    headlineLarge = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp
    ),
    headlineSmall = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelLarge = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        letterSpacing = 1.sp
    ),
    labelSmall = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 1.sp
    )
)

@Composable
fun ChordFlowTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ChordFlowColorScheme,
        typography = ChordFlowTypography,
        content = content
    )
}
