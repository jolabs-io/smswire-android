package app.relay.gateway.engine

import app.relay.gateway.FakeClock
import app.relay.gateway.FakeSims
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.SmsQuota
import app.relay.gateway.port.SmsQuotaProvider
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
    fun `sim selector honours request then preference then alternates`() {
        val selector = DefaultSimSelector(FakeSims(listOf(1, 2)))
        assertEquals(2, selector.select(2, SimPreference.SIM1))
        // A requested SIM is never swapped, even when it's missing.
        assertEquals(2, DefaultSimSelector(FakeSims(listOf(1))).select(2, SimPreference.SIM1))
        assertEquals(1, selector.select(null, SimPreference.SIM1))
        assertEquals(listOf(1, 2, 1), List(3) { selector.select(null, SimPreference.ALTERNATE) })
        assertEquals(1, DefaultSimSelector(FakeSims(listOf(1))).select(null, SimPreference.SIM2))
    }
}
