package app.relay.android.feature.messages

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.component.ToastController
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.Message
import app.relay.gateway.model.Project
import app.relay.gateway.service.DeliveryTimeline
import app.relay.gateway.service.MessageService
import app.relay.gateway.service.ProjectService
import app.relay.gateway.service.TimelineStep
import app.relay.protocol.MessageStatus
import app.relay.sms.SegmentCalculator
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class HookTone { OK, ERROR, PENDING, NONE }

data class MessageDetailUi(
    val id: String,
    val to: String,
    val body: String,
    val status: MessageStatus,
    val project: String,
    val segments: String,
    val sim: String,
    val steps: List<TimelineStep>,
    val hookTarget: String,
    val hookResult: String,
    val hookTone: HookTone,
    val canRetry: Boolean,
)

/** Delivery report sheet for one message, opened from the Gateway feed or the Messages log. */
@OptIn(ExperimentalCoroutinesApi::class)
class MessageDetailViewModel(
    private val messages: MessageService,
    projects: ProjectService,
    settings: Flow<GatewaySettings>,
    private val segments: SegmentCalculator,
    private val toast: ToastController,
) : ViewModel() {
    private val selected = MutableStateFlow<String?>(null)

    val state: StateFlow<MessageDetailUi?> = selected.flatMapLatest { id ->
        if (id == null) {
            flowOf(null)
        } else {
            combine(messages.observe(id), projects.observeAll(), settings) { m, list, s ->
                m?.let { render(it, list.firstOrNull { p -> p.project.id == it.projectId }?.project, s.maxRatePerMinute) }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun open(id: String) {
        selected.value = id
    }

    fun close() {
        selected.value = null
    }

    fun retry() {
        val id = selected.value ?: return
        viewModelScope.launch {
            if (messages.retry(id)) toast.show("Re-queued for sending")
        }
    }

    private fun render(m: Message, project: Project?, rate: Int): MessageDetailUi {
        val info = segments.calculate(m.body)
        val hook = project?.webhookUrl?.takeIf { it.isNotBlank() }
        val (result, tone) = when {
            hook == null -> "Add one in project settings to receive status updates" to HookTone.NONE
            m.webhook == null -> "Waiting for the server to call it…" to HookTone.PENDING
            m.webhook!!.succeeded ->
                "status=${m.webhook!!.status.wireName} → ${m.webhook!!.httpStatus} OK · ${m.webhook!!.durationMs} ms" to HookTone.OK
            else -> {
                val w = m.webhook!!
                "status=${w.status.wireName} → ${w.httpStatus?.toString() ?: w.error ?: "no response"}" to HookTone.ERROR
            }
        }
        return MessageDetailUi(
            id = m.id,
            to = m.to,
            body = m.body,
            status = m.status,
            project = project?.name ?: "Deleted project",
            segments = "${info.segments} · ${info.encoding.label}",
            sim = m.sim?.let { "SIM $it" } ?: "—",
            steps = DeliveryTimeline.build(m, project?.name, rate),
            hookTarget = hook?.let { "POST $it" } ?: "No webhook set for this project",
            hookResult = result,
            hookTone = tone,
            canRetry = m.status == MessageStatus.FAILED,
        )
    }
}
