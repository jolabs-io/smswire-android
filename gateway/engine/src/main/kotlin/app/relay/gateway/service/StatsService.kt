package app.relay.gateway.service

import app.relay.gateway.model.StatusCounts
import app.relay.gateway.port.Clock
import app.relay.gateway.port.MessageRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

data class TodayStats(val counts: StatusCounts) {
    val sent: Int get() = counts.attempted
    val delivered: Int get() = counts.delivered
    val failed: Int get() = counts.failed
    val awaiting: Int get() = counts.sent

    /** Delivered share of finished messages, or null before anything finished. */
    val deliveryRate: Double?
        get() = (delivered + failed).takeIf { it > 0 }?.let { delivered.toDouble() / it }
}

/** Per-minute send counts, oldest first; the last bucket is the current minute. */
data class Throughput(val perMinute: List<Int>) {
    val current: Int get() = perMinute.lastOrNull() ?: 0
}

object ThroughputCalculator {
    fun buckets(sentTimes: List<Long>, now: Long, minutes: Int): List<Int> {
        val counts = IntArray(minutes)
        val currentMinute = now / MINUTE
        for (t in sentTimes) {
            val age = (currentMinute - t / MINUTE).toInt()
            if (age in 0 until minutes) counts[minutes - 1 - age]++
        }
        return counts.toList()
    }

    private const val MINUTE = 60_000L
}

fun ticker(periodMs: Long): Flow<Unit> = flow {
    while (true) {
        emit(Unit)
        delay(periodMs)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class StatsService(
    private val messages: MessageRepository,
    private val clock: Clock,
    private val ticker: Flow<Unit>,
    private val windowMinutes: Int = 30,
) {
    fun observeToday(): Flow<TodayStats> =
        ticker.flatMapLatest { messages.observeStatusCounts(clock.startOfToday()) }.map(::TodayStats)

    fun observeThroughput(): Flow<Throughput> = ticker.flatMapLatest {
        val now = clock.now()
        messages.observeSentTimes(now - windowMinutes * 60_000L)
            .map { Throughput(ThroughputCalculator.buckets(it, clock.now(), windowMinutes)) }
    }
}
