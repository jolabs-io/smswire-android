package app.relay.android.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val scheme = darkColorScheme(
    primary = RelayColors.Lime,
    onPrimary = RelayColors.Ink,
    background = RelayColors.Background,
    onBackground = RelayColors.Text,
    surface = RelayColors.Surface,
    onSurface = RelayColors.Text,
    surfaceVariant = RelayColors.SurfaceRaised,
    onSurfaceVariant = RelayColors.Muted,
    outline = RelayColors.Line,
    error = RelayColors.Red,
)

/** Relay is dark-only by design: it's a console you glance at, often on a charger. */
@Composable
fun RelayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
