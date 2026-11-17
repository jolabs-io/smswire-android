package app.relay.android.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.component.ToastController
import app.relay.android.designsystem.format.Format
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.ReceiveRules
import app.relay.gateway.port.RelayApiException
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.service.ProjectOverview
import app.relay.gateway.service.ProjectService
import app.relay.protocol.MessageType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProjectDetailUi(
    val name: String,
    val enabled: Boolean,
    val today: String,
    val deliveryRate: String,
    val endpoint: String,
    val lastUsed: String,
    val sendsFrom: String,
    val type: String,
    val webhook: String,
    /** Which received SMS it takes, in a few words. */
    val receives: String,
    /** The rules themselves, for editing; null until the server sent them. */
    val rules: ReceiveRules?,
    /** This phone's Incoming SMS is All: a project's every SMS really goes. */
    val phoneAllowsAll: Boolean,
)

/** The receive rules being edited here, and whether they're on their way to the server. */
data class ReceiveEditorUi(val draft: ReceiveDraft, val saving: Boolean = false)

/**
 * One project as this phone sees it: where it sends from and how it's doing today, managed on the
 * web. What it receives can be changed here too; the server passes it on to the web and the
 * project's other phones.
 */
class ProjectDetailViewModel(
    private val projectId: String,
    private val projects: ProjectService,
    settings: SettingsRepository,
    private val sims: SimCardProvider,
    private val toast: ToastController,
    private val now: () -> Long,
) : ViewModel() {
    val state: StateFlow<ProjectDetailUi?> = combine(projects.observe(projectId), settings.settings) { o, s -> o?.let { render(it, s.inboxSync) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _editor = MutableStateFlow<ReceiveEditorUi?>(null)
    /** Null when not editing. */
    val editor: StateFlow<ReceiveEditorUi?> = _editor.asStateFlow()

    fun editReceive() {
        _editor.value = ReceiveEditorUi(ReceiveDraft.of(state.value?.rules))
    }

    fun cancelReceive() {
        _editor.value = null
    }

    fun changeReceive(transform: (ReceiveDraft) -> ReceiveDraft) = _editor.update { it?.copy(draft = transform(it.draft)) }

    fun saveReceive() {
        val editing = _editor.value ?: return
        if (editing.saving || editing.draft.blocked) return
        _editor.value = editing.copy(saving = true)
        viewModelScope.launch {
            runCatching { projects.updateReceive(projectId, editing.draft.toRules()) }
                .onSuccess {
                    _editor.value = null
                    toast.show("Saved. The web and this project's other phones have it too.")
                }
                .onFailure { e ->
                    _editor.update { it?.copy(saving = false) }
                    toast.show(if (e is RelayApiException) "Not saved: ${e.message}" else "Not saved: the server can't be reached. Try again when online.")
                }
        }
    }

    private fun render(o: ProjectOverview, inbox: InboxSync): ProjectDetailUi {
        val p = o.project
        return ProjectDetailUi(
            name = p.name,
            enabled = p.enabled,
            today = Format.count(o.activity.today),
            deliveryRate = Format.percent(o.activity.deliveryRate),
            endpoint = p.endpoint,
            lastUsed = Format.lastUsed(o.activity.lastReceivedAt, now()),
            sendsFrom = p.sim?.let { slot ->
                val carrier = sims.available().firstOrNull { it.slot == slot }?.carrier
                if (carrier != null) "SIM $slot · $carrier" else "SIM $slot · not in this phone"
            } ?: "Any SIM (this phone's default)",
            type = TYPE_LABEL.getValue(p.defaultType),
            webhook = p.webhookUrl?.takeIf { it.isNotBlank() } ?: "None",
            receives = when {
                inbox == InboxSync.OFF -> "Nothing: off on this phone"
                else -> p.receive?.let { Format.receives(it.replies, it.all, it.senders, inbox == InboxSync.ALL) } ?: "Updating…"
            },
            rules = p.receive,
            phoneAllowsAll = inbox == InboxSync.ALL,
        )
    }

    private companion object {
        val TYPE_LABEL = mapOf(
            MessageType.OTP to "OTP · sent first",
            MessageType.TRANSACTIONAL to "Transactional",
            MessageType.NOTIFICATION to "Notification",
            MessageType.MARKETING to "Marketing · sent last",
        )
    }
}
