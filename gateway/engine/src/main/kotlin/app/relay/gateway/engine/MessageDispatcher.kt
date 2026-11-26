package app.relay.gateway.engine

import app.relay.gateway.model.Message
import app.relay.gateway.model.SimPreference
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * The send loop: takes the oldest sendable message, waits for a throttle slot and room in
 * the platform quota, and hands it to the radio, while every [SendCondition] holds. Outcomes come back through
 * [SmsOutcomeHandler].
 *
 * With `parallelSims` on there is one such loop per SIM, each with its own throttle, so a
 * dual-SIM phone has two messages in the air at once. They share the platform quota, which
 * counts parts per app rather than per SIM. A loop takes only what its SIM can send: messages
 * for that slot, and those that name none. Taking turns between SIMs, a loop also leaves a
 * message to the SIM its recipient stays on (see [DefaultSimSelector]) when that SIM has a loop
 * of its own, so one person doesn't hear from both numbers.
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

    /** Taken by a loop but not yet moved to sending, so the other loops leave it alone. By id. */
    private val taking = Mutex()
    private val taken = mutableMapOf<String, Held>()

    /** The SIMs that have a send loop of their own right now. */
    private val looping: MutableSet<Int> = ConcurrentHashMap.newKeySet()

    /** Which loop holds a message, and who it is for (null for a name or a short code). */
    private class Held(val slot: Int?, val person: String?)

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
        if (slot != null) looping += slot
        try {
            loop(slot, ready, throttle, waker)
        } finally {
            wakers -= waker
            if (slot != null) looping -= slot
        }
    }

    private suspend fun loop(slot: Int?, ready: StateFlow<Boolean>, throttle: SendThrottle, waker: Channel<Unit>) {
        while (true) {
            ready.first { it }
            val config = settings.current()
            val next = take(slot, config.defaultSim)
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
                val sim = simSelector.select(next.sim ?: slot, config.defaultSim, next.to)
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

    /**
     * The next message for [slot] that no other loop is holding. A SIM's loop passes over a few that
     * belong to another loop; if the queue starts with more than that, it waits for them to go.
     */
    private suspend fun take(slot: Int?, preference: SimPreference): Message? = taking.withLock {
        val now = clock.now()
        val paused = batches.pausedIds()
        // Kept small: Room binds every excluded id as its own variable.
        val passed = mutableSetOf<String>()
        var next = messages.nextSendable(now, paused, slot, taken.keys)
        while (next != null && slot != null && preference == SimPreference.ALTERNATE && next.sim == null &&
            belongsElsewhere(next, slot, preference)
        ) {
            passed += next.id
            if (passed.size >= MAX_PASSED) return@withLock null
            next = messages.nextSendable(now, paused, slot, taken.keys + passed)
        }
        next?.also { taken[it.id] = Held(slot, ReceiveFilter.personDigits(it.to)) }
    }

    /**
     * Whether [message] should go out from another loop's SIM: the one already holding a message to
     * the same person (it isn't sending yet, so the history can't tell), else the SIM that texted
     * them last. Only a SIM with a loop of its own, or nothing would ever send it.
     */
    private suspend fun belongsElsewhere(message: Message, slot: Int, preference: SimPreference): Boolean {
        val person = ReceiveFilter.personDigits(message.to)
        if (person != null && taken.values.any { it.person == person && it.slot != null && it.slot != slot }) return true
        val home = simSelector.stickySim(message.to, preference) ?: return false
        return home != slot && home in looping
    }

    private suspend fun release(id: String) = taking.withLock { taken -= id }

    private companion object {
        /** How many messages for other SIMs a loop looks past before it waits. */
        const val MAX_PASSED = 8
    }
}
