package app.relay.gateway.engine

import app.relay.gateway.port.Clock
import app.relay.gateway.port.RelayConnection
import app.relay.protocol.AppUpdate
import app.relay.protocol.DeviceFrame
import app.relay.protocol.UpdateStep
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** The update the server offers this phone (app_update), and the steps the phone reports back. */
class UpdateSync(private val connection: RelayConnection, private val clock: Clock) {
    private val _offer = MutableStateFlow<AppUpdate?>(null)

    /** The newest build for this phone; null when it's up to date (or nothing was said yet). */
    val offer: StateFlow<AppUpdate?> = _offer.asStateFlow()

    fun offered(release: AppUpdate?) {
        _offer.value = release
    }

    /** Best effort: offline, the dashboard just shows the last step it heard. */
    fun report(versionName: String, step: UpdateStep, error: String? = null) {
        connection.send(DeviceFrame.UpdateState(versionName, step, error, clock.now()))
    }
}
