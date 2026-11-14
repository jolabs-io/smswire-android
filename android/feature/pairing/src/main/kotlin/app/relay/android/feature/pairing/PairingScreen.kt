package app.relay.android.feature.pairing

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.PrimaryButton
import app.relay.android.designsystem.component.RelayTextField
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.component.SectionLabel
import app.relay.android.designsystem.component.SendOnlyNote
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

/**
 * [onScan] opens the QR scanner; its result goes to [PairingViewModel.onScanned]. [sendOnly]: this is
 * the send-only build, which says what it can't do before it's paired.
 */
@Composable
fun PairingRoute(viewModel: PairingViewModel, onScan: () -> Unit, sendOnly: Boolean = false) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val form = viewModel.form
    PairingScreen(state, onScan, form::onCode, form::onServer, form::toggleServer, viewModel::submit, sendOnly)
}

@Composable
fun PairingScreen(
    state: PairingUi,
    onScan: () -> Unit,
    onCode: (String) -> Unit,
    onServer: (String) -> Unit,
    onToggleServer: () -> Unit,
    onSubmit: () -> Unit,
    sendOnly: Boolean = false,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(RelayColors.Background)
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(RelayColors.Lime), contentAlignment = Alignment.Center) {
            Icon(RelayIcons.Logo, contentDescription = null, tint = RelayColors.Ink, modifier = Modifier.size(34.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pair this phone", style = RelayType.ScreenTitle, color = RelayColors.Text)
            Text(
                "${LocalAppName.current} sends your web app's SMS from this phone's SIM. On the web dashboard, open Phones → Pair a phone, then scan the QR code it shows.",
                style = RelayType.Body,
                color = RelayColors.Muted,
            )
        }
        if (sendOnly) SendOnlyNote()

        PairingForm(state, onScan, onCode, onServer, onToggleServer, onSubmit)
        Spacer(Modifier.height(8.dp))
    }
}

/**
 * Scan button, or a typed code (and server); used by pairing and by switching workspace.
 * [submitLabel] and [busyLabel] name what the code does.
 */
@Composable
fun PairingForm(
    state: PairingUi,
    onScan: () -> Unit,
    onCode: (String) -> Unit,
    onServer: (String) -> Unit,
    onToggleServer: () -> Unit,
    onSubmit: () -> Unit,
    scanLabel: String = "Scan pairing code",
    submitLabel: String = "Pair with code",
    busyLabel: String = "Pairing…",
) {
    Column(verticalArrangement = Arrangement.spacedBy(22.dp)) {
        PrimaryButton(if (state.busy) busyLabel else scanLabel, onScan, active = !state.busy)

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(RelayColors.Line))
            Text("or type the code", style = sans(12f), color = RelayColors.Dim)
            Box(Modifier.weight(1f).height(1.dp).background(RelayColors.Line))
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionLabel("Pairing code", Modifier.padding(horizontal = 2.dp))
            RelayTextField(
                state.code,
                onCode,
                "XXXX-XXXX",
                monospace = true,
                textSize = 20f,
                height = 56.dp,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, keyboardType = KeyboardType.Ascii),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                Modifier.fillMaxWidth().clickable(onClick = onToggleServer).padding(horizontal = 2.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Server", style = sans(13f), color = RelayColors.Muted)
                Text(state.server.substringAfter("://") + if (state.editingServer) "" else "  ·  change", style = mono(12.5f), color = RelayColors.Dim)
            }
            if (state.editingServer) {
                RelayTextField(
                    state.server,
                    onServer,
                    "https://",
                    monospace = true,
                    textSize = 13.5f,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
            }
        }

        state.error?.let {
            Text(
                it,
                style = RelayType.Hint,
                color = RelayColors.Red,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(RelayColors.tint(RelayColors.Red)).padding(12.dp),
            )
        }

        SecondaryButton(
            if (state.busy) busyLabel else submitLabel,
            onClick = { if (state.canSubmit) onSubmit() },
            modifier = Modifier.fillMaxWidth(),
            icon = RelayIcons.Check,
            contentColor = if (state.canSubmit) RelayColors.Text else RelayColors.Dim,
        )
    }
}
