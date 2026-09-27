package app.umbra.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Black = Color(0xFF000000)
val BlackSurface = Color(0xFF0E0E0E)
val BlackSurface2 = Color(0xFF1A1A1A)
val Gold = Color(0xFFF5A623)
val GoldDim = Color(0x1FF5A623)
val White = Color(0xFFE2E2E2)
val WhiteMuted = Color(0xFF888888)
val WhiteDim = Color(0xFF3D3D3D)
val GreenOk = Color(0xFF3DD68C)
val RedErr = Color(0xFFFF5C5C)

private val UmbraColorScheme = darkColorScheme(
    primary = Gold,
    onPrimary = Black,
    primaryContainer = GoldDim,
    background = Black,
    surface = BlackSurface,
    surfaceVariant = BlackSurface2,
    onBackground = White,
    onSurface = White,
    onSurfaceVariant = WhiteMuted,
    outline = WhiteDim,
    error = RedErr,
)

@Composable
fun UmbraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = UmbraColorScheme,
        content = content,
    )
}
