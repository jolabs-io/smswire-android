package app.relay.android.startup

import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.service.DeviceSetup
import app.relay.gateway.service.ProjectService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Starts and stops the gateway's foreground service. */
interface GatewayServiceController {
    fun start()
    fun stop()
}

/**
 * Runs the gateway while the phone is paired with a workspace: on pairing it syncs the
 * projects and starts the service; on unpairing (by the user, or revoked on the web) it
 * stops it, and the app shows the pairing screen. Honours "keep alive in background".
 */
class StartupCoordinator(
    private val setup: DeviceSetup,
    private val projects: ProjectService,
    private val settings: SettingsRepository,
    private val service: GatewayServiceController,
    private val scope: CoroutineScope,
    private val retryDelaysMs: List<Long> = listOf(5_000, 15_000, 60_000),
) {
    private var job: Job? = null
    @Volatile private var paired = false

    fun start() {
        if (job != null) return
        job = scope.launch {
            setup.identity.map { it?.deviceId }.distinctUntilChanged().collect { deviceId ->
                paired = deviceId != null
                if (deviceId == null) {
                    service.stop()
                } else {
                    service.start()
                    syncProjects()
                }
            }
        }
    }

    fun onForeground() {
        if (paired) service.start()
    }

    fun onBackground() {
        scope.launch { if (!settings.current().keepAlive) service.stop() }
    }

    /** The project list is a cache; keep trying while offline (the socket also refreshes it on change). */
    private suspend fun syncProjects() {
        var attempt = 0
        while (paired) {
            if (runCatching { projects.refresh() }.isSuccess) return
            delay(retryDelaysMs[attempt.coerceAtMost(retryDelaysMs.lastIndex)])
            attempt++
        }
    }
}
