package app.relay.android.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.CodeBlock
import app.relay.android.designsystem.component.PrimaryButton
import app.relay.android.designsystem.component.RelaySheet
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType

/** Android's send limit and the two ways to lift it without the user touching adb in production. */
@Composable
internal fun SmsLimitSheet(
    visible: Boolean,
    limit: SmsLimitUi,
    onDismiss: () -> Unit,
    onDefaultApp: () -> Unit,
    onCopyCommand: () -> Unit,
) {
    RelaySheet(visible = visible, onDismiss = onDismiss) {
        LimitSummary(limit.partsPerMinute)
        if (limit.defaultAppLiftsLimit) DefaultAppOption(limit.isDefaultApp, onDefaultApp)
        if (limit.partsPerMinute != null) AdbOption(limit.adbCommand, onCopyCommand)
    }
}

@Composable
private fun LimitSummary(partsPerMinute: Int?) {
    val app = LocalAppName.current
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Android SMS limit", style = RelayType.SheetTitle, color = RelayColors.Text)
        Text(
            if (partsPerMinute == null) {
                "No limit applies. $app sends at your chosen rate."
            } else {
                "Android stops to ask for confirmation after $partsPerMinute SMS parts a minute, which would stall the gateway. $app holds to that pace; lift the limit to send faster."
            },
            style = RelayType.Body,
            color = RelayColors.Muted,
        )
    }
}

@Composable
private fun DefaultAppOption(isDefault: Boolean, onClick: () -> Unit) {
    val app = LocalAppName.current
    LimitOption(
        title = "Default SMS app",
        body = if (isDefault) {
            "$app is the default SMS app, so no limit applies. Incoming SMS show as notifications and stay in the system inbox."
        } else {
            "Removes the limit. Incoming SMS then arrive as $app notifications instead of in your messaging app, so it suits a dedicated gateway phone."
        },
    ) {
        if (isDefault) {
            SecondaryButton("Change default SMS app", onClick, Modifier.fillMaxWidth())
        } else {
            PrimaryButton("Set $app as default SMS app", onClick, height = 48.dp)
        }
    }
}

@Composable
private fun AdbOption(command: String, onCopy: () -> Unit) {
    LimitOption(
        title = "Raise it from a computer",
        body = "Once per phone, over USB with USB debugging on. Runs the commands below, then restarts the phone.",
    ) {
        CodeBlock(command)
        SecondaryButton("Copy commands", onCopy, Modifier.fillMaxWidth(), icon = RelayIcons.Copy)
    }
}

@Composable
private fun LimitOption(title: String, body: String, actions: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = RelayType.RowTitle, color = RelayColors.Text)
            Text(body, style = RelayType.Hint, color = RelayColors.Dim)
        }
        actions()
    }
}
