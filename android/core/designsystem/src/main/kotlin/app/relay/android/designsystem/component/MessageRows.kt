package app.relay.android.designsystem.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans
import app.relay.protocol.MessageStatus

@Immutable
data class MessageRowModel(
    val id: String,
    val to: String,
    val preview: String,
    val project: String,
    val time: String,
    val status: MessageStatus,
)

/** Compact row for the live feed on the Gateway tab. */
@Composable
fun FeedMessageRow(model: MessageRowModel, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(model.to, style = mono(13.5f, FontWeight.Medium), color = RelayColors.Text)
            Text(
                "${model.project} · ${model.time}",
                style = sans(12f),
                color = RelayColors.Dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        StatusChip(model.status)
    }
    Hairline()
}

/** Row in the message log: number + status, body preview, project and time. */
@Composable
fun LogMessageRow(model: MessageRowModel, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(model.to, style = mono(14f, FontWeight.Medium), color = RelayColors.Text)
            StatusChip(model.status)
        }
        Text(
            model.preview,
            style = sans(13.5f, lineHeight = 1.4f),
            color = RelayColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text("${model.project} · ${model.time}", style = mono(11.5f), color = RelayColors.Dim)
    }
    Hairline(RelayColors.DividerSoft)
}
