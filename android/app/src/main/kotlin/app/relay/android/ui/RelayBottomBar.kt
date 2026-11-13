package app.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.Hairline
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.sans

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    GATEWAY("gateway", "Gateway", RelayIcons.TabGateway),
    MESSAGES("messages", "Messages", RelayIcons.TabMessages),
    BULK("bulk", "Bulk", RelayIcons.TabBulk),
    PROJECTS("projects", "Projects", RelayIcons.TabProjects),
}

@Composable
fun RelayBottomBar(current: String?, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(RelayColors.Background)) {
        Hairline(RelayColors.DividerSoft)
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 4.dp)) {
            Tab.entries.forEach { tab ->
                val selected = tab.route == current
                Column(
                    Modifier.weight(1f).clickable { onSelect(tab) }.padding(vertical = 2.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier
                            .size(width = 58.dp, height = 30.dp)
                            .clip(RoundedCornerShape(15.dp))
                            .background(if (selected) RelayColors.Lime.copy(alpha = 0.14f) else Color.Transparent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            tab.icon,
                            contentDescription = null,
                            tint = if (selected) RelayColors.Lime else RelayColors.Muted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Text(tab.label, style = sans(11.5f, FontWeight.Medium), color = if (selected) RelayColors.Text else RelayColors.Muted)
                }
            }
        }
    }
}
