package app.relay.android.feature.gateway

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.ColorLegend
import app.relay.android.designsystem.component.FeedMessageRow
import app.relay.android.designsystem.component.Hairline
import app.relay.android.designsystem.component.IconBoxButton
import app.relay.android.designsystem.component.PulseDot
import app.relay.android.designsystem.component.RelayCard
import app.relay.android.designsystem.component.RelayListCard
import app.relay.android.designsystem.component.RelaySwitch
import app.relay.android.designsystem.component.SectionLabel
import app.relay.android.designsystem.component.StackedBar
import app.relay.android.designsystem.component.StatCell
import app.relay.android.designsystem.component.SwitchSize
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

@Composable
fun GatewayRoute(
    viewModel: GatewayViewModel,
    onOpenSettings: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenMessage: (String) -> Unit,
    /** Asks to stop the gateway and close the app. */
    onExit: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    GatewayScreen(state, viewModel::setOnline, onOpenSettings, onOpenMessages, onOpenMessage, onExit)
}

@Composable
fun GatewayScreen(
    state: GatewayUiState,
    onToggleOnline: (Boolean) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMessages: () -> Unit,
    onOpenMessage: (String) -> Unit,
    onExit: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Header(state.deviceLine, onOpenSettings, onExit) }
        item { StatusCard(state, onToggleOnline) }
        item { TodayCard(state.today) }
        item { ThroughputCard(state) }
        item { LiveHeader(onOpenMessages) }
        item {
            RelayListCard {
                state.feed.forEach { row -> FeedMessageRow(row) { onOpenMessage(row.id) } }
                if (state.feed.isEmpty()) {
                    Text(
                        "Waiting for the first request…",
                        style = RelayType.Body,
                        color = RelayColors.Dim,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(deviceLine: String, onOpenSettings: () -> Unit, onExit: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text("Relay", style = sans(24f, FontWeight.SemiBold, lineHeight = 1.05f, tracking = -0.03f), color = RelayColors.Text)
            Text(deviceLine, style = mono(12f), color = RelayColors.Muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HeaderButton(RelayIcons.Power, "Stop and exit", onExit, tint = RelayColors.Red)
            HeaderButton(RelayIcons.Settings, "Settings", onOpenSettings)
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, tint: Color = RelayColors.Text) {
    IconBoxButton(
        icon,
        contentDescription = contentDescription,
        onClick = onClick,
        radius = 14.dp,
        iconSize = 20.dp,
        background = RelayColors.Surface,
        tint = tint,
    )
}

@Composable
private fun StatusCard(state: GatewayUiState, onToggleOnline: (Boolean) -> Unit) {
    val (color, label, title, sub) = when (state.mode) {
        GatewayMode.ONLINE -> Quad(
            RelayColors.Lime,
            "ONLINE",
            "Gateway is live",
            "Listening for ${state.enabledProjects} project${if (state.enabledProjects == 1) "" else "s"}. " +
                "Up ${state.uptime ?: "0m"} on ${state.server}.",
        )
        GatewayMode.CONNECTING -> Quad(
            RelayColors.Blue,
            "CONNECTING",
            "Reconnecting…",
            "Requests wait on ${state.server} until the phone is back online.",
        )
        GatewayMode.ON_BATTERY -> Quad(
            RelayColors.Amber,
            "ON BATTERY",
            "Waiting for charger",
            "Only send while charging is on. " +
                (if (state.inQueue > 0) "${state.inQueue} queued message${if (state.inQueue == 1) "" else "s"} send" else "Requests send") +
                " when the phone is plugged in.",
        )
        GatewayMode.PAUSED -> Quad(
            RelayColors.Amber,
            "PAUSED",
            "Gateway paused",
            "New requests are held on the server and send when you resume.",
        )
    }
    RelayCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PulseDot(color, pulsing = state.mode == GatewayMode.ONLINE)
                Text(label, style = mono(11.5f, FontWeight.SemiBold, tracking = 0.14f), color = color)
            }
            RelaySwitch(state.switchOn, onToggleOnline, contentDescription = "Toggle gateway", size = SwitchSize.Large)
        }
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, style = sans(26f, FontWeight.SemiBold, lineHeight = 1.1f, tracking = -0.025f), color = RelayColors.Text)
            Text(sub, style = RelayType.Body, color = RelayColors.Muted)
        }
        if (state.mode == GatewayMode.PAUSED && state.heldCount > 0) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(RelayColors.tint(RelayColors.Amber, 0.12f))
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${state.heldCount} requests waiting on server", style = sans(13f, FontWeight.Medium), color = RelayColors.Amber)
                Text("HELD", style = mono(11f, FontWeight.Medium, tracking = 0.08f), color = RelayColors.Amber)
            }
        }
        Hairline(RelayColors.Line)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCell("Sending SIM", state.sim, Modifier.weight(1f))
            StatCell("Latency", state.latency, Modifier.weight(1f))
            StatCell("Battery", state.battery, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TodayCard(today: TodayUi) {
    RelayCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionLabel("Today")
            Text("${today.deliveryRate} delivered", style = mono(12.5f, FontWeight.Medium), color = RelayColors.Lime)
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(today.sent, style = mono(54f, FontWeight.Medium, lineHeight = 0.95f, tracking = -0.05f), color = RelayColors.Text)
            Text("sent", style = sans(15f), color = RelayColors.Muted, modifier = Modifier.padding(bottom = 6.dp))
        }
        StackedBar(
            listOf(
                today.deliveredFraction to RelayColors.Lime,
                today.awaitingFraction to RelayColors.Blue,
                today.failedFraction to RelayColors.Red,
            ),
            gap = 2.dp,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ColorLegend(RelayColors.Lime, "Delivered", today.delivered, Modifier.weight(1f))
            ColorLegend(RelayColors.Blue, "Awaiting", today.awaiting, Modifier.weight(1f))
            ColorLegend(RelayColors.Red, "Failed", today.failed, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ThroughputCard(state: GatewayUiState) {
    RelayCard(spacing = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            SectionLabel("Throughput")
            Text("per minute · last 30 min", style = sans(12f), color = RelayColors.Dim)
        }
        Row(
            Modifier.fillMaxWidth().height(64.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            state.bars.forEachIndexed { i, h ->
                val height by animateFloatAsState(h, tween(300), label = "bar")
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(height)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i == state.bars.lastIndex) RelayColors.Lime else RelayColors.BarIdle),
                )
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val style = mono(12.5f)
            Text("now ${state.nowRate}/min", style = style, color = RelayColors.Muted)
            Text("${state.inQueue} in queue", style = style, color = RelayColors.Muted)
            Text("limit ${state.rateLimit}/min", style = style, color = RelayColors.Muted)
        }
    }
}

@Composable
private fun LiveHeader(onOpenMessages: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel("Live")
        Row(
            Modifier.clickable(onClick = onOpenMessages).padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text("All messages", style = sans(13f, FontWeight.Medium), color = RelayColors.Lime)
            Icon(RelayIcons.ChevronRight, contentDescription = null, tint = RelayColors.Lime, modifier = Modifier.size(16.dp))
        }
    }
}

private data class Quad(val color: Color, val label: String, val title: String, val sub: String)
