package app.relay.android.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.DetailTopBar
import app.relay.android.designsystem.component.LeaveWorkspaceOptions
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.gateway.service.SwitchResult

/**
 * Settings → Switch workspace, or a pairing QR scanned while paired. [onScan] opens the scanner;
 * its result goes to [SwitchWorkspaceViewModel.onScanned]. [onSwitched] runs once the phone moved.
 */
@Composable
fun SwitchWorkspaceRoute(
    viewModel: SwitchWorkspaceViewModel,
    onScan: () -> Unit,
    onBack: () -> Unit,
    onSwitched: (SwitchResult) -> Unit,
) {
    val ui by viewModel.state.collectAsStateWithLifecycle()
    val form by viewModel.form.state.collectAsStateWithLifecycle()
    val switched by viewModel.switched.collectAsStateWithLifecycle()
    LaunchedEffect(switched) { switched?.let(onSwitched) }
    val f = viewModel.form
    SwitchWorkspaceScreen(
        ui,
        form,
        onBack = { if (!form.busy) onBack() },
        onKeepHistory = viewModel::setKeepHistory,
        onScan = onScan,
        onCode = f::onCode,
        onServer = f::onServer,
        onToggleServer = f::toggleServer,
        onSubmit = viewModel::submit,
    )
}

@Composable
fun SwitchWorkspaceScreen(
    ui: SwitchUi,
    form: PairingUi,
    onBack: () -> Unit,
    onKeepHistory: (Boolean) -> Unit,
    onScan: () -> Unit,
    onCode: (String) -> Unit,
    onServer: (String) -> Unit,
    onToggleServer: () -> Unit,
    onSubmit: () -> Unit,
) {
    val current = ui.workspace.ifEmpty { "this workspace" }
    Column(Modifier.fillMaxSize().background(RelayColors.Background)) {
        DetailTopBar("Switch workspace", onBack)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "This phone sends for $current. Scan the pairing code of the other workspace (on its web dashboard: " +
                    "Phones → Pair a phone). The phone joins it first, then leaves $current, whose projects lose this phone.",
                style = RelayType.Body,
                color = RelayColors.Muted,
            )
            LeaveWorkspaceOptions(ui.keepHistory, onKeepHistory, ui.unsent, ui.workspace)
            PairingForm(
                form,
                onScan,
                onCode,
                onServer,
                onToggleServer,
                onSubmit,
                scanLabel = "Scan the other workspace's code",
                submitLabel = "Switch with code",
                busyLabel = "Switching…",
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}
