package app.relay.android.designsystem.theme

import androidx.compose.ui.graphics.Color

/** Relay's dark console palette. Lime = delivered / primary, amber = held, blue = in flight, red = failed. */
object RelayColors {
    val Background = Color(0xFF0E0F0D)
    val Surface = Color(0xFF171915)
    val SurfaceRaised = Color(0xFF20231E)
    val SurfaceHover = Color(0xFF262A23)
    val Track = Color(0xFF2F332B)
    val Line = Color(0xFF2A2E27)
    val Divider = Color(0xFF20231E)
    val DividerSoft = Color(0xFF1C1F1A)
    val BarIdle = Color(0xFF3A4034)
    val CodeBackground = Color(0xFF0A0B09)
    val CodeText = Color(0xFFC9CEC1)

    val Text = Color(0xFFF1F3EC)
    val Muted = Color(0xFFA6AB9F)
    val Dim = Color(0xFF878C80)
    val Placeholder = Color(0xFF6E7368)
    val Ink = Color(0xFF11130F)

    val Lime = Color(0xFFC8F25A)
    val Amber = Color(0xFFF2C14E)
    val Blue = Color(0xFF7CC4F5)
    val Red = Color(0xFFFF7A6B)

    val Scrim = Color(0x9E000000)

    /** Translucent fill behind a coloured label (status chips, banners). */
    fun tint(color: Color, alpha: Float = 0.16f) = color.copy(alpha = alpha)
}
