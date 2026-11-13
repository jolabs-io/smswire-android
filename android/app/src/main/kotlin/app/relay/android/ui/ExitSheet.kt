package app.relay.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.RelaySheet
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType

/** Open/close state of the "Exit?" confirmation, shared by the gateway's power button and Settings → Exit. */
class ExitConfirmState internal constructor(open: Boolean) {
    var open by mutableStateOf(open)
        private set

    fun ask() {
        open = true
    }

    fun dismiss() {
        open = false
    }
}

@Composable
fun rememberExitConfirm(): ExitConfirmState =
    rememberSaveable(saver = Saver(save = { it.open }, restore = ::ExitConfirmState)) { ExitConfirmState(false) }

/** Confirms before [onExit] stops the gateway and closes the app. */
@Composable
fun ExitSheet(state: ExitConfirmState, onExit: () -> Unit) {
    val app = LocalAppName.current
    BackHandler(enabled = state.open, onBack = state::dismiss)
    RelaySheet(visible = state.open, onDismiss = state::dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Exit $app?", style = RelayType.SheetTitle, color = RelayColors.Text)
            Text(
                "The gateway stops and the app closes, so nothing runs in the background. Messages wait on the server, " +
                    "or go to a backup phone, until you open $app again.",
                style = RelayType.Body,
                color = RelayColors.Muted,
            )
        }
        SecondaryButton(
            "Stop and exit",
            onClick = onExit,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentColor = RelayColors.Red,
            background = RelayColors.Background,
            borderColor = RelayColors.Red.copy(alpha = 0.35f),
        )
    }
}
