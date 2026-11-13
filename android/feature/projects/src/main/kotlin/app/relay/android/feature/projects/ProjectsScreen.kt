package app.relay.android.feature.projects

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.relay.android.designsystem.component.ScreenHeader
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

@Composable
fun ProjectsScreen(rows: List<ProjectRowUi>, onOpen: (String) -> Unit) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { ScreenHeader("Projects") }
        item {
            Text(
                "Projects are created on the web dashboard, with their API keys. This phone sends for the ones assigned to it.",
                style = RelayType.Body,
                color = RelayColors.Muted,
                modifier = Modifier.padding(start = 2.dp, end = 2.dp, bottom = 4.dp),
            )
        }
        items(rows, key = { it.id }) { ProjectRow(it) { onOpen(it.id) } }
        if (rows.isEmpty()) {
            item {
                Text(
                    "No projects use this phone yet. Assign it to one on the web.",
                    style = sans(13.5f),
                    color = RelayColors.Dim,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
                )
            }
        }
    }
}

@Composable
private fun ProjectRow(row: ProjectRowUi, onClick: () -> Unit) {
    val statusColor = if (row.enabled) RelayColors.Lime else RelayColors.Dim
    val shape = RoundedCornerShape(22.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RelayColors.Surface)
            .border(1.dp, RelayColors.Line, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(RelayColors.SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            Text(row.initials, style = mono(14f, FontWeight.SemiBold), color = statusColor)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(row.name, style = sans(15.5f, FontWeight.SemiBold), color = RelayColors.Text)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.size(6.dp).background(statusColor, CircleShape))
                Text("${if (row.enabled) "Active" else "Disabled"} · ${row.slug}", style = mono(12f), color = RelayColors.Dim)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(row.today, style = mono(15f, FontWeight.Medium), color = RelayColors.Text)
            Text("today", style = sans(11f), color = RelayColors.Dim)
        }
    }
}
