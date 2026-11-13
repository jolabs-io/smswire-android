package app.relay.gateway.engine

import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.Clock
import app.relay.gateway.port.PowerMonitor
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.port.SmsQuotaProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

/** Spaces sends so the device never exceeds a per-minute rate. */
interface SendThrottle {
    suspend fun awaitSlot(ratePerMinute: Int)
}

/**
 * Enforces a minimum interval of 60s / rate between consecutive sends. The rate is passed
 * per call, so the batch-specific limit and the global limit share one timeline.
 */
class IntervalSendThrottle(private val clock: Clock) : SendThrottle {
    private val mutex = Mutex()
    private var lastSlot: Long? = null

    override suspend fun awaitSlot(ratePerMinute: Int) = mutex.withLock {
        val interval = 60_000L / ratePerMinute.coerceAtLeast(1)
        val last = lastSlot
        if (last != null) {
            val wait = last + interval - clock.now()
            if (wait > 0) delay(wait)
        }
        lastSlot = clock.now()
    }
}

/**
 * Keeps sends under the platform's own quota, so the OS never stops to ask the user for
 * confirmation (which stalls an unattended gateway). Counts SMS parts, not messages.
 */
class SmsQuotaGuard(private val quota: SmsQuotaProvider, private val clock: Clock) {
    private val mutex = Mutex()
    private val stamps = ArrayDeque<Long>()

    suspend fun awaitRoom(parts: Int) = mutex.withLock {
        while (true) {
            val q = quota.current() ?: return@withLock
            val now = clock.now()
            // The platform stamps a send slightly after we do; the margin keeps our window from closing first.
            val window = q.windowMs + MARGIN_MS
            while (stamps.isNotEmpty() && stamps.first() <= now - window) stamps.removeFirst()
            // A message larger than the whole quota can only go out into an empty window.
            if (stamps.isEmpty() || stamps.size + parts <= q.maxParts) {
                repeat(parts) { stamps.addLast(now) }
                return@withLock
            }
            delay(stamps.first() + window - now)
        }
    }

    private companion object {
        const val MARGIN_MS = 1_000L
    }
}

fun interface RetryPolicy {
    /** Delay before retry number [attempt] (1-based), or null once [maxRetries] is used up. */
    fun delayBefore(attempt: Int, maxRetries: Int): Long?
}

/** Default backoff: 30s, 2m, 10m, then 10m for any further retries. */
class BackoffRetryPolicy(
    private val steps: List<Long> = listOf(30_000L, 120_000L, 600_000L),
) : RetryPolicy {
    override fun delayBefore(attempt: Int, maxRetries: Int): Long? =
        if (attempt > maxRetries) null else steps[(attempt - 1).coerceIn(0, steps.lastIndex)]
}

/** Picks the SIM slot for a message. */
fun interface SimSelector {
    fun select(requested: Int?, preference: SimPreference): Int?
}

/**
 * A requested SIM (by the API or the project) is binding: it is never swapped for another,
 * so the recipient always sees the expected number. The sender fails it if it's missing.
 */
class DefaultSimSelector(private val sims: SimCardProvider) : SimSelector {
    private val next = AtomicInteger(0)

    override fun select(requested: Int?, preference: SimPreference): Int? {
        if (requested != null) return requested
        val slots = sims.available().map { it.slot }
        if (slots.isEmpty()) return null
        return when (preference) {
            SimPreference.SIM1 -> slots.firstOrNull { it == 1 } ?: slots.first()
            SimPreference.SIM2 -> slots.firstOrNull { it == 2 } ?: slots.first()
            SimPreference.ALTERNATE -> slots[Math.floorMod(next.getAndIncrement(), slots.size)]
        }
    }
}

/**
 * A condition that must hold before anything is sent. New conditions (e.g. "only on Wi-Fi")
 * plug in without touching the dispatcher.
 */
fun interface SendCondition {
    fun isMet(settings: Flow<GatewaySettings>): Flow<Boolean>
}

val GatewayOnlineCondition = SendCondition { settings -> settings.map { it.gatewayOnline } }

class ChargingCondition(private val power: PowerMonitor) : SendCondition {
    override fun isMet(settings: Flow<GatewaySettings>): Flow<Boolean> =
        combine(settings, power.state) { s, p -> !s.onlySendWhileCharging || p.charging }
}
