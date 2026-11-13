package app.relay.android.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.PrimaryButton
import app.relay.android.designsystem.component.RelaySheet
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.component.SectionLabel
import app.relay.android.designsystem.component.StatusChip
import app.relay.android.designsystem.component.rememberLastNonNull
import app.relay.android.designsystem.format.Format
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans
import app.relay.gateway.service.StepState
import app.relay.gateway.service.TimelineStep

@Composable
fun MessageDetailSheet(
    detail: MessageDetailUi?,
    onDismiss: () -> Unit,
    onCopyId: (String) -> Unit,
    onRetry: () -> Unit,
) {
    val shown = rememberLastNonNull(detail)
    RelaySheet(visible = detail != null, onDismiss = onDismiss, spacing = 16.dp) {
        val d = shown ?: return@RelaySheet
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            StatusChip(d.status, large = true)
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(RelayColors.SurfaceRaised).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(RelayIcons.Close, contentDescription = "Close", tint = RelayColors.Text, modifier = Modifier.size(18.dp))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("To", style = sans(12f), color = RelayColors.Dim)
            Text(d.to, style = mono(22f, FontWeight.Medium, tracking = -0.02f), color = RelayColors.Text)
        }
        Text(
            d.body,
            style = sans(15f, lineHeight = 1.5f),
            color = RelayColors.Text,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(RelayColors.SurfaceRaised)
                .padding(horizontal = 15.dp, vertical = 14.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Meta("Project", d.project, mono = false, modifier = Modifier.weight(1f))
                Meta("Segments", d.segments, modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Meta("SIM", d.sim, modifier = Modifier.weight(1f))
                Meta("Message ID", d.id, modifier = Modifier.weight(1f))
            }
        }
        Column {
            SectionLabel("Delivery timeline", Modifier.padding(bottom = 12.dp))
            d.steps.forEachIndexed { i, step -> TimelineRow(step, next = d.steps.getOrNull(i + 1)) }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .border(1.dp, RelayColors.Line, RoundedCornerShape(16.dp))
                .padding(horizontal = 15.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Status webhook", style = sans(11.5f), color = RelayColors.Dim)
            Text(d.hookTarget, style = mono(12.5f, FontWeight.Medium, lineHeight = 1.45f), color = RelayColors.Text)
            Text(
                d.hookResult,
                style = mono(12f, FontWeight.Medium),
                color = when (d.hookTone) {
                    HookTone.OK -> RelayColors.Lime
                    HookTone.ERROR -> RelayColors.Red
                    HookTone.PENDING, HookTone.NONE -> RelayColors.Dim
                },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton(
                "Copy ID",
                onClick = { onCopyId(d.id) },
                modifier = Modifier.weight(1f),
                height = 50.dp,
                radius = 15.dp,
                textStyle = sans(14.5f, FontWeight.Medium),
            )
            if (d.canRetry) {
                PrimaryButton("Retry send", onClick = onRetry, modifier = Modifier.weight(1f), height = 50.dp)
            }
        }
    }
}

@Composable
private fun Meta(label: String, value: String, modifier: Modifier = Modifier, mono: Boolean = true) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = sans(11.5f), color = RelayColors.Dim)
        Text(
            value,
            style = if (mono) mono(13f, FontWeight.Medium) else sans(13.5f, FontWeight.Medium),
            color = RelayColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TimelineRow(step: TimelineStep, next: TimelineStep?) {
    val color = stepColor(step.state)
    val hollow = step.state == StepState.TODO || step.state == StepState.CURRENT
    val line = when (next?.state) {
        null -> Color.Transparent
        StepState.DONE, StepState.FAILED -> RelayColors.Lime.copy(alpha = 0.45f)
        else -> RelayColors.Track
    }
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Column(
            Modifier.width(14.dp).fillMaxHeight().padding(top = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(if (hollow) Color.Transparent else color)
                    .border(2.dp, color, CircleShape),
            )
            Box(Modifier.weight(1f).heightIn(min = 18.dp).padding(vertical = 3.dp).width(2.dp).background(line))
        }
        Row(
            Modifier.weight(1f).padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    step.label,
                    style = sans(14f, FontWeight.Medium),
                    color = when (step.state) {
                        StepState.TODO -> RelayColors.Dim
                        StepState.FAILED -> RelayColors.Red
                        else -> RelayColors.Text
                    },
                )
                Text(step.detail, style = sans(12f), color = RelayColors.Dim)
            }
            Text(Format.clock(step.at), style = mono(12f), color = RelayColors.Muted)
        }
    }
}

private fun stepColor(state: StepState) = when (state) {
    StepState.DONE -> RelayColors.Lime
    StepState.FAILED -> RelayColors.Red
    StepState.CURRENT -> RelayColors.Blue
    StepState.TODO -> RelayColors.Track
}
