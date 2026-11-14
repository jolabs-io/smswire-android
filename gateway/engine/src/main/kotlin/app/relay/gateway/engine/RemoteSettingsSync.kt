package app.relay.gateway.engine

import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.SimPreference
import app.relay.gateway.model.UpdateMode
import app.relay.gateway.port.RelayConnection
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.DefaultSim
import app.relay.protocol.DeviceFrame
import app.relay.protocol.InboxSyncMode
import app.relay.protocol.RemoteSettings
import app.relay.protocol.RemoteSettingsPatch
import app.relay.protocol.UpdateMode as WireUpdateMode
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map

/**
 * Keeps the server's copy of this phone's settings current and applies the ones changed on
 * the web. The server URL is never part of it: changing it remotely would cut the phone off.
 */
class RemoteSettingsSync(
    private val settings: SettingsRepository,
    private val connection: RelayConnection,
    /** False in a build that can't receive SMS: the inbox mode is reported as off, whatever is stored. */
    private val receivesSms: Boolean = true,
) {
    suspend fun snapshot(): RemoteSettings = settings.current().toRemote(receivesSms)

    suspend fun apply(patch: RemoteSettingsPatch) {
        val before = settings.current()
        settings.update { it.with(patch) }
        val after = settings.current()
        // The server holds or releases its queue on this frame, as when the switch is flipped here.
        if (after.gatewayOnline != before.gatewayOnline) connection.send(DeviceFrame.GatewayState(after.gatewayOnline))
        // A change is reported by [run]; confirm a no-op too so the server stops waiting on it.
        if (after == before) connection.send(DeviceFrame.Settings(after.toRemote(receivesSms)))
    }

    /** Reports every change after the hello, wherever it was made. */
    suspend fun run() {
        settings.settings.map { it.toRemote(receivesSms) }.distinctUntilChanged().drop(1).collect {
            connection.send(DeviceFrame.Settings(it))
        }
    }
}

private fun GatewaySettings.with(p: RemoteSettingsPatch) = copy(
    gatewayOnline = p.gatewayOnline ?: gatewayOnline,
    defaultSim = p.defaultSim?.toPreference() ?: defaultSim,
    maxRatePerMinute = p.maxRatePerMinute?.coerceIn(GatewaySettings.MIN_RATE, GatewaySettings.MAX_RATE) ?: maxRatePerMinute,
    maxRetries = p.maxRetries?.coerceIn(0, GatewaySettings.MAX_RETRIES) ?: maxRetries,
    requestDeliveryReports = p.requestDeliveryReports ?: requestDeliveryReports,
    keepAlive = p.keepAlive ?: keepAlive,
    startOnBoot = p.startOnBoot ?: startOnBoot,
    onlySendWhileCharging = p.onlySendWhileCharging ?: onlySendWhileCharging,
    updateMode = p.updateMode?.toModel() ?: updateMode,
)

private fun GatewaySettings.toRemote(receivesSms: Boolean) = RemoteSettings(
    gatewayOnline = gatewayOnline,
    defaultSim = defaultSim.toWire(),
    maxRatePerMinute = maxRatePerMinute,
    maxRetries = maxRetries,
    requestDeliveryReports = requestDeliveryReports,
    keepAlive = keepAlive,
    startOnBoot = startOnBoot,
    onlySendWhileCharging = onlySendWhileCharging,
    inboxSync = if (receivesSms) inboxSync.toWire() else InboxSyncMode.OFF,
    receivesSms = receivesSms,
    updateMode = updateMode.toWire(),
)

private fun UpdateMode.toWire() = when (this) {
    UpdateMode.ASK -> WireUpdateMode.ASK
    UpdateMode.AUTO -> WireUpdateMode.AUTO
}

private fun WireUpdateMode.toModel() = when (this) {
    WireUpdateMode.ASK -> UpdateMode.ASK
    WireUpdateMode.AUTO -> UpdateMode.AUTO
}

private fun InboxSync.toWire() = when (this) {
    InboxSync.REPLIES -> InboxSyncMode.REPLIES
    InboxSync.ALL -> InboxSyncMode.ALL
    InboxSync.OFF -> InboxSyncMode.OFF
}

private fun SimPreference.toWire() = when (this) {
    SimPreference.SIM1 -> DefaultSim.SIM1
    SimPreference.SIM2 -> DefaultSim.SIM2
    SimPreference.ALTERNATE -> DefaultSim.ALTERNATE
}

private fun DefaultSim.toPreference() = when (this) {
    DefaultSim.SIM1 -> SimPreference.SIM1
    DefaultSim.SIM2 -> SimPreference.SIM2
    DefaultSim.ALTERNATE -> SimPreference.ALTERNATE
}
