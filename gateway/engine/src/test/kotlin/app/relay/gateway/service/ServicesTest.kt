package app.relay.gateway.service

import app.relay.gateway.model.Batch
import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.gateway.model.StatusCounts
import app.relay.protocol.BatchSource
import app.relay.protocol.BatchStatus
import app.relay.protocol.MessageStatus
import app.relay.gateway.FakeRelayApi
import app.relay.gateway.data.memory.InMemoryDeviceIdentityStore
import app.relay.gateway.data.memory.InMemorySettingsRepository
import app.relay.gateway.port.RelayApiException
import kotlinx.coroutines.test.runTest
import kotlin.test.assertFailsWith
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BatchProgressTest {
    private val batch = Batch("b1", "p1", "Sale", BatchSource.API, 100, 30, BatchStatus.RUNNING, 0)

    @Test
    fun `splits progress like the design`() {
        val p = BatchProgress(batch, StatusCounts(queued = 10, sent = 6, delivered = 40, failed = 4), deliveryReports = true)
        assertEquals(44, p.processed)
        assertEquals(56, p.pending)
        assertEquals(50, p.notSent)
        assertEquals(44, p.percent)
        assertEquals(2, p.etaMinutes)
        assertFalse(p.isComplete)
    }

    @Test
    fun `completes once nothing is left to send or confirm`() {
        val waiting = BatchProgress(batch, StatusCounts(sent = 2, delivered = 98), deliveryReports = true)
        assertEquals(BatchStatus.RUNNING, waiting.status)
        assertEquals(BatchStatus.DONE, waiting.copy(deliveryReports = false).status)
        assertEquals(BatchStatus.DONE, BatchProgress(batch, StatusCounts(delivered = 99, failed = 1), true).status)
    }

    @Test
    fun `paused batch has no eta`() {
        assertNull(BatchProgress(batch.copy(status = BatchStatus.PAUSED), StatusCounts(), true).etaMinutes)
    }
}

class ThroughputTest {
    @Test
    fun `buckets sends per minute with current minute last`() {
        val now = 100 * 60_000L + 5_000
        val times = listOf(now - 1_000, now - 2_000, now - 61_000, now - 29 * 60_000L, now - 31 * 60_000L)
        val buckets = ThroughputCalculator.buckets(times, now, 30)
        assertEquals(30, buckets.size)
        assertEquals(2, buckets.last())
        assertEquals(1, buckets[28])
        assertEquals(1, buckets[0])
        assertEquals(4, buckets.sum())
    }
}

class DeliveryTimelineTest {
    private val base = Message("m1", "p1", "+447700900123", "hi", MessageStatus.QUEUED, MessageTimeline(1, 2))

    @Test
    fun `delivered message shows four completed steps`() {
        val m = base.copy(status = MessageStatus.DELIVERED, timeline = base.timeline.copy(sentAt = 3, deliveredAt = 4))
        val steps = DeliveryTimeline.build(m, "Shopfront", 30)
        assertEquals(listOf("Received via API", "Queued on device", "Sent to carrier", "Delivered"), steps.map { it.label })
        assertTrue(steps.all { it.state == StepState.DONE })
        assertEquals("Shopfront · POST /messages", steps[0].detail)
    }

    @Test
    fun `failure before carrier ends the timeline`() {
        val m = base.copy(status = MessageStatus.FAILED, error = "RESULT_ERROR_NO_SERVICE", timeline = base.timeline.copy(failedAt = 5))
        val steps = DeliveryTimeline.build(m, null, 30)
        assertEquals(3, steps.size)
        assertEquals(StepState.FAILED, steps.last().state)
        assertEquals("RESULT_ERROR_NO_SERVICE", steps.last().detail)
    }

    @Test
    fun `sent message awaits report`() {
        val m = base.copy(status = MessageStatus.SENT, timeline = base.timeline.copy(sentAt = 3))
        assertEquals(StepState.CURRENT, DeliveryTimeline.build(m, null, 30).last().state)
    }
}

class DeviceSetupTest {
    @Test
    fun `reads the pairing QR`() {
        assertEquals(
            PairingLink("https://api.relay.app", "KTQZ-W8ZJ"),
            DeviceSetup.parseLink("smswire://pair?s=https%3A%2F%2Fapi.relay.app%2F&c=KTQZ-W8ZJ"),
        )
        assertNull(DeviceSetup.parseLink("https://example.com"))
        assertNull(DeviceSetup.parseLink("smswire://pair?s=ftp%3A%2F%2Fx&c=AAAA"))
        assertNull(DeviceSetup.parseLink("smswire://pair?s=https%3A%2F%2Fx"))
        // Links from before the renames are not read (hard cut).
        assertNull(DeviceSetup.parseLink("relay://pair?s=https%3A%2F%2Fapi.relay.app&c=AAAA"))
        assertNull(DeviceSetup.parseLink("josms://pair?s=https%3A%2F%2Fapi.relay.app&c=AAAA"))
    }

    @Test
    fun `pairing stores the identity and points the phone at that server`() = runTest {
        val identity = InMemoryDeviceIdentityStore()
        val settings = InMemorySettingsRepository()
        val api = FakeRelayApi()
        val setup = DeviceSetup(identity, settings, api, phoneKey = "a1b2c3")

        assertFailsWith<RelayApiException> { setup.pair("https://api.relay.app", "WRONG", "Pixel", "Pixel 8") }
        assertNull(identity.load())

        val paired = setup.pair("https://api.relay.app/", " GOOD-CODE ", "Pixel", "Pixel 8")
        assertEquals(listOf<String?>("a1b2c3"), api.phoneKeys)
        assertEquals("Fortune Clinics", paired.workspaceName)
        assertEquals(paired, identity.load())
        assertEquals("https://api.relay.app", settings.current().serverUrl)

        setup.unpair()
        assertNull(identity.load())
        assertEquals(1, api.unpaired)
    }

    @Test
    fun `unpairing offline still forgets the pairing`() = runTest {
        val identity = InMemoryDeviceIdentityStore()
        val api = FakeRelayApi().apply { unpairFails = true }
        val setup = DeviceSetup(identity, InMemorySettingsRepository(), api)
        setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")

        setup.unpair()
        assertNull(identity.load())
    }
}
