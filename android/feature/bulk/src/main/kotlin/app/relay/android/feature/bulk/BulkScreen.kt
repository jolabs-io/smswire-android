package app.relay.android.feature.bulk

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.Hairline
import app.relay.android.designsystem.component.RelayCard
import app.relay.android.designsystem.component.RelayListCard
import app.relay.android.designsystem.component.ScreenHeader
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.component.SectionLabel
import app.relay.android.designsystem.component.StackedBar
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.GeistMono
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

@Composable
fun BulkRoute(viewModel: BulkViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    BulkScreen(state, viewModel::toggle)
}

@Composable
fun BulkScreen(state: BulkUiState, onToggle: (id: String, running: Boolean) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Bulk") }
        item {
            Text(
                buildAnnotatedString {
                    append("Batches arrive from your web app via ")
                    withStyle(SpanStyle(fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 12.5.sp, color = RelayColors.Text)) {
                        append("POST /messages/bulk")
                    }
                    append(", or from Send on the web dashboard. Pausing here pauses it everywhere.")
                },
                style = RelayType.Body,
                color = RelayColors.Muted,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
        item { SectionLabel("Active", Modifier.padding(start = 4.dp, top = 6.dp)) }
        items(state.active, key = { it.id }) { ActiveBatchCard(it, onToggle) }
        if (state.active.isEmpty()) {
            item {
                Text(
                    "No batches running.",
                    style = sans(13.5f),
                    color = RelayColors.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RelayColors.Line, RoundedCornerShape(24.dp))
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                )
            }
        }
        if (state.done.isNotEmpty()) {
            item { SectionLabel("Completed", Modifier.padding(start = 4.dp, top = 10.dp)) }
            item {
                RelayListCard {
                    state.done.forEach { DoneBatchRow(it) }
                }
            }
        }
    }
}

@Composable
private fun ActiveBatchCard(batch: ActiveBatchUi, onToggle: (String, Boolean) -> Unit) {
    val statusColor = if (batch.running) RelayColors.Lime else RelayColors.Amber
    RelayCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(batch.name, style = sans(16.5f, FontWeight.SemiBold, lineHeight = 1.25f, tracking = -0.01f), color = RelayColors.Text)
                Text(batch.meta, style = RelayType.Caption, color = RelayColors.Dim)
            }
            Text(
                if (batch.running) "RUNNING" else "PAUSED",
                style = mono(10.5f, FontWeight.SemiBold, tracking = 0.12f),
                color = statusColor,
                modifier = Modifier
                    .border(1.dp, statusColor, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(batch.done, style = mono(32f, FontWeight.Medium, lineHeight = 1f, tracking = -0.04f), color = RelayColors.Text)
            Text("/ ${batch.total}", style = mono(14f), color = RelayColors.Dim)
            Text(
                batch.percent,
                style = mono(14f, FontWeight.Medium),
                color = RelayColors.Muted,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
        StackedBar(
            listOf(
                batch.deliveredFraction to RelayColors.Lime,
                batch.failedFraction to RelayColors.Red,
                batch.awaitingFraction to RelayColors.Blue,
            ),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Count("Delivered", batch.delivered, Modifier.weight(1f))
            Count("Pending", batch.pending, Modifier.weight(1f))
            Count("Failed", batch.failed, Modifier.weight(1f))
        }
        Hairline(RelayColors.Line)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(batch.rateLine, style = mono(12.5f), color = RelayColors.Muted)
            SecondaryButton(
                if (batch.running) "Pause" else "Resume",
                onClick = { onToggle(batch.id, batch.running) },
                icon = if (batch.running) RelayIcons.Pause else RelayIcons.Play,
                height = 40.dp,
                radius = 12.dp,
                textStyle = sans(13.5f, FontWeight.Medium),
                contentPadding = PaddingValues(start = 11.dp, end = 14.dp),
            )
        }
    }
}

@Composable
private fun Count(label: String, value: String, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = sans(12f), color = RelayColors.Muted)
        Text(value, style = mono(14f, FontWeight.Medium), color = RelayColors.Text)
    }
}

@Composable
private fun DoneBatchRow(batch: DoneBatchUi) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(batch.name, style = sans(14.5f, FontWeight.Medium), color = RelayColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(batch.summary, style = RelayType.Caption, color = RelayColors.Dim)
        }
        Text(batch.okPercent, style = mono(14f, FontWeight.Medium), color = RelayColors.Lime)
    }
    Hairline()
}
