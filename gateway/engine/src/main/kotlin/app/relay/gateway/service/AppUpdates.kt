package app.relay.gateway.service

import app.relay.gateway.engine.UpdateSync
import app.relay.gateway.model.UpdateMode
import app.relay.gateway.port.AppInstaller
import app.relay.gateway.port.Clock
import app.relay.gateway.port.DownloadedApk
import app.relay.gateway.port.MessageRepository
import app.relay.gateway.port.RelayApi
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.UpdateNotifier
import app.relay.gateway.port.UpdateStore
import app.relay.protocol.AppUpdate
import app.relay.protocol.UpdateStep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class UpdatePhase { AVAILABLE, DOWNLOADING, READY, INSTALLING, FAILED }

/** The update this phone is on its way to, for the banner, the sheet and Settings. */
data class UpdateStatus(
    val update: AppUpdate,
    val phase: UpdatePhase,
    /** 0..1 while downloading. */
    val progress: Float = 0f,
    val error: String? = null,
    /** "Later" was tapped: no banner or notification until it wears off. Never for a critical update. */
    val snoozed: Boolean = false,
)

/** The answer to "Check for updates". */
enum class CheckResult { UPDATE, UP_TO_DATE, OFFLINE }

/** The new version's notes, shown once it runs. */
data class WhatsNew(val versionName: String, val notes: String)

/**
 * Keeps the app current. The server offers the build for this phone ([UpdateSync]); how it gets
 * installed is the phone's update mode:
 * - [UpdateMode.ASK]: a notification and a banner; the user taps Install (or Later, for a day).
 * - [UpdateMode.AUTO]: download, wait until nothing is waiting to send, install (silently where
 *   Android allows; otherwise a "tap to install" notification).
 * Before installing, the gateway goes offline so the server holds new messages; the new version
 * (or a failed install) puts it back as it was.
 */
