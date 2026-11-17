package app.relay.android.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.PrimaryButton
import app.relay.android.designsystem.component.RelaySwitch
import app.relay.android.designsystem.component.RelayTextField
import app.relay.android.designsystem.component.SecondaryButton
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono

/** Replies, every SMS and the senders a project always takes, edited here and saved to the server. */
@Composable
internal fun ReceiveEditor(
    editor: ReceiveEditorUi,
    phoneAllowsAll: Boolean,
    onChange: ((ReceiveDraft) -> ReceiveDraft) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val d = editor.draft
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(RelayColors.Surface)
            .border(1.dp, RelayColors.Line, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ToggleRow("Replies", "From people this project texted in the last 30 days.", d.replies) { on -> onChange { it.copy(replies = on) } }
        ToggleRow(
            "Every SMS",
            if (phoneAllowsAll) "Everything on its SIM, bank codes and personal texts included." else "Everything on its SIM. This phone sends it only with Incoming SMS set to All in Settings.",
            d.all,
        ) { on -> onChange { it.copy(all = on) } }
        Senders(d, onChange)
        Text("STOP and START from people it texted, and join keywords, always come through.", style = RelayType.Hint, color = RelayColors.Dim)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SecondaryButton("Cancel", onCancel, Modifier.weight(1f))
            PrimaryButton(if (editor.saving) "Saving…" else "Save", onSave, Modifier.weight(1f), active = !editor.saving && !d.blocked, height = 46.dp)
        }
    }
}

@Composable
private fun ToggleRow(title: String, hint: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = RelayType.RowTitle, color = RelayColors.Text)
            Text(hint, style = RelayType.Hint, color = RelayColors.Dim)
        }
        RelaySwitch(checked, onChange, contentDescription = title)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Senders(d: ReceiveDraft, onChange: ((ReceiveDraft) -> ReceiveDraft) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Always receive from", style = RelayType.RowTitle, color = RelayColors.Text)
        if (d.senders.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                d.senders.forEach { sender -> SenderChip(sender) { onChange { it.remove(sender) } } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            RelayTextField(
                value = d.typed,
                onValueChange = { v -> onChange { it.copy(typed = v) } },
                placeholder = "+251911…, 127, CBE",
                modifier = Modifier.weight(1f),
                monospace = true,
                textSize = 14f,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onChange { it.add() } }),
            )
            SecondaryButton("Add", { onChange { it.add() } })
        }
        val issue = d.issue
        Text(
            issue?.message ?: "Numbers, short codes (127) or sender names (CBE, telebirr).",
            style = RelayType.Hint,
            color = if (issue != null) RelayColors.Red else RelayColors.Dim,
        )
    }
}

@Composable
private fun SenderChip(sender: String, onRemove: () -> Unit) {
    Row(
        Modifier
            .clip(CircleShape)
            .background(RelayColors.SurfaceRaised)
            .border(1.dp, RelayColors.Line, CircleShape)
            .clickable(onClick = onRemove)
            .padding(start = 11.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(sender, style = mono(12.5f, FontWeight.Medium), color = RelayColors.Text)
        Icon(RelayIcons.Close, contentDescription = "Remove $sender", tint = RelayColors.Muted, modifier = Modifier.size(12.dp))
    }
}
