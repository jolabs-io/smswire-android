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
import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.gateway.model.ReceiveRules
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.ConnectionState
import app.relay.gateway.port.PowerState
import app.relay.gateway.port.SmsEvent
import app.relay.protocol.BatchDto
import app.relay.protocol.BatchSource
import app.relay.protocol.BatchStatus
import app.relay.protocol.DefaultSim
import app.relay.protocol.AppUpdate
import app.relay.protocol.DeviceFrame
import app.relay.protocol.MessageStatus
import app.relay.protocol.MessageType
import app.relay.protocol.RemoteSettings
import app.relay.protocol.RemoteSettingsPatch
import app.relay.protocol.SimInfo
import app.relay.protocol.UpdateMode
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
    private class Harness(
        scope: TestScope,
        settings: GatewaySettings = GatewaySettings(),
        receivesSms: Boolean = true,
        val sims: FakeSims = FakeSims(),
    ) {
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
                sims = sims,
                power = power,
                clock = FakeClock { scope.testScheduler.currentTime },
                appVersion = "test",
                receivesSms = receivesSms,
                appVersionCode = 7,
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

        /** History: a message to [to] that went out from [sim] earlier, as if from before a restart. */
        suspend fun texted(to: String, sim: Int) {
            val id = "old-${messages.all.size}"
            messages.insertIfAbsent(
                Message(id, "p1", to, "hi", MessageStatus.DELIVERED, MessageTimeline(0, 0, sentAt = 0, deliveredAt = 0), sim = sim, reportedStatus = MessageStatus.DELIVERED),
            )
        }

        /** Message id to the SIM it went out from. */
        fun sentFrom() = radio.sent.associate { it.second.messageId to it.second.simSlot }

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
                    parallelSims = false,
                    receivesSms = true,
                    joinKeywords = true,
                    receiveRules = true,
                ),
                appVersionCode = 7,
            ),
            h.connection.sent.first(),
        )
    }

    @Test
    fun `the update mode can be changed from the web`() = runTest {
        val h = Harness(this)
        h.connection.frames.emit(ServerFrame.ApplySettings(RemoteSettingsPatch(updateMode = UpdateMode.AUTO)))
        runCurrent()
        assertEquals(app.relay.gateway.model.UpdateMode.AUTO, h.settingsRepo.current().updateMode)
        assertEquals(UpdateMode.AUTO, h.connection.sent.filterIsInstance<DeviceFrame.Settings>().last().settings.updateMode)
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
    fun `with its SIMs used at once, two messages go out together`() = runTest {
        val h = Harness(this, GatewaySettings(parallelSims = true))
        h.dispatch(h.job("001"), h.job("002"), h.job("003"), h.job("004"))
        advanceTimeBy(1_000)
        // One loop per SIM, so two leave at once instead of 2s apart. Which two is immaterial:
        // they are the same age and priority.
        assertEquals(2, h.radio.sent.size)
        assertEquals(setOf(1, 2), h.radio.sent.map { it.second.simSlot }.toSet())

        // Each SIM keeps its own pace, and nothing is left behind.
        advanceTimeBy(2_000)
        assertEquals(4, h.radio.sent.size)
        assertEquals(setOf(1, 2), h.radio.sent.drop(2).map { it.second.simSlot }.toSet())
        assertEquals(setOf("001", "002", "003", "004"), h.radio.sent.map { it.second.messageId }.toSet())
        // Two at a time, so four take one interval rather than three.
        assertEquals(listOf(0L, 0L, 2_000L, 2_000L), h.radio.sent.map { it.first })
    }

    @Test
    fun `one at a time by default, from the SIM the phone prefers`() = runTest {
        val h = Harness(this)
        h.dispatch(h.job("001"), h.job("002"))
        advanceTimeBy(1_000)
        assertEquals(1, h.radio.sent.size)
        assertEquals(1, h.radio.sent.single().second.simSlot)
    }

    @Test
    fun `a message that names a SIM is only sent from that one`() = runTest {
        val h = Harness(this, GatewaySettings(parallelSims = true))
        h.dispatch(h.job("001").copy(sim = 2), h.job("002").copy(sim = 2))
        advanceTimeBy(1_000)
        // Both are for SIM 2, so only its loop may take them: one goes, the other waits its turn.
        assertEquals(1, h.radio.sent.size)
        assertEquals(2, h.radio.sent.single().second.simSlot)
        advanceTimeBy(2_000)
        assertEquals(listOf(2, 2), h.radio.sent.map { it.second.simSlot })
    }

    @Test
    fun `taking turns, a person stays on the SIM that texted them last`() = runTest {
        val h = Harness(this, GatewaySettings(defaultSim = SimPreference.ALTERNATE))
        h.dispatch(h.job("001"), h.job("002"))
        advanceTimeBy(3_000)
        // 003 is for the person 002 reached on SIM 2; 004 is someone new and takes the next turn.
        h.dispatch(h.job("003").copy(to = h.job("002").to), h.job("004"))
        advanceTimeBy(5_000)
        assertEquals(mapOf("001" to 1, "002" to 2, "003" to 2, "004" to 1), h.sentFrom())
    }

    @Test
    fun `after a restart the phone still keeps people on their SIM`() = runTest {
        val h = Harness(this, GatewaySettings(defaultSim = SimPreference.ALTERNATE))
        h.texted(h.job("001").to, sim = 2)
        h.dispatch(h.job("001"), h.job("002"))
        advanceTimeBy(3_000)
        // A fresh engine's first turn is SIM 1; the history keeps 001's person on SIM 2.
        assertEquals(mapOf("001" to 2, "002" to 1), h.sentFrom())
    }

    @Test
    fun `with every SIM at once, each loop leaves people on their own SIM`() = runTest {
        val h = Harness(this, GatewaySettings(defaultSim = SimPreference.ALTERNATE, parallelSims = true))
        h.texted(h.job("001").to, sim = 2)
        h.texted(h.job("002").to, sim = 1)
        h.dispatch(h.job("001"), h.job("002"))
        advanceTimeBy(1_000)
        // SIM 1's loop wakes first and would take 001, the oldest; it leaves it to SIM 2.
        assertEquals(mapOf("001" to 2, "002" to 1), h.sentFrom())
    }

    @Test
    fun `with every SIM at once, two messages to someone new go out from one SIM`() = runTest {
        val h = Harness(this, GatewaySettings(defaultSim = SimPreference.ALTERNATE, parallelSims = true))
        // Each SIM sends one now and holds the next until its 2s slot, so neither is free.
        h.dispatch(h.job("001"), h.job("002"), h.job("003"), h.job("004"))
        advanceTimeBy(100)
        val person = "+251911234567"
        h.dispatch(h.job("005").copy(to = person), h.job("006").copy(to = person))
        advanceTimeBy(6_500)
        // At 2s one loop takes 005 and holds it for its next slot, before the history can show it;
        // the other leaves 006 to that loop rather than send it from its own number alongside.
        val toPerson = h.radio.sent.filter { it.second.to == person }
        assertEquals(1, toPerson.map { it.second.simSlot }.distinct().size)
        assertEquals(listOf(4_000L, 6_000L), toPerson.map { it.first })
    }

    @Test
    fun `with every SIM at once, a person's SIM without a loop doesn't strand their message`() = runTest {
        val sims = FakeSims(listOf(1))
        val h = Harness(this, GatewaySettings(defaultSim = SimPreference.ALTERNATE, parallelSims = true), sims = sims)
        // SIM 2 goes in after the loops started: it has no loop until the settings change or a restart.
        sims.slots = listOf(1, 2)
        h.texted(h.job("001").to, sim = 2)
        h.dispatch(h.job("001"))
        advanceTimeBy(1_000)
        assertEquals(mapOf("001" to 1), h.sentFrom())
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
    fun `a reconnect fetches the projects again, for changes made while offline`() = runTest {
        val h = Harness(this)
        h.api.projects = listOf(h.api.project("p9", "Changed while offline"))
        h.connection.state.value = ConnectionState.Disconnected
        h.connection.state.value = ConnectionState.Connected(1)
        runCurrent()
        assertEquals("Changed while offline", h.projects.find("p9")?.name)
    }

    @Test
    fun `the server's update offer reaches the updater`() = runTest {
        val h = Harness(this)
        val release = AppUpdate("1.4.0", 310, 3_000_000, null, "/app/download/android/1.4.0", null, critical = false, publishedAt = 0)
        h.connection.frames.tryEmit(ServerFrame.AppUpdateOffer(release))
        runCurrent()
        assertEquals(release, h.component.updateSync.offer.value)
        h.connection.frames.tryEmit(ServerFrame.AppUpdateOffer(null))
        runCurrent()
        assertEquals(null, h.component.updateSync.offer.value)
    }

    @Test
    fun `a revoked token forgets the pairing`() = runTest {
        val h = Harness(this)
        h.connection.state.value = ConnectionState.Revoked
        runCurrent()
        assertEquals(null, h.identity.load())
    }

    @Test
    fun `a revoke of the token it connected with leaves a newer pairing alone`() = runTest {
        val h = Harness(this)
        runCurrent()
        // Switched to another workspace before the old socket's refusal came in.
        h.identity.save(DeviceIdentity("dev_2", "t2", "Pixel", "Pixel 8"))
        h.connection.state.value = ConnectionState.Revoked
        runCurrent()
        assertEquals("dev_2", h.identity.load()?.deviceId)
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
    fun `replies also sends a join keyword from a number the phone never texted, and nothing else from it`() = runTest {
        val h = Harness(this)
        h.connection.frames.emit(ServerFrame.JoinKeywords(listOf("A2", "እሺ")))
        runCurrent()
        val inbound = h.component.inboundSync
        inbound.receive("+251922000000", " a 2! ", sentAt = 1, receivedAt = 2_000, sim = 1)
        inbound.receive("0922 000 001", "እሺ", sentAt = 2, receivedAt = 2_000, sim = 1)
        inbound.receive("+251922000000", "A2 please", sentAt = 3, receivedAt = 2_000, sim = 1)
        inbound.receive("+251922000000", "hey, it's me", sentAt = 4, receivedAt = 2_000, sim = 1)
        // A bank or a short code never goes, even with a keyword's text.
        inbound.receive("CBE", "A2", sentAt = 5, receivedAt = 2_000, sim = 1)
        inbound.receive("8994", "A2", sentAt = 6, receivedAt = 2_000, sim = 1)
        assertEquals(listOf(" a 2! ", "እሺ"), h.connection.sent.filterIsInstance<DeviceFrame.Incoming>().map { it.body })

        // The server's next list replaces this one.
        h.connection.frames.emit(ServerFrame.JoinKeywords(emptyList()))
        runCurrent()
        inbound.receive("+251922000002", "A2", sentAt = 7, receivedAt = 3_000, sim = 1)
        assertEquals(2, h.connection.sent.filterIsInstance<DeviceFrame.Incoming>().size)
    }

    @Test
    fun `off keeps join keywords on the phone too`() = runTest {
        val h = Harness(this, GatewaySettings(inboxSync = InboxSync.OFF))
        h.connection.frames.emit(ServerFrame.JoinKeywords(listOf("A2")))
        runCurrent()
        h.component.inboundSync.receive("+251922000000", "A2", sentAt = 1, receivedAt = 2_000, sim = 1)
        assertTrue(h.connection.sent.none { it is DeviceFrame.Incoming })
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

    /** The phone's projects, with their receive rules as the web set them. */
    private suspend fun Harness.rules(vararg projects: Pair<String, ReceiveRules>, sims: Map<String, Int> = emptyMap()) {
        val list = projects.map { (id, rules) -> api.project(id, id).copy(receive = rules, sim = sims[id]) }
        api.projects = list
        this.projects.replaceAll(list)
    }

    private fun Harness.incoming() = connection.sent.filterIsInstance<DeviceFrame.Incoming>()

    @Test
    fun `with rules from the web, a listed sender goes however it's written, and strangers don't`() = runTest {
        val h = Harness(this)
        h.rules("p1" to ReceiveRules(replies = false, senders = listOf("cbe", "127", "0911 234 567")))
        val inbound = h.component.inboundSync
        inbound.receive("CBE", "Credited", sentAt = 1, receivedAt = 2_000, sim = 1)
        inbound.receive("127", "Paid", sentAt = 2, receivedAt = 2_000, sim = 1)
        inbound.receive("+251911234567", "Hi", sentAt = 3, receivedAt = 2_000, sim = 1)
        inbound.receive("Safaricom", "Promo", sentAt = 4, receivedAt = 2_000, sim = 1)
        inbound.receive("+251922000000", "Hello", sentAt = 5, receivedAt = 2_000, sim = 1)
        assertEquals(listOf("Credited", "Paid", "Hi"), h.incoming().map { it.body })
    }

    @Test
    fun `a reply goes for the project that texted them, which the server is told`() = runTest {
        val h = Harness(this)
        h.rules("p1" to ReceiveRules(), "p2" to ReceiveRules(replies = false))
        h.dispatch(SmsJob(messageId = "m1", projectId = "p1", to = "+251911234567", body = "Your order shipped", receivedAt = 1_000))
        h.dispatch(SmsJob(messageId = "m2", projectId = "p2", to = "+251922000000", body = "Your code", receivedAt = 1_000))
        runCurrent()

        h.component.inboundSync.receive("0911 234 567", "Thanks", sentAt = 1, receivedAt = 2_000, sim = 1)
        h.component.inboundSync.receive("+251922000000", "Thanks", sentAt = 2, receivedAt = 2_000, sim = 1)
        // Replies off still lets STOP through: opt-outs keep working.
        h.component.inboundSync.receive("+251922000000", "Stop", sentAt = 3, receivedAt = 2_000, sim = 1)
        assertEquals(listOf("0911 234 567" to listOf("p1"), "+251922000000" to listOf("p2")), h.incoming().map { it.from to it.texted })
        assertEquals("Stop", h.incoming().last().body)
    }

    @Test
    fun `a project's every SMS needs the phone set to All, and stays on its SIM`() = runTest {
        val h = Harness(this)
        h.rules("p1" to ReceiveRules(all = true), sims = mapOf("p1" to 2))
        val inbound = h.component.inboundSync
        inbound.receive("CBE", "Code 1", sentAt = 1, receivedAt = 2_000, sim = 2)
        assertTrue(h.incoming().isEmpty())

        h.settingsRepo.update { it.copy(inboxSync = InboxSync.ALL) }
        inbound.receive("CBE", "Code 2", sentAt = 2, receivedAt = 2_000, sim = 1)
        inbound.receive("CBE", "Code 3", sentAt = 3, receivedAt = 2_000, sim = 2)
        assertEquals(listOf("Code 3" to emptyList<String>()), h.incoming().map { it.body to it.texted })
    }

    @Test
    fun `join keywords get through whatever the projects have set, and off still keeps everything`() = runTest {
        val h = Harness(this)
        h.rules("p1" to ReceiveRules(replies = false))
        h.connection.frames.emit(ServerFrame.JoinKeywords(listOf("A2")))
        runCurrent()
        h.component.inboundSync.receive("+251922000000", "a2", sentAt = 1, receivedAt = 2_000, sim = 1)
        h.component.inboundSync.receive("CBE", "A2", sentAt = 2, receivedAt = 2_000, sim = 1)
        assertEquals(listOf("a2"), h.incoming().map { it.body })

        h.settingsRepo.update { it.copy(inboxSync = InboxSync.OFF) }
        h.component.inboundSync.receive("+251922000001", "a2", sentAt = 3, receivedAt = 2_000, sim = 1)
        assertEquals(1, h.incoming().size)
    }

    @Test
    fun `until the server sends rules, the phone keeps its old behaviour`() = runTest {
        val h = Harness(this, GatewaySettings(inboxSync = InboxSync.ALL))
        h.projects.replaceAll(listOf(h.api.project("p1", "Shop"), h.api.project("p2", "Bank").copy(receive = ReceiveRules(replies = false))))
        h.component.inboundSync.receive("CBE", "Code", sentAt = 1, receivedAt = 2_000, sim = 1)
        assertEquals(listOf("Code"), h.incoming().map { it.body })
    }

    @Test
    fun `what a project receives can be changed here, on the server first`() = runTest {
        val h = Harness(this)
        h.rules("p1" to ReceiveRules())
        val rules = ReceiveRules(replies = false, all = true, senders = listOf("127"))
        h.component.projectService.updateReceive("p1", rules)
        assertEquals(listOf("p1" to rules), h.api.savedRules)
        assertEquals(rules, h.projects.find("p1")?.receive)

        // Offline: nothing changes here, and the caller hears about it.
        h.api.updateReceiveFails = true
        assertTrue(runCatching { h.component.projectService.updateReceive("p1", ReceiveRules()) }.isFailure)
        assertEquals(rules, h.projects.find("p1")?.receive)
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
