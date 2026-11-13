package app.relay.android.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.component.MessageRowModel
import app.relay.android.designsystem.format.Format
import app.relay.gateway.model.Message
import app.relay.gateway.port.Clock
import app.relay.gateway.service.MessageService
import app.relay.gateway.service.ProjectService
import app.relay.protocol.MessageStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn

enum class MessageFilter(val label: String, private val statuses: Set<MessageStatus>?) {
    ALL("All", null),
    QUEUED("Queued", setOf(MessageStatus.QUEUED, MessageStatus.SENDING)),
    SENT("Sent", setOf(MessageStatus.SENT)),
    DELIVERED("Delivered", setOf(MessageStatus.DELIVERED)),
    FAILED("Failed", setOf(MessageStatus.FAILED));

    fun matches(status: MessageStatus) = statuses == null || status in statuses
}

data class FilterChipUi(val filter: MessageFilter, val count: Int, val selected: Boolean)

data class MessagesUiState(
    val query: String = "",
    val filters: List<FilterChipUi> = MessageFilter.entries.map { FilterChipUi(it, 0, it == MessageFilter.ALL) },
    val rows: List<MessageRowModel> = emptyList(),
) {
    val shownLabel: String get() = "${rows.size} shown"
}

@OptIn(ExperimentalCoroutinesApi::class, kotlinx.coroutines.FlowPreview::class)
class MessagesViewModel(
    messages: MessageService,
    projects: ProjectService,
    private val clock: Clock,
    ticker: Flow<Unit>,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow(MessageFilter.ALL)

    private val searched = query.debounce(150).flatMapLatest { messages.search(it) }

    val state: StateFlow<MessagesUiState> = combine(
        query,
        filter,
        searched,
        projects.observeAll(),
        ticker,
    ) { q, f, found, projectList, _ ->
        val names = projectList.associate { it.project.id to it.project.name }
        val now = clock.now()
        MessagesUiState(
            query = q,
            filters = MessageFilter.entries.map { chip -> FilterChipUi(chip, found.count { chip.matches(it.status) }, chip == f) },
            rows = found.filter { f.matches(it.status) }.map { it.toRow(names, now) },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MessagesUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun onFilter(value: MessageFilter) {
        filter.value = value
    }
}

internal fun Message.toRow(projectNames: Map<String, String>, now: Long) = MessageRowModel(
    id = id,
    to = to,
    preview = body,
    project = projectNames[projectId] ?: "Deleted project",
    time = Format.ago(timeline.receivedAt, now),
    status = status,
)
