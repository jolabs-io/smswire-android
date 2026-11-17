package app.relay.android.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.CopyField
import app.relay.android.designsystem.component.DetailTopBar
import app.relay.android.designsystem.component.IconBoxButton
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.component.SectionLabel
import app.relay.android.designsystem.component.StatCell
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

/** [onCopy] receives a label ("Endpoint URL") and the value to put on the clipboard. */
@Composable
fun ProjectDetailRoute(
    viewModel: ProjectDetailViewModel,
    onBack: () -> Unit,
    onCopy: (label: String, value: String) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val editor by viewModel.editor.collectAsStateWithLifecycle()
    val s = state ?: return
    Column(Modifier.fillMaxSize().background(RelayColors.Background)) {
        DetailTopBar(s.name, onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(RelayColors.Surface)
                    .border(1.dp, RelayColors.Line, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatCell("Today", s.today, Modifier.weight(1f), large = true)
                StatCell("Delivered", s.deliveryRate, Modifier.weight(1f), valueColor = RelayColors.Lime, large = true)
                StatCell("Status", if (s.enabled) "Active" else "Off", Modifier.weight(1f), large = true)
            }

            Section("Endpoint URL") {
                CopyField(s.endpoint) {
                    IconBoxButton(RelayIcons.Copy, "Copy URL", { onCopy("Endpoint URL", s.endpoint) })
                }
                Text("Last request ${s.lastUsed}", style = RelayType.Caption, color = RelayColors.Dim, modifier = Modifier.padding(horizontal = 2.dp))
            }

            Section("Sending") {
                InfoRow("From", s.sendsFrom)
                InfoRow("Default type", s.type)
                InfoRow("Webhook", s.webhook, mono = true)
            }

            Section("Receiving") {
                val editing = editor
                if (editing != null) {
                    ReceiveEditor(editing, s.phoneAllowsAll, viewModel::changeReceive, viewModel::saveReceive, viewModel::cancelReceive)
                } else {
                    InfoRow("Takes", s.receives)
                    SecondaryButton("Change what it receives", viewModel::editReceive, Modifier.fillMaxWidth())
                }
            }

            Text(
                "API keys, the SIM, the message type and the webhook are managed on the web dashboard. What it receives can be changed here or on the web; each shows the other's changes on its own.",
                style = RelayType.Hint,
                color = RelayColors.Dim,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, mono: Boolean = false) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RelayColors.Surface)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = sans(13f), color = RelayColors.Muted)
        Text(
            value,
            style = if (mono) mono(12.5f) else sans(13.5f, FontWeight.Medium),
            color = RelayColors.Text,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun Section(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionLabel(label, Modifier.padding(horizontal = 2.dp))
        content()
    }
}
