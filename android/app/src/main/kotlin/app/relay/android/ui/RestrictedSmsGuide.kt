package app.relay.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.sans

// Drawn look-alikes of the Android screens the restricted-SMS steps go through, with the thing to
// tap highlighted. Decoration only: the step text says the same, so screen readers skip them.

/** Settings → Apps → the app: the title bar, with ⋮ at the top right. */
@Composable
fun AppInfoMock(app: String) = MockScreen {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("‹", style = sans(18f), color = RelayColors.Muted)
        Spacer(Modifier.size(10.dp))
        Column(Modifier.weight(1f)) {
            Text("App info", style = sans(13.5f, FontWeight.SemiBold), color = RelayColors.Text)
            Text(app, style = sans(11.5f), color = RelayColors.Dim)
        }
        Highlight(CircleShape) { Text("⋮", style = sans(16f, FontWeight.Bold), color = RelayColors.Text) }
    }
}

/** The ⋮ menu: the one item to choose. */
@Composable
fun RestrictedMenuMock() = MockScreen {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
        Highlight(RoundedCornerShape(8.dp)) {
            Text("Allow restricted settings", style = sans(13f, FontWeight.Medium), color = RelayColors.Text)
        }
    }
}

/** Permissions → SMS: Allow chosen. */
@Composable
fun SmsPermissionMock() = MockScreen {
    Text("SMS permission", style = sans(13.5f, FontWeight.SemiBold), color = RelayColors.Text)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Choice("Allow", chosen = true)
        Choice("Don't allow", chosen = false)
    }
}

@Composable
private fun MockScreen(content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics {}
            .clip(shape)
            .background(RelayColors.SurfaceRaised)
            .border(1.dp, RelayColors.Line, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** What to tap: ringed in the app's accent. */
@Composable
private fun Highlight(shape: androidx.compose.ui.graphics.Shape, content: @Composable () -> Unit) {
    Box(
        Modifier
            .clip(shape)
            .background(RelayColors.tint(RelayColors.Lime, 0.14f))
            .border(1.5.dp, RelayColors.Lime, shape)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun Choice(label: String, chosen: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier.size(16.dp).clip(CircleShape).border(1.5.dp, if (chosen) RelayColors.Lime else RelayColors.Dim, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (chosen) Box(Modifier.size(8.dp).clip(CircleShape).background(RelayColors.Lime))
        }
        Text(label, style = sans(13f, if (chosen) FontWeight.Medium else FontWeight.Normal), color = if (chosen) RelayColors.Text else RelayColors.Muted)
    }
}
