package app.relay.android.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.LocalAppName
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.sans

/** What the send-only build can't do, said the same way on the Pair screen and in Settings. */
fun sendOnlyHint(appName: String): String =
    "It sends SMS and gets delivery reports, but can't receive SMS, so replies and STOP opt-outs don't reach $appName. " +
        "Need them? Install the two-way app from the download page over this one; the phone stays paired."

/** An amber note: this is the send-only build, and what that means. */
@Composable
fun SendOnlyNote(modifier: Modifier = Modifier) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(RelayColors.tint(RelayColors.Amber, 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Send-only build", style = RelayType.RowTitle, color = RelayColors.Amber)
        Text(sendOnlyHint(LocalAppName.current), style = RelayType.Hint, color = RelayColors.Muted)
    }
}

/** "Send-only", small and amber, beside the phone's name; [onClick] opens where it's explained. */
@Composable
fun SendOnlyChip(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(CircleShape)
            .background(RelayColors.tint(RelayColors.Amber))
            .clickable(onClickLabel = "What send-only means", onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.size(6.dp).background(RelayColors.Amber, CircleShape))
        Text("Send-only", style = sans(11.5f, FontWeight.Medium), color = RelayColors.Amber)
    }
}
