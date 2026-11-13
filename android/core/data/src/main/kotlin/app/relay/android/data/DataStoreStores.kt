package app.relay.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.relayPreferences: DataStore<Preferences> by preferencesDataStore(name = "relay")

class DataStoreSettingsRepository(context: Context) : SettingsRepository {
    private val store = context.applicationContext.relayPreferences

    override val settings: Flow<GatewaySettings> = store.data.map(::read).distinctUntilChanged()

    override suspend fun current(): GatewaySettings = settings.first()

    override suspend fun update(transform: (GatewaySettings) -> GatewaySettings) {
        store.edit { prefs -> write(prefs, transform(read(prefs))) }
    }

    private fun read(p: Preferences): GatewaySettings {
        val d = GatewaySettings()
        return GatewaySettings(
            gatewayOnline = p[ONLINE] ?: d.gatewayOnline,
            defaultSim = p[SIM]?.let { runCatching { SimPreference.valueOf(it) }.getOrNull() } ?: d.defaultSim,
            maxRatePerMinute = p[RATE] ?: d.maxRatePerMinute,
            maxRetries = p[RETRIES] ?: d.maxRetries,
            requestDeliveryReports = p[DLR] ?: d.requestDeliveryReports,
            keepAlive = p[KEEP_ALIVE] ?: d.keepAlive,
            startOnBoot = p[BOOT] ?: d.startOnBoot,
            onlySendWhileCharging = p[CHARGING] ?: d.onlySendWhileCharging,
            inboxSync = p[INBOX]?.let { runCatching { InboxSync.valueOf(it) }.getOrNull() } ?: d.inboxSync,
            serverUrl = p[SERVER] ?: d.serverUrl,
        )
    }

    private fun write(p: androidx.datastore.preferences.core.MutablePreferences, s: GatewaySettings) {
        p[ONLINE] = s.gatewayOnline
        p[SIM] = s.defaultSim.name
        p[RATE] = s.maxRatePerMinute
        p[RETRIES] = s.maxRetries
        p[DLR] = s.requestDeliveryReports
        p[KEEP_ALIVE] = s.keepAlive
        p[BOOT] = s.startOnBoot
        p[CHARGING] = s.onlySendWhileCharging
        p[INBOX] = s.inboxSync.name
        p[SERVER] = s.serverUrl
    }

    private companion object {
        val ONLINE = booleanPreferencesKey("gateway_online")
        val SIM = stringPreferencesKey("default_sim")
        val RATE = intPreferencesKey("max_rate")
        val RETRIES = intPreferencesKey("max_retries")
        val DLR = booleanPreferencesKey("delivery_reports")
        val KEEP_ALIVE = booleanPreferencesKey("keep_alive")
        val BOOT = booleanPreferencesKey("start_on_boot")
        val CHARGING = booleanPreferencesKey("only_charging")
        val INBOX = stringPreferencesKey("inbox_sync")
        val SERVER = stringPreferencesKey("server_url")
    }
}

/**
 * Device pairing with the relay. The token stays in app-private storage, which is excluded
 * from backups (see the app manifest).
 */
class DataStoreDeviceIdentityStore(context: Context) : DeviceIdentityStore {
    private val store = context.applicationContext.relayPreferences

    override val identity: Flow<DeviceIdentity?> = store.data.map(::read).distinctUntilChanged()

    override suspend fun load(): DeviceIdentity? = identity.first()

    override suspend fun save(identity: DeviceIdentity) {
        store.edit {
            it[DEVICE_ID] = identity.deviceId
            it[TOKEN] = identity.token
            it[NAME] = identity.name
            it[MODEL] = identity.model
            it[WORKSPACE] = identity.workspaceName
        }
    }

    override suspend fun clear() {
        store.edit {
            it.remove(DEVICE_ID)
            it.remove(TOKEN)
            it.remove(WORKSPACE)
        }
    }

    private fun read(p: Preferences): DeviceIdentity? {
        val id = p[DEVICE_ID] ?: return null
        val token = p[TOKEN] ?: return null
        // A pairing from before workspaces had no name; it's re-paired on the next 401 anyway.
        return DeviceIdentity(id, token, p[NAME].orEmpty(), p[MODEL].orEmpty(), p[WORKSPACE].orEmpty())
    }

    private companion object {
        val DEVICE_ID = stringPreferencesKey("device_id")
        val TOKEN = stringPreferencesKey("device_token")
        val NAME = stringPreferencesKey("device_name")
        val MODEL = stringPreferencesKey("device_model")
        val WORKSPACE = stringPreferencesKey("workspace_name")
    }
}
