package app.relay.android.feature.settings

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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.Hairline
import app.relay.android.designsystem.component.MonoValue
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.sans

/**
 * The device checks that need fixing, as a card on the Gateway screen. Each row says what goes
 * wrong until it's fixed and runs the same fix as its row in Settings.
 */
@Composable
fun DeviceIssuesCard(issues: List<DeviceCheckUi>, onFix: (CheckAction) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RelayColors.tint(RelayColors.Amber, 0.08f))
            .border(1.dp, RelayColors.tint(RelayColors.Amber, 0.32f), shape),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(RelayIcons.Alert, contentDescription = null, tint = RelayColors.Amber, modifier = Modifier.size(18.dp))
            Text(
                if (issues.size == 1) "1 issue needs fixing" else "${issues.size} issues need fixing",
                style = sans(15f, FontWeight.SemiBold),
                color = RelayColors.Text,
                modifier = Modifier.semantics { heading() },
            )
        }
        issues.forEach { issue ->
            Hairline(RelayColors.tint(RelayColors.Amber, 0.16f))
            IssueRow(issue) { issue.action?.let(onFix) }
        }
    }
}

@Composable
private fun IssueRow(issue: DeviceCheckUi, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Fix", onClick = onClick)
            .padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(issue.label, style = sans(14f, FontWeight.Medium), color = RelayColors.Text)
            issue.action?.let(::consequence)?.let { Text(it, style = RelayType.Hint, color = RelayColors.Muted) }
        }
        MonoValue(issue.value, RelayColors.Amber)
        Icon(RelayIcons.ChevronRight, contentDescription = null, tint = RelayColors.Amber, modifier = Modifier.size(16.dp))
    }
}

/** What goes wrong until the check is fixed. */
private fun consequence(action: CheckAction): String? = when (action) {
    CheckAction.BATTERY -> "Android may stop sending while the screen is off."
    CheckAction.SMS_PERMISSION -> "This phone can't send SMS until it's allowed."
    CheckAction.RECEIVE_SMS_PERMISSION -> "Replies and STOP won't reach the web inbox."
    CheckAction.NOTIFICATIONS -> "The gateway's status notification is hidden."
    else -> null
}
