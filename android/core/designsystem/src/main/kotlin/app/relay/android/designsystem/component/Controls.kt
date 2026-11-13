package app.relay.android.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

enum class SwitchSize(val width: Dp, val height: Dp, val knob: Dp) {
    Large(52.dp, 30.dp, 24.dp),
    Small(44.dp, 26.dp, 20.dp),
}

@Composable
fun RelaySwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: SwitchSize = SwitchSize.Small,
) {
    val inset = 3.dp
    val knobX by animateDpAsState(if (checked) size.width - size.knob - inset else inset, tween(180), label = "knob")
    val track by animateColorAsState(if (checked) RelayColors.Lime else RelayColors.Track, tween(180), label = "track")
    Box(
        modifier
            .size(size.width, size.height)
            .clip(CircleShape)
            .background(track)
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .semantics { this.contentDescription = contentDescription },
    ) {
        Box(
            Modifier
                .offset(x = knobX, y = inset)
                .size(size.knob)
                .background(if (checked) RelayColors.Ink else RelayColors.Muted, CircleShape),
        )
    }
}

/**
 * Equal-width segments; the selected one is a light pill. [captions] adds a smaller second
 * line under a segment (e.g. the carrier under "SIM 1") instead of crowding one long label.
 */
@Composable
fun <T> SegmentedControl(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = RelayColors.Surface,
    segmentHeight: Dp = 38.dp,
    textSize: Float = 13f,
    captions: Map<T, String> = emptyMap(),
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(trackColor)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (value, label) ->
            val on = value == selected
            Column(
                Modifier
                    .weight(1f)
                    .height(if (captions.isEmpty()) segmentHeight else segmentHeight + 12.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (on) RelayColors.Text else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    label,
                    style = sans(textSize, FontWeight.Medium),
                    color = if (on) RelayColors.Ink else RelayColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                captions[value]?.let { caption ->
                    Text(
                        caption,
                        style = sans(11f),
                        color = if (on) RelayColors.Ink.copy(alpha = 0.6f) else RelayColors.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Rounded filter / choice chip; [count] renders as a faded mono suffix. */
@Composable
fun PillChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: String? = null,
    height: Dp = 36.dp,
    horizontalPadding: Dp = 13.dp,
    textSize: Float = 13f,
) {
    val fg = if (selected) RelayColors.Ink else RelayColors.Muted
    Row(
        modifier
            .height(height)
            .clip(CircleShape)
            .background(if (selected) RelayColors.Text else RelayColors.Surface)
            .border(1.dp, if (selected) RelayColors.Text else RelayColors.Line, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text(label, style = sans(textSize, FontWeight.Medium), color = fg)
        if (count != null) Text(count, style = mono(11.5f, FontWeight.Medium), color = fg.copy(alpha = 0.7f))
    }
}

/** Large lime call to action. [active] = false keeps it tappable but greyed (the tap explains what's missing). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = true,
    height: Dp = 54.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) RelayColors.Lime else RelayColors.Track)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = RelayType.ButtonStrong, color = if (active) RelayColors.Ink else RelayColors.Dim)
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    height: Dp = 46.dp,
    radius: Dp = 14.dp,
    textStyle: androidx.compose.ui.text.TextStyle = RelayType.Button,
    contentColor: Color = RelayColors.Text,
    background: Color = RelayColors.SurfaceRaised,
    borderColor: Color = RelayColors.Line,
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp),
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(radius))
            .clickable(onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(17.dp))
        Text(text, style = textStyle, color = contentColor)
    }
}

/** Compact lime pill with a plus, used for "New". */
@Composable
fun NewButton(text: String, onClick: () -> Unit, icon: ImageVector, modifier: Modifier = Modifier) {
    Row(
        modifier
            .height(40.dp)
            .clip(CircleShape)
            .background(RelayColors.Lime)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RelayColors.Ink, modifier = Modifier.size(18.dp))
        Text(text, style = sans(14f, FontWeight.SemiBold), color = RelayColors.Ink)
    }
}

/** Square icon button (copy, reveal, settings…). */
@Composable
fun IconBoxButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    radius: Dp = 12.dp,
    iconSize: Dp = 18.dp,
    background: Color = RelayColors.SurfaceRaised,
    tint: Color = RelayColors.Text,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(radius))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

/** Value box with trailing actions, e.g. endpoint URL + copy. */
@Composable
fun CopyField(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = RelayColors.Text,
    background: Color = RelayColors.Surface,
    actions: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(16.dp))
            .padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = mono(12.5f, FontWeight.Medium, lineHeight = 1.45f), color = textColor, modifier = Modifier.weight(1f))
        actions()
    }
}
