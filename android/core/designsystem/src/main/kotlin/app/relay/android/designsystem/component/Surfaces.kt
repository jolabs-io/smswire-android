package app.relay.android.designsystem.component

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans
import app.relay.protocol.MessageStatus

@Composable
fun RelayCard(
    modifier: Modifier = Modifier,
    radius: Dp = 24.dp,
    padding: Dp = 18.dp,
    spacing: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(RelayColors.Surface)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(radius))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Card whose rows sit edge to edge, separated by hairlines. */
@Composable
fun RelayListCard(modifier: Modifier = Modifier, radius: Dp = 24.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(radius))
            .background(RelayColors.Surface)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(radius)),
        content = content,
    )
}

@Composable
fun Hairline(color: Color = RelayColors.Divider) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = RelayColors.Dim) {
    Text(text.uppercase(), style = RelayType.SectionLabel, color = color, modifier = modifier)
}

/** Section label with a link to the full list on the right: "LIVE … All messages ›". */
@Composable
fun SectionHeaderLink(label: String, action: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(label)
        TextLink(action, onClick)
    }
}

/** Lime link with a chevron, for opening another screen: "All messages ›". */
@Composable
fun TextLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(text, style = sans(13f, FontWeight.Medium), color = RelayColors.Lime)
        Icon(RelayIcons.ChevronRight, contentDescription = null, tint = RelayColors.Lime, modifier = Modifier.size(16.dp))
    }
}

/** Label above a value, as used in stat grids. */
@Composable
fun StatCell(label: String, value: String, modifier: Modifier = Modifier, valueColor: Color = RelayColors.Text, large: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = sans(11.5f), color = RelayColors.Dim)
        Text(
            value,
            style = if (large) RelayType.ValueLarge else RelayType.Value,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun StatusChip(status: MessageStatus, modifier: Modifier = Modifier, large: Boolean = false) {
    val visual = status.visual
    Row(
        modifier
            .clip(CircleShape)
            .background(RelayColors.tint(visual.color))
            .padding(horizontal = if (large) 12.dp else 9.dp, vertical = if (large) 6.dp else 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (large) 7.dp else 6.dp),
    ) {
        Box(Modifier.size(if (large) 7.dp else 6.dp).background(visual.color, CircleShape))
        Text(visual.label, style = sans(if (large) 13f else 11.5f, FontWeight.Medium), color = visual.color)
    }
}

/** Live indicator; pings outward while [pulsing]. */
@Composable
fun PulseDot(color: Color, pulsing: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(9.dp)) {
        if (pulsing) {
            val transition = rememberInfiniteTransition(label = "ping")
            val progress by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1600, easing = FastOutSlowInEasing), RepeatMode.Restart),
                label = "progress",
            )
            Box(
                Modifier
                    .size(9.dp)
                    .graphicsLayer {
                        val scale = 1f + 1.8f * progress
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.55f * (1f - progress)
                    }
                    .background(color, CircleShape),
            )
        }
        Box(Modifier.size(9.dp).background(color, CircleShape))
    }
}

/** Horizontal bar split into proportional coloured segments (fractions of the whole). */
@Composable
fun StackedBar(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    gap: Dp = 0.dp,
    track: Color = RelayColors.Line,
) {
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(track),
    ) {
        val full = maxWidth
        Row(Modifier.fillMaxHeight(), horizontalArrangement = Arrangement.spacedBy(gap)) {
            segments.forEach { (fraction, color) ->
                val animated by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(400), label = "segment")
                Box(Modifier.fillMaxHeight().width(full * animated).background(color))
            }
        }
    }
}

@Composable
fun ColorLegend(color: Color, label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Text(label, style = sans(12f), color = RelayColors.Muted)
        }
        Text(value, style = RelayType.ValueLarge, color = RelayColors.Text)
    }
}

/** Header for pushed screens: back arrow, title, optional trailing content. */
@Composable
fun DetailTopBar(title: String, onBack: () -> Unit, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).padding(start = 6.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(RelayIcons.Back, contentDescription = "Back", tint = RelayColors.Text, modifier = Modifier.size(22.dp))
        }
        Text(
            title,
            style = RelayType.TopBarTitle,
            color = RelayColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

/** Large title used at the top of each tab, with an optional action on the right. */
@Composable
fun ScreenHeader(title: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = RelayType.ScreenTitle, color = RelayColors.Text)
        trailing()
    }
}

/** Row in a settings-style list: label (+ optional sub text) and trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = RelayType.RowTitle, color = RelayColors.Text)
            if (subtitle != null) Text(subtitle, style = sans(12.5f, lineHeight = 1.4f), color = RelayColors.Dim)
        }
        trailing()
    }
}

@Composable
fun MonoValue(text: String, color: Color = RelayColors.Lime) {
    Text(text, style = mono(12.5f, FontWeight.Medium), color = color)
}

/** Monospace code that scrolls sideways instead of wrapping (snippets, shell commands). */
@Composable
fun CodeBlock(code: String, modifier: Modifier = Modifier) {
    Text(
        code,
        style = mono(11.5f, lineHeight = 1.65f),
        color = RelayColors.CodeText,
        softWrap = false,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RelayColors.CodeBackground)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(16.dp))
            .horizontalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 16.dp),
    )
}