class AppUpdates(
    private val sync: UpdateSync,
    private val api: RelayApi,
    private val installer: AppInstaller,
    private val notifier: UpdateNotifier,
    private val store: UpdateStore,
    private val settings: SettingsRepository,
    private val messages: MessageRepository,
    private val clock: Clock,
    private val scope: CoroutineScope,
    /** The running app. */
    private val versionName: String,
    private val versionCode: Int?,
    /** False in the send-only build, which only ever installs the send-only .apk. */
    private val receivesSms: Boolean,
    private val idleCheckMs: Long = 60_000,
    private val snoozeMs: Long = 24 * 60 * 60_000L,
) {
    private val _status = MutableStateFlow<UpdateStatus?>(null)
    private val _whatsNew = MutableStateFlow<WhatsNew?>(null)
    /** One download or install at a time, whoever asked (the mode, or a tap). */
    private val work = Mutex()
    private var apk: DownloadedApk? = null
    private var job: Job? = null

    val status: StateFlow<UpdateStatus?> = _status.asStateFlow()
    val whatsNew: StateFlow<WhatsNew?> = _whatsNew.asStateFlow()

    /** For the app's lifetime: follows the server's offer and the update mode. Idempotent. */
    fun start() {
        if (job != null) return
        job = scope.launch {
            store.whatsNew()?.takeIf { it.first == versionName }?.let { _whatsNew.value = WhatsNew(it.first, it.second) }
            val offers = sync.offer.map { it?.takeIf(::applies) }.distinctUntilChanged { a, b -> a?.versionName == b?.versionName && a?.critical == b?.critical }
            val modes = settings.settings.map { it.updateMode }.distinctUntilChanged()
            combine(offers, modes, ::Pair).collectLatest { (offer, mode) -> follow(offer, mode) }
        }
    }

    /** The user taps Install (or Retry): download if needed, then install now, idle or not. */
    fun install() {
        val update = _status.value?.update ?: return
        scope.launch { work.withLock { downloadAndInstall(update) } }
    }

    /** "Later": quiet for a day. A critical update can't be put off. */
    fun later() {
        val s = _status.value ?: return
        if (s.update.critical) return
        scope.launch {
            store.snooze(s.update.versionName, clock.now() + snoozeMs)
            _status.update { it?.copy(snoozed = true) }
            notifier.clear()
        }
    }

    /** "Check for updates": asks the server now, whether or not the gateway is connected. */
    suspend fun check(): CheckResult {
        val found = try {
            api.checkUpdate(versionName, versionCode, sendOnly = !receivesSms)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return CheckResult.OFFLINE
        }
        sync.offered(found)
        if (found == null || !applies(found)) return CheckResult.UP_TO_DATE
        // Asked for it: a snoozed update shows again.
        _status.update { s -> s?.takeIf { it.update.versionName == found.versionName }?.copy(snoozed = false) ?: s }
        return CheckResult.UPDATE
    }

    /** The installer said no (or the user cancelled it): report it and put the gateway back. */
    suspend fun onInstallFailed(reason: String) {
        val update = _status.value?.update ?: store.pending()?.let { p -> sync.offer.value?.takeIf { it.versionName == p.versionName } }
        restoreGateway()
        if (update == null) return
        apk = null
        _status.value = UpdateStatus(update, UpdatePhase.FAILED, error = reason)
        sync.report(update.versionName, UpdateStep.FAILED, reason)
        notifier.failed(update, reason)
    }

    /**
     * After the app was replaced (or on any start): if an update was under way, put the gateway back
     * as it was, and if this is the new version, show its notes once. True when one was under way.
     */
    suspend fun finishUpdate(): Boolean {
        val pending = store.pending() ?: return false
        if (pending.versionName == versionName && !pending.notes.isNullOrBlank()) {
            store.setWhatsNew(pending.versionName, pending.notes)
            _whatsNew.value = WhatsNew(pending.versionName, pending.notes)
        }
        restoreGateway()
        return true
    }

    fun dismissWhatsNew() {
        _whatsNew.value = null
        scope.launch { store.setWhatsNew(null, null) }
    }

    private suspend fun follow(offer: AppUpdate?, mode: UpdateMode) {
        if (offer == null) {
            if (_status.value?.phase != UpdatePhase.INSTALLING) {
                _status.value = null
                notifier.clear()
            }
            return
        }
        if (_status.value?.update?.versionName != offer.versionName) {
            apk = null
            _status.value = UpdateStatus(offer, UpdatePhase.AVAILABLE, snoozed = snoozed(offer))
            sync.report(offer.versionName, UpdateStep.AVAILABLE)
        } else {
            _status.update { it?.copy(update = offer) }
        }
        val status = _status.value ?: return
        when (mode) {
            UpdateMode.ASK -> if (!status.snoozed && status.phase == UpdatePhase.AVAILABLE) notifier.available(offer)
            UpdateMode.AUTO -> {
                // A failure waits for a tap (or the next version) rather than retrying in a loop.
                if (status.phase == UpdatePhase.FAILED || status.phase == UpdatePhase.INSTALLING) return
                work.withLock {
                    if (download(offer) == null) return
                    if (!installer.installsSilently()) {
                        notifier.ready(offer)
                        return
                    }
                    awaitIdle()
                    install(offer)
                }
            }
        }
    }

    private suspend fun downloadAndInstall(update: AppUpdate) {
        if (download(update) != null) install(update)
    }

    /** The checked .apk, downloading it first if needed; null (reported) when that fails. */
    private suspend fun download(update: AppUpdate): DownloadedApk? {
        apk?.takeIf { it.versionName == update.versionName }?.let { return it }
        _status.value = UpdateStatus(update, UpdatePhase.DOWNLOADING)
        sync.report(update.versionName, UpdateStep.DOWNLOADING)
        return try {
            installer.download(update) { p -> _status.update { it?.copy(progress = p) } }.also {
                apk = it
                _status.value = UpdateStatus(update, UpdatePhase.READY, progress = 1f)
                sync.report(update.versionName, UpdateStep.READY)
            }
        } catch (e: CancellationException) {
            // The offer or the mode changed meanwhile: start over from "available".
            _status.value = UpdateStatus(update, UpdatePhase.AVAILABLE)
            throw e
        } catch (e: Exception) {
            val reason = e.message ?: "Download failed"
            _status.value = UpdateStatus(update, UpdatePhase.FAILED, error = reason)
            sync.report(update.versionName, UpdateStep.FAILED, reason)
            notifier.failed(update, reason)
            null
        }
    }

    private suspend fun install(update: AppUpdate) {
        val file = apk ?: return
        _status.value = UpdateStatus(update, UpdatePhase.INSTALLING, progress = 1f)
        sync.report(update.versionName, UpdateStep.INSTALLING)
        notifier.clear()
        // The server holds new messages while the app is replaced; the new version resumes.
        store.installing(update.versionName, update.notes, settings.current().gatewayOnline)
        settings.update { it.copy(gatewayOnline = false) }
        try {
            installer.install(file)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            onInstallFailed(e.message ?: "Install failed")
        }
    }

    /** Nothing waiting to be sent: installing interrupts no one. */
    private suspend fun awaitIdle() {
        while (messages.observeStatusCounts(since = 0).first().pending > 0) delay(idleCheckMs)
    }

    private suspend fun restoreGateway() {
        val pending = store.pending() ?: return
        settings.update { it.copy(gatewayOnline = pending.wasOnline) }
        store.clearPending()
    }

    private suspend fun snoozed(offer: AppUpdate): Boolean {
        if (offer.critical) return false
        val (version, until) = store.snoozed() ?: return false
        return version == offer.versionName && until > clock.now()
    }

    /** Newer than this app, and the same build (two-way or send-only) as this app. */
    private fun applies(offer: AppUpdate): Boolean {
        val code = offer.versionCode
        val newer = if (code != null && versionCode != null) code > versionCode else offer.versionName != versionName
        return newer && offer.url.trimEnd('/').endsWith("/send-only") == !receivesSms
    }
}
