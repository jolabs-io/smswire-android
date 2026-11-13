package app.relay.android.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/** Builds a Geist text style. [lineHeight] is a multiple of the font size, [tracking] is in em. */
fun sans(size: Float, weight: FontWeight = FontWeight.Normal, lineHeight: Float? = null, tracking: Float = 0f) =
    style(GeistSans, size, weight, lineHeight, tracking)

/** Geist Mono: numbers, phone numbers, keys and code. */
fun mono(size: Float, weight: FontWeight = FontWeight.Normal, lineHeight: Float? = null, tracking: Float = 0f) =
    style(GeistMono, size, weight, lineHeight, tracking)

private fun style(
    family: androidx.compose.ui.text.font.FontFamily,
    size: Float,
    weight: FontWeight,
    lineHeight: Float?,
    tracking: Float,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight?.let { (size * it).sp } ?: TextUnit.Unspecified,
    letterSpacing = if (tracking == 0f) TextUnit.Unspecified else tracking.em,
)

/** Named styles that recur across screens. */
object RelayType {
    val ScreenTitle = sans(28f, FontWeight.SemiBold, lineHeight = 1.1f, tracking = -0.03f)
    val TopBarTitle = sans(18f, FontWeight.SemiBold)
    val SheetTitle = sans(22f, FontWeight.SemiBold, lineHeight = 1.15f, tracking = -0.02f)
    val SectionLabel = mono(11f, FontWeight.Medium, tracking = 0.12f)
    val Body = sans(13.5f, lineHeight = 1.45f)
    val Hint = sans(12.5f, lineHeight = 1.45f)
    val Caption = sans(12f)
    val CaptionSmall = sans(11.5f)
    val Button = sans(14f, FontWeight.Medium)
    val ButtonStrong = sans(15.5f, FontWeight.SemiBold)
    val RowTitle = sans(14.5f, FontWeight.Medium)
    val Value = mono(13f, FontWeight.Medium)
    val ValueLarge = mono(16f, FontWeight.Medium)
}
