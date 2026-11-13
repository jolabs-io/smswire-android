package app.relay.gateway.service

import app.relay.gateway.model.Message
import app.relay.protocol.MessageStatus

enum class StepState { DONE, FAILED, CURRENT, TODO }

data class TimelineStep(val label: String, val detail: String, val at: Long?, val state: StepState)

/** The delivery report shown for a message: what happened, when, and what's still to come. */
object DeliveryTimeline {
    fun build(message: Message, projectName: String?, rateLimit: Int): List<TimelineStep> {
        val t = message.timeline
        val error = message.error.orEmpty()
        val route = if (message.batchId != null) "POST /messages/bulk" else "POST /messages"
        return buildList {
            add(TimelineStep("Received via API", "${projectName ?: "Project"} · $route", t.receivedAt, StepState.DONE))
            add(TimelineStep("Queued on device", "Rate limit $rateLimit/min", t.queuedAt, StepState.DONE))

            add(
                when {
                    t.sentAt != null -> TimelineStep("Sent to carrier", "Accepted by SMSC", t.sentAt, StepState.DONE)
                    message.status == MessageStatus.FAILED ->
                        TimelineStep("Send failed", error, t.failedAt, StepState.FAILED)
                    message.status == MessageStatus.SENDING ->
                        TimelineStep("Sending…", "Handing to SIM ${message.sim ?: 1}", null, StepState.CURRENT)
                    else -> TimelineStep("Sent to carrier", "Waiting in queue", null, StepState.TODO)
                },
            )

            when {
                message.status == MessageStatus.DELIVERED ->
                    add(TimelineStep("Delivered", "Delivery report received", t.deliveredAt, StepState.DONE))
                message.status == MessageStatus.FAILED && t.sentAt != null ->
                    add(TimelineStep("Undelivered", error, t.failedAt, StepState.FAILED))
                message.status == MessageStatus.SENT ->
                    add(TimelineStep("Awaiting delivery report", "Carrier has not confirmed yet", null, StepState.CURRENT))
                message.status != MessageStatus.FAILED ->
                    add(TimelineStep("Delivered", "Delivery report", null, StepState.TODO))
            }
        }
    }
}
