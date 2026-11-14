package app.relay.gateway.service

import app.relay.gateway.FakeClock
import app.relay.gateway.FakeConnection
import app.relay.gateway.FakeRelayApi
import app.relay.gateway.data.memory.InMemoryMessageRepository
import app.relay.gateway.data.memory.InMemorySettingsRepository
import app.relay.gateway.data.memory.InMemoryUpdateStore
import app.relay.gateway.engine.UpdateSync
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.Message
import app.relay.gateway.model.MessageTimeline
import app.relay.gateway.model.UpdateMode
import app.relay.gateway.port.AppInstaller
import app.relay.gateway.port.ConnectionState
import app.relay.gateway.port.DownloadedApk
import app.relay.gateway.port.UpdateNotifier
import app.relay.protocol.AppUpdate
import app.relay.protocol.DeviceFrame
import app.relay.protocol.MessageStatus
import app.relay.protocol.UpdateStep
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AppUpdatesTest {
    private class FakeInstaller(var silent: Boolean = true) : AppInstaller {
        val downloads = mutableListOf<String>()
        val installs = mutableListOf<String>()
        var downloadFails: String? = null
        var installFails: String? = null

        override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): DownloadedApk {
            downloadFails?.let { throw IllegalStateException(it) }
            onProgress(0.5f)
            downloads += update.url
            return DownloadedApk(update.versionName, "/cache/${update.versionName}.apk")
        }

        override suspend fun install(apk: DownloadedApk) {
            installFails?.let { throw IllegalStateException(it) }
            installs += apk.versionName
        }

        override fun installsSilently() = silent
    }

    private class FakeNotifier : UpdateNotifier {
        val shown = mutableListOf<String>()
        override fun available(update: AppUpdate) {
            shown += "available ${update.versionName}"
        }
        override fun ready(update: AppUpdate) {
            shown += "ready ${update.versionName}"
        }
        override fun failed(update: AppUpdate, reason: String) {
            shown += "failed $reason"
        }
        override fun clear() {
            shown += "clear"
        }
    }

    private class Rig(scope: TestScope, mode: UpdateMode = UpdateMode.ASK, receivesSms: Boolean = true, val store: InMemoryUpdateStore = InMemoryUpdateStore()) {
        val connection = FakeConnection().apply { state.value = ConnectionState.Connected(0) }
        val clock = FakeClock { scope.testScheduler.currentTime }
        val sync = UpdateSync(connection, clock)
        val api = FakeRelayApi()
        val installer = FakeInstaller()
        val notifier = FakeNotifier()
        val settings = InMemorySettingsRepository(GatewaySettings(updateMode = mode, gatewayOnline = true))
        val messages = InMemoryMessageRepository()
        val updates = AppUpdates(
            sync, api, installer, notifier, store, settings, messages, clock, scope.backgroundScope as CoroutineScope,
            versionName = "1.3.0", versionCode = 300, receivesSms = receivesSms,
        ).also { it.start() }

        fun steps() = connection.sent.filterIsInstance<DeviceFrame.UpdateState>().map { it.state }
    }

    private fun release(version: String = "1.4.0", code: Int? = 310, critical: Boolean = false, sendOnly: Boolean = false) = AppUpdate(
        versionName = version,
        versionCode = code,
        size = 3_000_000,
        sha256 = "ab".repeat(32),
        url = "/app/download/android/$version" + if (sendOnly) "/send-only" else "",
        notes = "### New\n- Updates install themselves",
        critical = critical,
        publishedAt = 0,
    )

    @Test
    fun `asks first, then installs on a tap with the gateway paused`() = runTest {
        val r = Rig(this)
        r.sync.offered(release())
        runCurrent()
        assertEquals(UpdatePhase.AVAILABLE, r.updates.status.value?.phase)
        assertEquals(listOf("available 1.4.0"), r.notifier.shown)
        assertTrue(r.installer.downloads.isEmpty())

        r.updates.install()
        runCurrent()
        assertEquals(listOf("/app/download/android/1.4.0"), r.installer.downloads)
        assertEquals(listOf("1.4.0"), r.installer.installs)
        assertEquals(UpdatePhase.INSTALLING, r.updates.status.value?.phase)
        assertEquals(listOf(UpdateStep.AVAILABLE, UpdateStep.DOWNLOADING, UpdateStep.READY, UpdateStep.INSTALLING), r.steps())
        // The server holds messages meanwhile; the new version puts the gateway back.
        assertFalse(r.settings.current().gatewayOnline)
        assertEquals(true, r.store.pending()?.wasOnline)
    }

    @Test
    fun `later keeps quiet for a day, even after a restart, but not for a critical update`() = runTest {
        val store = InMemoryUpdateStore()
        val r = Rig(this, store = store)
        r.sync.offered(release())
        runCurrent()
        r.updates.later()
        runCurrent()
        assertEquals(true, r.updates.status.value?.snoozed)

        val again = Rig(this, store = store)
        again.sync.offered(release())
        runCurrent()
        assertEquals(true, again.updates.status.value?.snoozed)
        assertTrue(again.notifier.shown.isEmpty())

        val critical = Rig(this, store = store)
        critical.sync.offered(release(critical = true))
        runCurrent()
        critical.updates.later()
        runCurrent()
        assertEquals(false, critical.updates.status.value?.snoozed)
        assertEquals(listOf("available 1.4.0"), critical.notifier.shown)
    }

    @Test
    fun `automatic waits until nothing is waiting to send`() = runTest {
        val r = Rig(this, mode = UpdateMode.AUTO)
        r.messages.insertIfAbsent(Message("m1", "p1", "+251911000000", "Hi", MessageStatus.QUEUED, MessageTimeline(1, 1)))
        r.sync.offered(release())
        runCurrent()
        assertEquals(UpdatePhase.READY, r.updates.status.value?.phase)
        assertTrue(r.installer.installs.isEmpty())

        r.messages.update(r.messages.find("m1")!!.copy(status = MessageStatus.SENT))
        advanceTimeBy(61_000)
        runCurrent()
        assertEquals(listOf("1.4.0"), r.installer.installs)
    }

    @Test
    fun `automatic without silent installs downloads, then asks for a tap`() = runTest {
        val r = Rig(this, mode = UpdateMode.AUTO)
        r.installer.silent = false
        r.sync.offered(release())
        runCurrent()
        assertEquals(UpdatePhase.READY, r.updates.status.value?.phase)
        assertEquals(listOf("ready 1.4.0"), r.notifier.shown)
        assertTrue(r.installer.installs.isEmpty())
    }

    @Test
    fun `ignores what isn't newer, and the other build`() = runTest {
        val r = Rig(this)
        r.sync.offered(release(version = "1.3.0", code = 300))
        runCurrent()
        assertNull(r.updates.status.value)
        r.sync.offered(release(sendOnly = true))
        runCurrent()
        assertNull(r.updates.status.value)

        val sendOnly = Rig(this, receivesSms = false)
        sendOnly.sync.offered(release())
        runCurrent()
        assertNull(sendOnly.updates.status.value)
        sendOnly.sync.offered(release(sendOnly = true))
        runCurrent()
        assertEquals("1.4.0", sendOnly.updates.status.value?.update?.versionName)
    }

    @Test
    fun `a failed install is reported and the gateway comes back as it was`() = runTest {
        val r = Rig(this)
        r.installer.installFails = "INSTALL_FAILED_INSUFFICIENT_STORAGE"
        r.sync.offered(release())
        runCurrent()
        r.updates.install()
        runCurrent()
        assertEquals(UpdatePhase.FAILED, r.updates.status.value?.phase)
        assertEquals("INSTALL_FAILED_INSUFFICIENT_STORAGE", r.updates.status.value?.error)
        assertEquals(UpdateStep.FAILED, r.steps().last())
        assertTrue(r.settings.current().gatewayOnline)
        assertNull(r.store.pending())
        assertEquals("failed INSTALL_FAILED_INSUFFICIENT_STORAGE", r.notifier.shown.last())
    }

    @Test
    fun `a failed download waits for a tap instead of retrying`() = runTest {
        val r = Rig(this, mode = UpdateMode.AUTO)
        r.installer.downloadFails = "SHA-256 mismatch"
        r.sync.offered(release())
        runCurrent()
        assertEquals(UpdatePhase.FAILED, r.updates.status.value?.phase)
        advanceTimeBy(10 * 60_000)
        runCurrent()
        assertTrue(r.installer.downloads.isEmpty())

        r.installer.downloadFails = null
        r.updates.install()
        runCurrent()
        assertEquals(listOf("1.4.0"), r.installer.installs)
    }

    @Test
    fun `the new version resumes the gateway and shows what's new once`() = runTest {
        val store = InMemoryUpdateStore().apply { installing("1.3.0", "### New\n- Faster", wasOnline = true) }
        val r = Rig(this, store = store)
        r.settings.update { it.copy(gatewayOnline = false) }
        assertTrue(r.updates.finishUpdate())
        assertTrue(r.settings.current().gatewayOnline)
        assertEquals(WhatsNew("1.3.0", "### New\n- Faster"), r.updates.whatsNew.value)
        assertFalse(r.updates.finishUpdate())

        r.updates.dismissWhatsNew()
        runCurrent()
        assertNull(store.whatsNew())
    }

    @Test
    fun `check for updates asks the server for this build`() = runTest {
        val r = Rig(this, receivesSms = false)
        r.api.checkFails = true
        assertEquals(CheckResult.OFFLINE, r.updates.check())

        r.api.checkFails = false
        assertEquals(CheckResult.UP_TO_DATE, r.updates.check())
        r.api.update = release(sendOnly = true)
        assertEquals(CheckResult.UPDATE, r.updates.check())
        runCurrent()
        assertEquals("1.4.0", r.updates.status.value?.update?.versionName)
        assertEquals(Triple("1.3.0", 300, true), r.api.checks.last())
    }
}
