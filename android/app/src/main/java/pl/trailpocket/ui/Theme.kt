package pl.trailpocket.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Forest = Color(0xFF245F43)
val Ink = Color(0xFF172E24)
val Moss = Color(0xFFE4EDD9)
val Paper = Color(0xFFF7F8F2)

@Composable
fun TrailTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Forest, onPrimary = Color.White,
            primaryContainer = Moss, onPrimaryContainer = Ink, secondary = Forest,
            secondaryContainer = Moss, onSecondaryContainer = Forest, surfaceTint = Forest,
            background = Paper, onBackground = Ink, surface = Color.White, onSurface = Ink,
            surfaceVariant = Color(0xFFEEF1E8), onSurfaceVariant = Color(0xFF647367),
            outline = Color(0xFFB7C4B5)),
        typography = Typography(), content = content)
}
