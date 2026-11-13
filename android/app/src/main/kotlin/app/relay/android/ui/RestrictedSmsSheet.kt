package app.relay.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.PrimaryButton
import app.relay.android.designsystem.component.RelaySheet
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType

/** Whether the "Android blocked SMS" help shows. The activity owns it: permission results arrive there. */
class RestrictedSmsState(open: Boolean = false) {
    var open by mutableStateOf(open)
        private set

    fun show() {
        open = true
    }

    fun dismiss() {
        open = false
    }
}

/** How to lift Android's block on SMS for an app installed from a browser; [onOpenAppInfo] opens the page where that's done. */
@Composable
fun RestrictedSmsSheet(state: RestrictedSmsState, onOpenAppInfo: () -> Unit) {
    val app = LocalAppName.current
    BackHandler(enabled = state.open, onBack = state::dismiss)
    RelaySheet(visible = state.open, onDismiss = state::dismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Android blocked SMS for $app", style = RelayType.SheetTitle, color = RelayColors.Text)
            Text(
                "Apps installed from a browser can't use SMS until you allow it. It's a one-time step:",
                style = RelayType.Body,
                color = RelayColors.Muted,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Step(1, "Tap Open app info below.")
            Step(2, "Tap ⋮ at the top right, then Allow restricted settings, and confirm.")
            Step(3, "Open Permissions → SMS and choose Allow.")
        }
        Text("Then come back: the gateway starts on its own.", style = RelayType.Body, color = RelayColors.Muted)
        PrimaryButton("Open app info", onClick = onOpenAppInfo, height = 50.dp)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("$n.", style = RelayType.Body, color = RelayColors.Lime, modifier = Modifier.width(16.dp))
        Text(text, style = RelayType.Body, color = RelayColors.Text)
    }
}
