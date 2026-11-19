package app.relay.gateway.engine

import app.relay.gateway.model.Message
import app.relay.gateway.port.BatchRepository
import app.relay.gateway.port.Clock
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.OutgoingSms
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.port.SmsEvent
import app.relay.gateway.port.SmsSendException
import app.relay.gateway.port.SmsSender
import app.relay.sms.SegmentCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The send loop: takes the oldest sendable message, waits for a throttle slot and room in
 * the platform quota, and hands it to the radio, while every [SendCondition] holds. Outcomes come back through
 * [SmsOutcomeHandler].
 *
 * With `parallelSims` on there is one such loop per SIM, each with its own throttle, so a
 * dual-SIM phone has two messages in the air at once. They share the platform quota, which
 * counts parts per app rather than per SIM. A loop takes only what its SIM can send: messages
 * for that slot, and those that name none.
 */
class MessageDispatcher(
    private val messages: MessageRepository,
    private val batches: BatchRepository,
    private val settings: SettingsRepository,
    private val sims: SimCardProvider,
    private val ledger: MessageLedger,
    private val conditions: List<SendCondition>,
    /** A fresh throttle per send loop: each SIM is paced on its own timeline. */
    private val newThrottle: () -> SendThrottle,
    private val quota: SmsQuotaGuard,
    private val segments: SegmentCalculator,
    private val simSelector: SimSelector,
    private val sender: SmsSender,
    private val statusSync: StatusSync,
    private val outcomes: SmsOutcomeHandler,
    private val clock: Clock,
    private val idlePollMs: Long = 5_000,
) {
    /**
     * One per running loop. They each need their own: a message for a particular SIM can only be
     * taken by that SIM's loop, so waking just one of them would leave the work sitting until the
     * idle poll came round.
     */
    private val wakers = CopyOnWriteArrayList<Channel<Unit>>()

    /** Taken by a loop but not yet moved to sending, so the other loops leave it alone. */
    private val taking = Mutex()
    private val taken = mutableSetOf<String>()

    /** Nudges the idle loops; call when new work arrives or a batch resumes. */
    fun wake() {
        for (waker in wakers) waker.trySend(Unit)
    }

    /** Runs until cancelled. */
    suspend fun run() = coroutineScope {
        val ready = combine(conditions.map { it.isMet(settings.settings) }) { met -> met.all { it } }
            .stateIn(this, SharingStarted.Eagerly, false)

        // One loop for the phone, or one per SIM once it is set to use them at once. A SIM added
        // or removed is picked up the next time the settings change or the app restarts.
        settings.settings
            .map { it.parallelSims }
            .distinctUntilChanged()
            .collectLatest { parallel ->
                val slots = if (parallel) sims.available().map { it.slot }.ifEmpty { listOf(null) } else listOf(null)
                coroutineScope { for (slot in slots) launch { sendLoop(slot, ready) } }
            }
    }

    /** Sends for [slot], or for every SIM when it is null. */
    private suspend fun sendLoop(slot: Int?, ready: StateFlow<Boolean>) {
        val throttle = newThrottle()
        val waker = Channel<Unit>(Channel.CONFLATED)
        wakers += waker
        try {
            loop(slot, ready, throttle, waker)
        } finally {
            wakers -= waker
        }
    }

    private suspend fun loop(slot: Int?, ready: StateFlow<Boolean>, throttle: SendThrottle, waker: Channel<Unit>) {
        while (true) {
            ready.first { it }
            val config = settings.current()
            val next = take(slot)
            if (next == null) {
                withTimeoutOrNull(idlePollMs) { waker.receive() }
                continue
            }
            var started = false
            try {
                val batchRate = next.batchId?.let { batches.find(it)?.rate }
                throttle.awaitSlot(minOf(config.maxRatePerMinute, batchRate ?: Int.MAX_VALUE))
                quota.awaitRoom(segments.calculate(next.body).segments)
                if (!ready.value) continue

                // A loop bound to a SIM sends from it; the message's own choice still wins.
                val sim = simSelector.select(next.sim ?: slot, config.defaultSim)
                val sending = ledger.transition(next.id, Transition.StartSending(sim), clock.now()) ?: continue
                started = true
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
            } finally {
                // Once it is sending it is no longer sendable; until then, let it be taken again.
                release(next.id)
                if (!started) wake()
            }
        }
    }

    /** The next message for [slot] that no other loop is holding. */
    private suspend fun take(slot: Int?): Message? = taking.withLock {
        val next = messages.nextSendable(clock.now(), batches.pausedIds(), slot, taken) ?: return@withLock null
        taken += next.id
        next
    }

    private suspend fun release(id: String) = taking.withLock { taken -= id }
}
