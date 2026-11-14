package app.relay.android.feature.updates

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.sans
import app.relay.gateway.service.UpdatePhase
import app.relay.gateway.service.UpdateStatus

/** Whether the Gateway shows the banner (and the Settings tab its dot): a snoozed offer stays quiet. */
fun UpdateStatus?.showsBanner(): Boolean = this != null && !(snoozed && phase == UpdatePhase.AVAILABLE)

/** On the Gateway, above the status card: the update and where it's at. Tapping opens the sheet. */
@Composable
fun UpdateBanner(status: UpdateStatus, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val app = LocalAppName.current
    val v = status.update.versionName
    val tone: Color = when {
        status.phase == UpdatePhase.FAILED || status.update.critical -> RelayColors.Red
        else -> RelayColors.Amber
    }
    val (title, subtitle) = when (status.phase) {
        UpdatePhase.AVAILABLE ->
            (if (status.update.critical) "Important update: $app $v" else "$app $v is available") to "See what's new and install"
        UpdatePhase.DOWNLOADING -> "Downloading $v…" to "${(status.progress * 100).toInt()}%"
        UpdatePhase.READY -> "$app $v is ready" to "Tap to install"
        UpdatePhase.INSTALLING -> "Installing $v…" to "The gateway resumes once it's done"
        UpdatePhase.FAILED -> "Couldn't update to $v" to (status.error ?: "Tap to try again")
    }
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RelayColors.tint(tone, 0.08f))
            .border(1.dp, RelayColors.tint(tone, 0.32f), shape)
            .clickable(onClick = onOpen)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(RelayIcons.Upload, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = sans(15f, FontWeight.SemiBold), color = RelayColors.Text)
            Text(subtitle, style = RelayType.Hint, color = RelayColors.Muted)
        }
        Icon(RelayIcons.ChevronRight, contentDescription = null, tint = RelayColors.Dim, modifier = Modifier.size(16.dp))
    }
}
