package app.relay.android.feature.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.format.Format
import app.relay.gateway.model.InboxSync
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.service.ProjectOverview
import app.relay.gateway.service.ProjectService
import app.relay.protocol.MessageType
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

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
    /** Which received SMS it takes, as set on the web. */
    val receives: String,
)

/** One project as this phone sees it: where it sends from and how it's doing today. Edited on the web. */
class ProjectDetailViewModel(
    projectId: String,
    projects: ProjectService,
    settings: SettingsRepository,
    private val sims: SimCardProvider,
    private val now: () -> Long,
) : ViewModel() {
    val state: StateFlow<ProjectDetailUi?> = combine(projects.observe(projectId), settings.settings) { o, s -> o?.let { render(it, s.inboxSync) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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
