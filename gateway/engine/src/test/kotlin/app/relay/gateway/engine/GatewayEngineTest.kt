package app.relay.gateway.engine

import app.relay.gateway.FakeClock
import app.relay.gateway.FakeConnection
import app.relay.gateway.FakePower
import app.relay.gateway.FakeRelayApi
import app.relay.gateway.FakeSims
import app.relay.gateway.FakeSmsSender
import app.relay.gateway.GatewayComponent
import app.relay.gateway.GatewayPorts
import app.relay.gateway.data.memory.InMemoryBatchRepository
import app.relay.gateway.data.memory.InMemoryDeviceIdentityStore
import app.relay.gateway.data.memory.InMemoryMessageRepository
import app.relay.gateway.data.memory.InMemoryProjectRepository
import app.relay.gateway.data.memory.InMemorySettingsRepository
import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.ConnectionState
import app.relay.gateway.port.PowerState
import app.relay.gateway.port.SmsEvent
import app.relay.protocol.BatchDto
import app.relay.protocol.BatchSource
import app.relay.protocol.BatchStatus
import app.relay.protocol.DefaultSim
import app.relay.protocol.DeviceFrame
import app.relay.protocol.MessageStatus
import app.relay.protocol.MessageType
import app.relay.protocol.RemoteSettings
import app.relay.protocol.RemoteSettingsPatch
import app.relay.protocol.SimInfo
import app.relay.protocol.ServerFrame
import app.relay.protocol.SmsJob
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayEngineTest {
    private class Harness(scope: TestScope, settings: GatewaySettings = GatewaySettings(), receivesSms: Boolean = true) {
        val messages = InMemoryMessageRepository()
        val batches = InMemoryBatchRepository()
        val settingsRepo = InMemorySettingsRepository(settings)
        val connection = FakeConnection()
        val radio = FakeSmsSender().apply { now = { scope.testScheduler.currentTime } }
        val power = FakePower()
        val api = FakeRelayApi()
        val projects = InMemoryProjectRepository()
        val identity = InMemoryDeviceIdentityStore(DeviceIdentity("dev_1", "t", "Pixel", "Pixel 8"))
        val component = GatewayComponent(
            GatewayPorts(
                messages = messages,
                batches = batches,
                projects = projects,
                settings = settingsRepo,
                identity = identity,
                connection = connection,
                api = api,
                sender = radio,
                smsEvents = radio,
                sims = FakeSims(),
                power = power,
                clock = FakeClock { scope.testScheduler.currentTime },
                appVersion = "test",
                receivesSms = receivesSms,
            ),
        )

        init {
            component.engine.start(scope.backgroundScope)
            scope.runCurrent()
        }

        fun job(id: String, batchId: String? = null, type: MessageType = MessageType.TRANSACTIONAL, priority: Int = 1) =
            SmsJob(messageId = id, projectId = "p1", to = "+447700900$id", body = "hi", receivedAt = 0, batchId = batchId, type = type, priority = priority)

        fun dispatch(vararg jobs: SmsJob) {
            connection.frames.tryEmit(ServerFrame.Dispatch(jobs.toList()))
        }

        suspend fun status(id: String) = messages.find(id)!!.status

        fun reports() = connection.sent.filterIsInstance<DeviceFrame.StatusReport>()
    }

    @Test
    fun `greets the server on connect`() = runTest {
        val h = Harness(this)
        assertEquals(
            DeviceFrame.Hello(
                gatewayOnline = true,
                appVersion = "test",
                sims = listOf(SimInfo(1, "Carrier 1"), SimInfo(2, "Carrier 2")),
                battery = 80,
                charging = true,
                smsLimitPerMinute = null,
                settings = RemoteSettings(
                    gatewayOnline = true,
                    defaultSim = DefaultSim.SIM1,
                    maxRatePerMinute = 30,
                    maxRetries = 2,
                    requestDeliveryReports = true,
                    keepAlive = true,
                    startOnBoot = true,
                    onlySendWhileCharging = false,
                    receivesSms = true,
                ),
            ),
            h.connection.sent.first(),
        )
    }

    @Test
    fun `settings changed on the web are applied and reported back`() = runTest {
        val h = Harness(this)
        h.connection.frames.emit(ServerFrame.ApplySettings(RemoteSettingsPatch(maxRatePerMinute = 10, defaultSim = DefaultSim.ALTERNATE)))
        runCurrent()
        val applied = h.settingsRepo.current()
        assertEquals(10, applied.maxRatePerMinute)
        assertEquals(SimPreference.ALTERNATE, applied.defaultSim)
        val reported = h.connection.sent.filterIsInstance<DeviceFrame.Settings>().single().settings
        assertEquals(10, reported.maxRatePerMinute)
        assertEquals(DefaultSim.ALTERNATE, reported.defaultSim)
    }

    @Test
    fun `a change made on the phone is reported`() = runTest {
        val h = Harness(this)
        h.settingsRepo.update { it.copy(maxRetries = 4) }
        runCurrent()
        assertEquals(4, h.connection.sent.filterIsInstance<DeviceFrame.Settings>().single().settings.maxRetries)
    }

    @Test
    fun `an apply that changes nothing is still confirmed`() = runTest {
        val h = Harness(this)
        h.connection.frames.emit(ServerFrame.ApplySettings(RemoteSettingsPatch(maxRatePerMinute = 30)))
        runCurrent()
        assertEquals(1, h.connection.sent.filterIsInstance<DeviceFrame.Settings>().size)
    }

    @Test
    fun `pausing from the web holds the queue and tells the server`() = runTest {
        val h = Harness(this)
        h.connection.frames.emit(ServerFrame.ApplySettings(RemoteSettingsPatch(gatewayOnline = false)))
        runCurrent()
        assertTrue(h.connection.sent.contains(DeviceFrame.GatewayState(false)))
        h.dispatch(h.job("001"))
        advanceTimeBy(60_000)
        assertTrue(h.radio.sent.isEmpty())
    }

    @Test
    fun `sends dispatched jobs at the configured rate`() = runTest {
        val h = Harness(this)
        h.dispatch(h.job("001"), h.job("002"), h.job("003"))
        advanceTimeBy(10_000)
        assertEquals(listOf(0L, 2_000L, 4_000L), h.radio.sent.map { it.first - h.radio.sent.first().first })
        assertEquals(MessageStatus.SENDING, h.status("001"))
    }

    @Test
    fun `duplicate dispatch is sent once`() = runTest {
        val h = Harness(this)
        h.dispatch(h.job("001"))
        h.dispatch(h.job("001"))
        advanceTimeBy(10_000)
        assertEquals(1, h.radio.sent.size)
    }

    @Test
    fun `radio callbacks drive status and are reported`() = runTest {
        val h = Harness(this)
        h.dispatch(h.job("001"))
        runCurrent()
        h.radio.events.emit(SmsEvent.Sent("001", 100))
        h.radio.events.emit(SmsEvent.Delivered("001", 200))
        runCurrent()
        assertEquals(MessageStatus.DELIVERED, h.status("001"))
        assertEquals(
            listOf(MessageStatus.SENDING, MessageStatus.SENT, MessageStatus.DELIVERED),
            h.reports().map { it.status },
        )
    }

    @Test
    fun `send failure is retried after backoff then fails for good`() = runTest {
        val h = Harness(this, GatewaySettings(maxRetries = 1))
        h.radio.failNext = "RESULT_ERROR_NO_SERVICE"
        h.dispatch(h.job("001"))
        runCurrent()
        assertEquals(MessageStatus.QUEUED, h.status("001"))
        assertTrue(h.radio.sent.isEmpty())

        advanceTimeBy(31_000)
        assertEquals(1, h.radio.sent.size)
        h.radio.events.emit(SmsEvent.Failed("001", testScheduler.currentTime, "RESULT_ERROR_GENERIC_FAILURE", retryable = true))
        runCurrent()
        val failed = h.messages.find("001")!!
        assertEquals(MessageStatus.FAILED, failed.status)
        assertEquals("RESULT_ERROR_GENERIC_FAILURE", failed.error)
    }

    @Test
    fun `nothing is sent while the gateway is paused`() = runTest {
        val h = Harness(this)
        h.component.controller.setOnline(false)
        h.dispatch(h.job("001"))
        advanceTimeBy(60_000)
        assertTrue(h.radio.sent.isEmpty())
        assertTrue(h.connection.sent.contains(DeviceFrame.GatewayState(false)))

        h.component.controller.setOnline(true)
        advanceTimeBy(1_000)
        assertEquals(1, h.radio.sent.size)
    }

    @Test
    fun `charging condition holds the queue on battery`() = runTest {
        val h = Harness(this, GatewaySettings(onlySendWhileCharging = true))
        h.power.state.value = h.power.state.value.copy(charging = false)
        runCurrent()
        h.dispatch(h.job("001"))
        advanceTimeBy(30_000)
        assertTrue(h.radio.sent.isEmpty())
        assertTrue(h.component.controller.status.first().waitingForCharger)
        h.power.state.value = h.power.state.value.copy(charging = true)
        advanceTimeBy(1_000)
        assertEquals(1, h.radio.sent.size)
        assertFalse(h.component.controller.status.first().waitingForCharger)
    }

    @Test
    fun `paused batch is skipped and resumes`() = runTest {
        val h = Harness(this)
        h.connection.frames.emit(
            ServerFrame.BatchAnnounced(BatchDto("b1", "p1", "Sale", BatchSource.API, 1, 30, BatchStatus.RUNNING, 0)),
        )
        runCurrent()
        h.component.batchService.setPaused("b1", paused = true)
        h.dispatch(h.job("001", batchId = "b1"), h.job("002"))
        advanceTimeBy(30_000)
        assertEquals(listOf("002"), h.radio.sent.map { it.second.messageId })

        h.component.batchService.setPaused("b1", paused = false)
        advanceTimeBy(5_000)
        assertEquals(listOf("002", "001"), h.radio.sent.map { it.second.messageId })
    }

    @Test
    fun `acked status is not re-sent on reconnect`() = runTest {
        val h = Harness(this)
        h.dispatch(h.job("001"), h.job("002"))
        advanceTimeBy(3_000)
        h.connection.frames.emit(ServerFrame.StatusAck("001", MessageStatus.SENDING))
        runCurrent()
        h.connection.sent.clear()

        h.connection.disconnect()
        runCurrent()
        h.connection.connect("", DeviceIdentity("dev_1", "t", "", ""))
        runCurrent()
        assertEquals(listOf("002"), h.reports().map { it.messageId })
    }

    @Test
    fun `manual retry re-queues a failed message`() = runTest {
        val h = Harness(this, GatewaySettings(maxRetries = 0))
        h.dispatch(h.job("001"))
        runCurrent()
        h.radio.events.emit(SmsEvent.Failed("001", 1, "boom", retryable = true))
        runCurrent()
        assertEquals(MessageStatus.FAILED, h.status("001"))

        assertTrue(h.component.messageService.retry("001"))
        advanceTimeBy(5_000)
        assertEquals(2, h.radio.sent.size)
    }

    @Test
    fun `an OTP is sent before a marketing backlog`() = runTest {
        val h = Harness(this)
        h.dispatch(
            h.job("m01", type = MessageType.MARKETING, priority = 2),
            h.job("m02", type = MessageType.MARKETING, priority = 2),
            h.job("otp", type = MessageType.OTP, priority = 0),
        )
        advanceTimeBy(10_000)
        assertEquals(listOf("otp", "m01", "m02"), h.radio.sent.map { it.second.messageId })
    }

    @Test
    fun `a batch paused on the web stops sending here`() = runTest {
        val h = Harness(this)
        h.connection.frames.tryEmit(ServerFrame.BatchAnnounced(BatchDto("b1", "p1", "Promo", BatchSource.WEB, 2, 60, BatchStatus.RUNNING, 0)))
        h.connection.frames.tryEmit(ServerFrame.BatchControl("b1", BatchStatus.PAUSED))
        runCurrent()
        h.dispatch(h.job("001", batchId = "b1"))
        advanceTimeBy(10_000)
        assertTrue(h.radio.sent.isEmpty())

        h.connection.frames.tryEmit(ServerFrame.BatchControl("b1", BatchStatus.RUNNING))
        advanceTimeBy(10_000)
        assertEquals(1, h.radio.sent.size)
    }

    @Test
    fun `projects changed on the web are fetched again`() = runTest {
        val h = Harness(this)
        h.api.projects = listOf(h.api.project("p9", "From the web"))
        h.connection.frames.tryEmit(ServerFrame.ProjectsChanged)
        runCurrent()
        assertEquals("From the web", h.projects.find("p9")?.name)
    }

    @Test
    fun `a revoked token forgets the pairing`() = runTest {
        val h = Harness(this)
        h.connection.state.value = ConnectionState.Revoked
        runCurrent()
        assertEquals(null, h.identity.load())
    }

    @Test
    fun `every battery percent and charging change is reported`() = runTest {
        val h = Harness(this)
        h.power.state.value = PowerState(80, true)
        runCurrent()
        assertTrue(h.connection.sent.none { it is DeviceFrame.DeviceStatus })
        h.power.state.value = PowerState(79, true)
        runCurrent()
        h.power.state.value = PowerState(79, false)
        runCurrent()
        assertEquals(
            listOf(79 to true, 79 to false),
            h.connection.sent.filterIsInstance<DeviceFrame.DeviceStatus>().map { it.battery to it.charging },
        )
    }

    @Test
    fun `received SMS reach the server once and are re-sent until acked`() = runTest {
        val h = Harness(this, GatewaySettings(inboxSync = InboxSync.ALL))
        h.component.inboundSync.receive("+251911234567", "STOP", sentAt = 1_000, receivedAt = 2_000, sim = 1)
        h.component.inboundSync.receive("+251911234567", "STOP", sentAt = 1_000, receivedAt = 2_500, sim = 1)
        val first = h.connection.sent.filterIsInstance<DeviceFrame.Incoming>()
        assertEquals(1, first.size)
        assertEquals("STOP", first.single().body)

        // Not acked: a reconnect sends it again.
        h.connection.state.value = ConnectionState.Disconnected
        h.connection.state.value = ConnectionState.Connected(1)
        runCurrent()
        assertEquals(2, h.connection.sent.filterIsInstance<DeviceFrame.Incoming>().size)

        h.connection.frames.tryEmit(ServerFrame.IncomingAck(first.single().ref))
        runCurrent()
        h.connection.state.value = ConnectionState.Disconnected
        h.connection.state.value = ConnectionState.Connected(2)
        runCurrent()
        assertEquals(2, h.connection.sent.filterIsInstance<DeviceFrame.Incoming>().size)
    }

    @Test
    fun `replies only sends a reply from a number the phone texted, however it is written, and nothing else`() = runTest {
        val h = Harness(this)
        h.dispatch(SmsJob(messageId = "m1", projectId = "p1", to = "+251911234567", body = "Your order shipped", receivedAt = 1_000))
        runCurrent()

        h.component.inboundSync.receive("0911 234 567", "STOP", sentAt = 1, receivedAt = 2_000, sim = 1)
        h.component.inboundSync.receive("CBE", "Your code is 482913", sentAt = 2, receivedAt = 2_000, sim = 1)
        h.component.inboundSync.receive("8994", "Balance: 12.50 ETB", sentAt = 3, receivedAt = 2_000, sim = 1)
        h.component.inboundSync.receive("+251922000000", "hey, it's me", sentAt = 4, receivedAt = 2_000, sim = 1)

        assertEquals(listOf("STOP"), h.connection.sent.filterIsInstance<DeviceFrame.Incoming>().map { it.body })
    }

    @Test
    fun `replies only forgets numbers texted more than 30 days ago`() = runTest {
        val h = Harness(this)
        h.dispatch(SmsJob(messageId = "m1", projectId = "p1", to = "+251911234567", body = "hi", receivedAt = 0))
        runCurrent()

        h.component.inboundSync.receive("+251911234567", "late", sentAt = 1, receivedAt = GatewaySettings.REPLY_WINDOW_MS + 1, sim = 1)
        h.component.inboundSync.receive("+251911234567", "in time", sentAt = 2, receivedAt = GatewaySettings.REPLY_WINDOW_MS, sim = 1)

        assertEquals(listOf("in time"), h.connection.sent.filterIsInstance<DeviceFrame.Incoming>().map { it.body })
    }

    @Test
    fun `off keeps every received SMS on the phone, all sends every one`() = runTest {
        val h = Harness(this, GatewaySettings(inboxSync = InboxSync.OFF))
        h.dispatch(SmsJob(messageId = "m1", projectId = "p1", to = "+251911234567", body = "hi", receivedAt = 1_000))
        runCurrent()
        h.component.inboundSync.receive("+251911234567", "STOP", sentAt = 1, receivedAt = 2_000, sim = 1)
        assertTrue(h.connection.sent.none { it is DeviceFrame.Incoming })

        h.settingsRepo.update { it.copy(inboxSync = InboxSync.ALL) }
        h.component.inboundSync.receive("CBE", "Your code is 482913", sentAt = 2, receivedAt = 3_000, sim = 1)
        assertEquals(listOf("Your code is 482913"), h.connection.sent.filterIsInstance<DeviceFrame.Incoming>().map { it.body })
    }

    @Test
    fun `the server sees the inbox mode but no patch changes it`() = runTest {
        val h = Harness(this, GatewaySettings(inboxSync = InboxSync.OFF))
        h.connection.frames.tryEmit(ServerFrame.ApplySettings(RemoteSettingsPatch(keepAlive = false)))
        runCurrent()
        assertEquals(InboxSync.OFF, h.settingsRepo.current().inboxSync)
        assertEquals(
            app.relay.protocol.InboxSyncMode.OFF,
            h.connection.sent.filterIsInstance<DeviceFrame.Settings>().last().settings.inboxSync,
        )
    }

    @Test
    fun `a build that can't receive SMS says so, and reports the inbox as off`() = runTest {
        val h = Harness(this, GatewaySettings(inboxSync = InboxSync.REPLIES), receivesSms = false)
        val hello = h.connection.sent.first() as DeviceFrame.Hello
        assertEquals(false, hello.settings?.receivesSms)
        assertEquals(app.relay.protocol.InboxSyncMode.OFF, hello.settings?.inboxSync)
    }
}
