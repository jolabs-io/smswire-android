package app.relay.android.feature.messages

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.relay.android.designsystem.component.Hairline
import app.relay.android.designsystem.component.LogMessageRow
import app.relay.android.designsystem.component.PillChip
import app.relay.android.designsystem.component.TextLink
import app.relay.android.designsystem.icon.RelayIcons
import app.relay.android.designsystem.theme.RelayColors
import app.relay.android.designsystem.theme.RelayType
import app.relay.android.designsystem.theme.mono
import app.relay.android.designsystem.theme.sans

@Composable
fun MessagesRoute(viewModel: MessagesViewModel, onOpenMessage: (String) -> Unit, onOpenBatches: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    MessagesScreen(state, viewModel::onQueryChange, viewModel::onFilter, onOpenMessage, onOpenBatches)
}

@Composable
fun MessagesScreen(
    state: MessagesUiState,
    onQueryChange: (String) -> Unit,
    onFilter: (MessageFilter) -> Unit,
    onOpenMessage: (String) -> Unit,
    onOpenBatches: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Messages", style = RelayType.ScreenTitle, color = RelayColors.Text)
                TextLink("Batches", onOpenBatches)
            }
            // While searching, how many match; the filter chips carry their own counts.
            SearchField(state.query, onQueryChange, matches = state.shownLabel.takeIf { state.query.isNotEmpty() })
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.filters.forEach { chip ->
                PillChip(chip.filter.label, chip.selected, { onFilter(chip.filter) }, count = chip.count.toString())
            }
        }
        Column(Modifier.padding(top = 12.dp)) { Hairline() }
        LazyColumn(Modifier.weight(1f)) {
            items(state.rows, key = { it.id }) { row -> LogMessageRow(row) { onOpenMessage(row.id) } }
            if (state.rows.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 56.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Nothing here", style = sans(15f, FontWeight.Medium), color = RelayColors.Text)
                        Text(
                            "No messages match this filter.",
                            style = sans(13f),
                            color = RelayColors.Dim,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQueryChange: (String) -> Unit, matches: String?) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(shape)
            .background(RelayColors.Surface)
            .border(1.dp, RelayColors.Line, shape)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(RelayIcons.Search, contentDescription = null, tint = RelayColors.Dim, modifier = Modifier.size(18.dp))
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = sans(14.5f).copy(color = RelayColors.Text),
            cursorBrush = SolidColor(RelayColors.Lime),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (query.isEmpty()) Text("Search number, text or ID", style = sans(14.5f), color = RelayColors.Placeholder)
                inner()
            },
        )
        matches?.let { Text(it, style = mono(12f), color = RelayColors.Dim) }
    }
}
