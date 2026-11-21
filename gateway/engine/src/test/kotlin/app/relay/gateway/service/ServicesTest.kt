package app.relay.gateway.service

import app.relay.gateway.model.Batch
import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.gateway.model.StatusCounts
import app.relay.protocol.BatchSource
import app.relay.protocol.BatchStatus
import app.relay.protocol.MessageStatus
import app.relay.gateway.FakeRelayApi
import app.relay.gateway.data.memory.InMemoryBatchRepository
import app.relay.gateway.data.memory.InMemoryDeviceIdentityStore
import app.relay.gateway.data.memory.InMemoryInboundRepository
import app.relay.gateway.data.memory.InMemoryJoinKeywordStore
import app.relay.gateway.data.memory.InMemoryMessageRepository
import app.relay.gateway.data.memory.InMemoryProjectRepository
import app.relay.gateway.data.memory.InMemorySettingsRepository
import app.relay.gateway.model.IncomingSms
import app.relay.gateway.port.RelayApiException
import kotlinx.coroutines.flow.first
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
    fun `reads the web's pairing page, on any host and in any language`() {
        val expected = PairingLink("https://api.relay.app", "KTQZ-W8ZJ")
        assertEquals(expected, DeviceSetup.parseLink("https://smswire.app/pair?s=https%3A%2F%2Fapi.relay.app%2F&c=KTQZ-W8ZJ"))
        // A self-hosted site, and the page in another language.
        assertEquals(expected, DeviceSetup.parseLink("https://sms.selfhost.example/am/pair?s=https%3A%2F%2Fapi.relay.app&c=KTQZ-W8ZJ"))
        assertEquals(expected, DeviceSetup.parseLink("https://smswire.app/pair/?s=https%3A%2F%2Fapi.relay.app&c=KTQZ-W8ZJ"))
        assertEquals(
            PairingLink("http://10.0.2.2:8080", "AAAA-BBBB"),
            DeviceSetup.parseLink("http://localhost:5173/pair?s=http%3A%2F%2F10.0.2.2%3A8080&c=AAAA-BBBB"),
        )
        // Another page of the same site is not a pairing link, however it is dressed up.
        assertNull(DeviceSetup.parseLink("https://smswire.app/download?s=https%3A%2F%2Fapi.relay.app&c=AAAA"))
        assertNull(DeviceSetup.parseLink("https://smswire.app/repair?s=https%3A%2F%2Fapi.relay.app&c=AAAA"))
        assertNull(DeviceSetup.parseLink("https://smswire.app/pair"))
    }

    private class Rig(phoneKey: String? = null) {
        val identity = InMemoryDeviceIdentityStore()
        val settings = InMemorySettingsRepository()
        val api = FakeRelayApi()
        val messages = InMemoryMessageRepository()
        val batches = InMemoryBatchRepository()
        val projects = InMemoryProjectRepository()
        val inbound = InMemoryInboundRepository()
        val setup = DeviceSetup(identity, settings, api, WorkspaceData(messages, batches, projects, inbound, InMemoryJoinKeywordStore()), phoneKey)

        /** What a paired phone has: a sent and a queued message, a done and a running batch, an upload waiting. */
        suspend fun stock() {
            messages.insertIfAbsent(message("sent", MessageStatus.SENT))
            messages.insertIfAbsent(message("queued", MessageStatus.QUEUED))
            batches.upsert(Batch("b_done", "p1", "Done", BatchSource.APP, 1, 10, BatchStatus.DONE, 0))
            batches.upsert(Batch("b_run", "p1", "Running", BatchSource.APP, 1, 10, BatchStatus.RUNNING, 0))
            inbound.insertIfAbsent(IncomingSms("r1", "+251911000000", "STOP", 0, 1, reported = false))
            projects.replaceAll(listOf(api.project("p1", "Shop")))
        }

        private fun message(id: String, status: MessageStatus) =
            Message(id, "p1", "+251911000000", "Hi", status, MessageTimeline(receivedAt = 1, queuedAt = 1))
    }

    @Test
    fun `pairing stores the identity and points the phone at that server`() = runTest {
        val rig = Rig(phoneKey = "a1b2c3")

        assertFailsWith<RelayApiException> { rig.setup.pair("https://api.relay.app", "WRONG", "Pixel", "Pixel 8") }
        assertNull(rig.identity.load())

        val paired = rig.setup.pair("https://api.relay.app/", " GOOD-CODE ", "Pixel", "Pixel 8")
        assertEquals(listOf<String?>("a1b2c3"), rig.api.phoneKeys)
        assertEquals("Fortune Clinics", paired.workspaceName)
        assertEquals(paired, rig.identity.load())
        assertEquals("https://api.relay.app", rig.settings.current().serverUrl)

        assertEquals(UnpairResult.DONE, rig.setup.unpair(keepHistory = false))
        assertNull(rig.identity.load())
        assertEquals(1, rig.api.unpaired)
    }

    @Test
    fun `unpairing clears the queue, and the history unless kept`() = runTest {
        val rig = Rig()
        rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.stock()

        rig.setup.unpair(keepHistory = true)
        assertEquals(listOf("sent"), rig.messages.all.map { it.id })
        // Kept history counts as reported: none of it goes to the next workspace's server.
        assertTrue(rig.messages.findUnreported().isEmpty())
        assertEquals(listOf("b_done"), rig.batches.observeAll().first().map { it.id })
        assertTrue(rig.inbound.unreported().isEmpty())
        assertTrue(rig.projects.observeAll().first().isEmpty())

        rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.stock()
        rig.setup.unpair(keepHistory = false)
        assertTrue(rig.messages.all.isEmpty())
        assertTrue(rig.batches.observeAll().first().isEmpty())
    }

    @Test
    fun `unpairing offline changes nothing until the user forgets anyway`() = runTest {
        val rig = Rig()
        val paired = rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.stock()
        rig.api.unpairFails = true

        assertEquals(UnpairResult.OFFLINE, rig.setup.unpair(keepHistory = false))
        assertEquals(paired, rig.identity.load())
        assertEquals(2, rig.messages.all.size)

        rig.setup.forgetAnyway(keepHistory = false)
        assertNull(rig.identity.load())
        assertTrue(rig.messages.all.isEmpty())
    }

    @Test
    fun `a token the server already refuses counts as unpaired`() = runTest {
        val rig = Rig()
        rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.api.unpairError = RelayApiException(401, "Unauthorized")

        assertEquals(UnpairResult.DONE, rig.setup.unpair(keepHistory = false))
        assertNull(rig.identity.load())

        rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.api.unpairError = RelayApiException(503, "Unavailable")
        assertEquals(UnpairResult.OFFLINE, rig.setup.unpair(keepHistory = false))
    }

    @Test
    fun `switching joins the new workspace first, then leaves the old one`() = runTest {
        val rig = Rig()
        rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.stock()

        val result = rig.setup.switchTo("https://eu.relay.app", "OTHER-CODE", "Pixel", "Pixel 8", keepHistory = false)

        assertEquals(SwitchResult.Switched("Abebe Shop", leftOld = true), result)
        assertEquals(1, rig.api.unpaired)
        assertEquals("dev_2", rig.identity.load()?.deviceId)
        assertEquals("https://eu.relay.app", rig.settings.current().serverUrl)
        assertTrue(rig.messages.all.isEmpty())
        assertTrue(rig.inbound.unreported().isEmpty())
        assertFalse(rig.setup.switching.value)
    }

    @Test
    fun `a code that doesn't work leaves the phone where it was`() = runTest {
        val rig = Rig()
        val paired = rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.stock()

        assertFailsWith<RelayApiException> {
            rig.setup.switchTo("https://api.relay.app", "WRONG", "Pixel", "Pixel 8", keepHistory = false)
        }
        assertEquals(paired, rig.identity.load())
        assertEquals(0, rig.api.unpaired)
        assertEquals(2, rig.messages.all.size)
        assertEquals("https://api.relay.app", rig.settings.current().serverUrl)
    }

    @Test
    fun `switching still happens when the old server can't be told`() = runTest {
        val rig = Rig()
        rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.api.unpairFails = true

        val result = rig.setup.switchTo("https://api.relay.app", "OTHER-CODE", "Pixel", "Pixel 8", keepHistory = true)
        assertEquals(SwitchResult.Switched("Abebe Shop", leftOld = false), result)
        assertEquals("dev_2", rig.identity.load()?.deviceId)
    }

    @Test
    fun `a code from the same workspace just pairs again`() = runTest {
        val rig = Rig()
        rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.stock()

        val result = rig.setup.switchTo("https://api.relay.app", "SAME-CODE", "Pixel", "Pixel 8", keepHistory = false)
        assertEquals(SwitchResult.SameWorkspace("Fortune Clinics"), result)
        assertEquals(0, rig.api.unpaired)
        assertEquals("token-3", rig.identity.load()?.token)
        assertEquals(2, rig.messages.all.size)
    }

    @Test
    fun `a late revoke of the old pairing doesn't touch the new one`() = runTest {
        val rig = Rig()
        val old = rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.setup.switchTo("https://api.relay.app", "OTHER-CODE", "Pixel", "Pixel 8", keepHistory = false)

        rig.setup.revokedOnWeb(old)
        assertEquals("dev_2", rig.identity.load()?.deviceId)

        // Paired again in the same workspace: the old token's socket closing is no revoke either.
        val current = rig.identity.load()!!
        rig.setup.switchTo("https://api.relay.app", "SAME-CODE", "Pixel", "Pixel 8", keepHistory = false)
        rig.setup.revokedOnWeb(current)
        assertEquals("token-3", rig.identity.load()?.token)
    }

    @Test
    fun `revoked on the web drops the queue and keeps the history`() = runTest {
        val rig = Rig()
        val paired = rig.setup.pair("https://api.relay.app", "GOOD-CODE", "Pixel", "Pixel 8")
        rig.stock()

        rig.setup.revokedOnWeb(paired)
        assertNull(rig.identity.load())
        assertEquals(listOf("sent"), rig.messages.all.map { it.id })
    }
}
