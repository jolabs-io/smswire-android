package app.relay.android.feature.updates

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.sans
import app.relay.gateway.service.ReleaseNotesBlock
import app.relay.gateway.service.parseReleaseNotes

/** A release's changelog section: its headings, items and paragraphs. */
@Composable
fun ReleaseNotesText(notes: String, modifier: Modifier = Modifier) {
    val blocks = remember(notes) { parseReleaseNotes(notes) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
                is ReleaseNotesBlock.Heading -> Text(block.text, style = sans(13f, FontWeight.SemiBold), color = RelayColors.Text)
                is ReleaseNotesBlock.Items -> Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    block.items.forEach { item ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("•", style = RelayType.Body, color = RelayColors.Dim)
                            Text(item, style = RelayType.Body, color = RelayColors.Muted)
                        }
                    }
                }
                is ReleaseNotesBlock.Text -> Text(block.text, style = RelayType.Body, color = RelayColors.Muted)
            }
        }
    }
}
