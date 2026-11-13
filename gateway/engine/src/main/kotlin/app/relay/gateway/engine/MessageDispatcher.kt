package app.relay.gateway.engine

import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.Clock
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.OutgoingSms
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SmsEvent
import app.relay.gateway.port.SmsSendException
import app.relay.gateway.port.SmsSender
import app.relay.sms.SegmentCalculator
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withTimeoutOrNull

/**
 * The send loop: takes the oldest sendable message, waits for a throttle slot and room in
 * the platform quota, and hands it to the radio, while every [SendCondition] holds. Outcomes come back through
 * [SmsOutcomeHandler].
 */
class MessageDispatcher(
    private val messages: MessageRepository,
    private val batches: BatchRepository,
    private val settings: SettingsRepository,
    private val ledger: MessageLedger,
    private val conditions: List<SendCondition>,
    private val throttle: SendThrottle,
    private val quota: SmsQuotaGuard,
    private val segments: SegmentCalculator,
    private val simSelector: SimSelector,
    private val sender: SmsSender,
    private val statusSync: StatusSync,
    private val outcomes: SmsOutcomeHandler,
    private val clock: Clock,
    private val idlePollMs: Long = 5_000,
) {
    private val wakeSignal = Channel<Unit>(Channel.CONFLATED)

    /** Nudges an idle loop; call when new work arrives or a batch resumes. */
    fun wake() {
        wakeSignal.trySend(Unit)
    }

    /** Runs until cancelled. */
    suspend fun run() = coroutineScope {
        val ready = combine(conditions.map { it.isMet(settings.settings) }) { met -> met.all { it } }
            .stateIn(this, SharingStarted.Eagerly, false)

        while (true) {
            ready.first { it }
            val config = settings.current()
            val next = messages.nextSendable(clock.now(), batches.pausedIds())
            if (next == null) {
                withTimeoutOrNull(idlePollMs) { wakeSignal.receive() }
                continue
            }

            val batchRate = next.batchId?.let { batches.find(it)?.rate }
            throttle.awaitSlot(minOf(config.maxRatePerMinute, batchRate ?: Int.MAX_VALUE))
            quota.awaitRoom(segments.calculate(next.body).segments)
            if (!ready.value) continue

            val sim = simSelector.select(next.sim, config.defaultSim)
            val sending = ledger.transition(next.id, Transition.StartSending(sim), clock.now()) ?: continue
            statusSync.report(sending)

            try {
                sender.send(
                    OutgoingSms(
                        messageId = sending.id,
                        to = sending.to,
                        body = sending.body,
                        simSlot = sim,
                        requestDeliveryReport = config.requestDeliveryReports,
                    ),
                )
            } catch (e: SmsSendException) {
                outcomes.handle(SmsEvent.Failed(sending.id, clock.now(), e.reason, retryable = true))
            }
        }
    }
}
