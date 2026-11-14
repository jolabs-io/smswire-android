package app.relay.android.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType

/**
 * What leaving a workspace (switching or unpairing) does to the phone's own records: history is
 * the user's call, while the [unsent] messages of [workspace] always go.
 */
@Composable
fun LeaveWorkspaceOptions(
    keepHistory: Boolean,
    onKeepHistory: (Boolean) -> Unit,
    unsent: Int,
    workspace: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RelayListCard(radius = 22.dp) {
            ListRow(
                "Keep message history",
                subtitle = if (keepHistory) "Sent messages stay on this phone to look back on" else "This phone's message list starts empty",
            ) {
                RelaySwitch(keepHistory, onKeepHistory, contentDescription = "Keep message history")
            }
        }
        if (unsent > 0) {
            Text(
                "$unsent unsent ${if (unsent == 1) "message is" else "messages are"} dropped from this phone; " +
                    "they belong to ${workspace.ifEmpty { "this workspace" }}.",
                style = RelayType.Hint,
                color = RelayColors.Amber,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
