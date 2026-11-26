package app.relay.gateway.engine

import app.relay.gateway.FakeClock
import app.relay.gateway.FakeSims
import app.relay.gateway.data.memory.InMemoryMessageRepository
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.SmsQuota
import app.relay.gateway.port.SmsQuotaProvider
import app.relay.protocol.MessageStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PoliciesTest {
    @Test
    fun `throttle spaces sends by the rate`() = runTest {
        val throttle = IntervalSendThrottle(FakeClock { testScheduler.currentTime })
        val slots = List(3) { throttle.awaitSlot(30); testScheduler.currentTime }
        assertEquals(listOf(0L, 2_000L, 4_000L), slots)
    }

    @Test
    fun `quota guard counts parts and waits for the window to clear`() = runTest {
        val guard = SmsQuotaGuard({ SmsQuota(maxParts = 3, windowMs = 60_000) }, FakeClock { testScheduler.currentTime })
        val at = listOf(1, 2, 1, 1).map { parts -> guard.awaitRoom(parts); testScheduler.currentTime }
        // 1 + 2 parts fill the quota; the next waits out the window plus margin, then there's room for one more.
        assertEquals(listOf(0L, 0L, 61_000L, 61_000L), at)
    }

    @Test
    fun `quota guard lets anything through when the platform imposes no quota`() = runTest {
        val guard = SmsQuotaGuard(SmsQuotaProvider { null }, FakeClock { testScheduler.currentTime })
        repeat(100) { guard.awaitRoom(5) }
        assertEquals(0L, testScheduler.currentTime)
    }

    @Test
    fun `quota guard sends an oversized message into an empty window`() = runTest {
        val guard = SmsQuotaGuard({ SmsQuota(maxParts = 3, windowMs = 60_000) }, FakeClock { testScheduler.currentTime })
        guard.awaitRoom(1)
        guard.awaitRoom(5)
        assertEquals(61_000L, testScheduler.currentTime)
    }

    @Test
    fun `backoff follows 30s 2m 10m and stops at max retries`() {
        val policy = BackoffRetryPolicy()
        assertEquals(30_000L, policy.delayBefore(1, 5))
        assertEquals(120_000L, policy.delayBefore(2, 5))
        assertEquals(600_000L, policy.delayBefore(4, 5))
        assertNull(policy.delayBefore(3, 2))
    }

    @Test
    fun `sim selector honours request then preference then alternates`() = runTest {
        val selector = selector(FakeSims(listOf(1, 2)))
        assertEquals(2, selector.select(2, SimPreference.SIM1, NEW))
        // A requested SIM is never swapped, even when it's missing.
        assertEquals(2, selector(FakeSims(listOf(1))).select(2, SimPreference.SIM1, NEW))
        assertEquals(1, selector.select(null, SimPreference.SIM1, NEW))
        assertEquals(listOf(1, 2, 1), List(3) { selector.select(null, SimPreference.ALTERNATE, "+25191100000$it") })
        assertEquals(1, selector(FakeSims(listOf(1))).select(null, SimPreference.SIM2, NEW))
    }

    // Sticky SIM. History goes on SIM 2: a fresh selector's next turn is SIM 1, so a pass means it stuck.

    @Test
    fun `taking turns, a person stays on the SIM that last texted them`() = runTest {
        val messages = history(went(KNOWN, sim = 2))
        val selector = selector(FakeSims(listOf(1, 2)), messages)
        assertEquals(2, selector.select(null, SimPreference.ALTERNATE, KNOWN))
        // However the number is written.
        assertEquals(2, selector.select(null, SimPreference.ALTERNATE, "0911 234-567"))
        assertEquals(2, selector.stickySim(KNOWN, SimPreference.ALTERNATE))
        // Sticky picks don't use up a turn: new people still start on SIM 1 and take turns.
        assertEquals(listOf(1, 2), List(2) { selector.select(null, SimPreference.ALTERNATE, "+25192200000$it") })
    }

    @Test
    fun `new people take turns`() = runTest {
        val selector = selector(FakeSims(listOf(1, 2)), history(went(KNOWN, sim = 2)))
        assertEquals(listOf(1, 2, 1), List(3) { selector.select(null, SimPreference.ALTERNATE, "+25192200000$it") })
        assertNull(selector.stickySim(NEW, SimPreference.ALTERNATE))
    }

    @Test
    fun `the latest message that went out decides, not a request or a failure`() = runTest {
        val messages = history(
            went(KNOWN, sim = 1, at = 10),
            went(KNOWN, sim = 2, at = 20),
            went(KNOWN, sim = 1, at = 30, status = MessageStatus.FAILED),
            went(KNOWN, sim = 1, at = 40, status = MessageStatus.QUEUED),
        )
        assertEquals(2, selector(FakeSims(listOf(1, 2)), messages).select(null, SimPreference.ALTERNATE, KNOWN))
        // One still in the air counts: it is the number they are about to see.
        messages.insertIfAbsent(went(KNOWN, sim = 1, at = 50, status = MessageStatus.SENDING))
        assertEquals(1, selector(FakeSims(listOf(1, 2)), messages).select(null, SimPreference.ALTERNATE, KNOWN))
    }

    @Test
    fun `when that SIM is gone the message takes the next turn`() = runTest {
        val sims = FakeSims(listOf(1, 2))
        val selector = selector(sims, history(went(KNOWN, sim = 2)))
        sims.slots = listOf(1, 3)
        assertEquals(listOf(1, 3), List(2) { selector.select(null, SimPreference.ALTERNATE, KNOWN) })
        assertNull(selector.stickySim(KNOWN, SimPreference.ALTERNATE))
        sims.slots = listOf(1)
        assertEquals(1, selector.select(null, SimPreference.ALTERNATE, KNOWN))
        // Back in: it sticks again.
        sims.slots = listOf(1, 2)
        assertEquals(2, selector.select(null, SimPreference.ALTERNATE, KNOWN))
    }

    @Test
    fun `a requested SIM still wins, and SIM 1 or SIM 2 still mean that SIM`() = runTest {
        val selector = selector(FakeSims(listOf(1, 2)), history(went(KNOWN, sim = 2)))
        assertEquals(1, selector.select(1, SimPreference.ALTERNATE, KNOWN))
        assertEquals(1, selector.select(null, SimPreference.SIM1, KNOWN))
        assertNull(selector.stickySim(KNOWN, SimPreference.SIM1))
        assertEquals(2, selector(FakeSims(listOf(1, 2)), history(went(KNOWN, sim = 1))).select(null, SimPreference.SIM2, KNOWN))
    }

    @Test
    fun `outside the reply window people take turns again`() = runTest {
        var now = GatewaySettings.REPLY_WINDOW_MS + 100
        val messages = history(went(KNOWN, sim = 2, at = 100))
        val selector = DefaultSimSelector(FakeSims(listOf(1, 2)), messages, FakeClock { now })
        assertEquals(2, selector.select(null, SimPreference.ALTERNATE, KNOWN))
        now += 1
        assertEquals(1, selector.select(null, SimPreference.ALTERNATE, KNOWN))
    }

    @Test
    fun `it holds across a restart, read from the message history`() = runTest {
        val messages = history()
        val before = selector(FakeSims(listOf(1, 2)), messages)
        before.select(null, SimPreference.ALTERNATE, NEW) // SIM 1 to someone else
        val sim = before.select(null, SimPreference.ALTERNATE, KNOWN)
        assertEquals(2, sim)
        messages.insertIfAbsent(went(KNOWN, sim = sim!!))
        // A new selector starts its turns over at SIM 1, but the history says SIM 2.
        assertEquals(2, selector(FakeSims(listOf(1, 2)), messages).select(null, SimPreference.ALTERNATE, KNOWN))
    }

    @Test
    fun `short codes and named senders never stick`() = runTest {
        val messages = history(went("12345", sim = 2), went("CBE", sim = 2))
        val selector = selector(FakeSims(listOf(1, 2)), messages)
        assertEquals(1, selector.select(null, SimPreference.ALTERNATE, "12345"))
        assertEquals(2, selector.select(null, SimPreference.ALTERNATE, "CBE"))
        assertNull(selector.stickySim("CBE", SimPreference.ALTERNATE))
    }

    private companion object {
        const val KNOWN = "+251911234567"
        const val NEW = "+251900000000"

        fun selector(sims: FakeSims, messages: InMemoryMessageRepository = InMemoryMessageRepository()) =
            DefaultSimSelector(sims, messages, FakeClock { 1_000 })

        suspend fun history(vararg sent: Message) = InMemoryMessageRepository().apply { sent.forEach { insertIfAbsent(it) } }

        private var ids = 0

        fun went(to: String, sim: Int, at: Long = 0, status: MessageStatus = MessageStatus.DELIVERED) =
            Message("h${ids++}", "p1", to, "hi", status, MessageTimeline(receivedAt = at, queuedAt = at, sentAt = at), sim = sim)
    }
}
