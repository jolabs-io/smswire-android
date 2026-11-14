package app.relay.android.feature.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.PrimaryButton
import app.relay.android.designsystem.component.RelaySheet
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.gateway.service.UpdatePhase
import app.relay.gateway.service.UpdateStatus
import java.util.Locale

/** The update: what changed, its size, Install (and Later, unless it's critical). */
@Composable
fun UpdateSheet(status: UpdateStatus?, visible: Boolean, onInstall: () -> Unit, onLater: () -> Unit, onDismiss: () -> Unit) {
    RelaySheet(visible = visible && status != null, onDismiss = onDismiss) {
        val s = status ?: return@RelaySheet
        val update = s.update
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("${LocalAppName.current} ${update.versionName}", style = RelayType.SheetTitle, color = RelayColors.Text)
            Text(
                buildString {
                    append(String.format(Locale.US, "%.1f MB", update.size / 1_000_000.0))
                    if (update.critical) append(" · Important: install it soon")
                },
                style = RelayType.Hint,
                color = if (update.critical) RelayColors.Red else RelayColors.Dim,
            )
        }
        update.notes?.takeIf { it.isNotBlank() }?.let {
            ReleaseNotesText(it, Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState()))
        }
        if (s.phase == UpdatePhase.FAILED) {
            Text(s.error ?: "The update failed.", style = RelayType.Hint, color = RelayColors.Red)
        }
        Text(
            "The gateway pauses while the app updates; the server holds new messages and sends them once it's back. This phone stays paired.",
            style = RelayType.Hint,
            color = RelayColors.Dim,
        )
        val busy = s.phase == UpdatePhase.DOWNLOADING || s.phase == UpdatePhase.INSTALLING
        PrimaryButton(
            when (s.phase) {
                UpdatePhase.DOWNLOADING -> "Downloading… ${(s.progress * 100).toInt()}%"
                UpdatePhase.INSTALLING -> "Installing…"
                UpdatePhase.FAILED -> "Try again"
                else -> "Install update"
            },
            onClick = { if (!busy) onInstall() },
            active = !busy,
        )
        if (!update.critical && !busy) {
            SecondaryButton("Later", onClick = onLater, modifier = Modifier.fillMaxWidth().height(46.dp))
        }
    }
}

/** Once, in the new version: what changed. */
@Composable
fun WhatsNewSheet(versionName: String?, notes: String?, onDismiss: () -> Unit) {
    RelaySheet(visible = versionName != null && notes != null, onDismiss = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("What's new in ${versionName.orEmpty()}", style = RelayType.SheetTitle, color = RelayColors.Text)
            Text("${LocalAppName.current} updated; the gateway is back on.", style = RelayType.Hint, color = RelayColors.Dim)
        }
        notes?.let { ReleaseNotesText(it, Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) }
        PrimaryButton("Got it", onClick = onDismiss)
    }
}
