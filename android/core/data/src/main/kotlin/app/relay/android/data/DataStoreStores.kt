package app.relay.android.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.relay.gateway.model.DeviceIdentity
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.UpdateMode
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.DeviceIdentityStore
import app.relay.gateway.port.JoinKeywordStore
import app.relay.gateway.port.PendingUpdate
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.UpdateStore
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
            parallelSims = p[PARALLEL_SIMS] ?: d.parallelSims,
            inboxSync = p[INBOX]?.let { runCatching { InboxSync.valueOf(it) }.getOrNull() } ?: d.inboxSync,
            updateMode = p[UPDATES]?.let { runCatching { UpdateMode.valueOf(it) }.getOrNull() } ?: d.updateMode,
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
        p[PARALLEL_SIMS] = s.parallelSims
        p[INBOX] = s.inboxSync.name
        p[UPDATES] = s.updateMode.name
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
        val PARALLEL_SIMS = booleanPreferencesKey("parallel_sims")
        val INBOX = stringPreferencesKey("inbox_sync")
        val UPDATES = stringPreferencesKey("update_mode")
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

/** The workspace's join keywords, so a keyword texted while offline or before the hello still goes up. */
class DataStoreJoinKeywordStore(context: Context) : JoinKeywordStore {
    private val store = context.applicationContext.relayPreferences

    override suspend fun keywords(): Set<String> = store.data.first()[KEYWORDS].orEmpty()

    override suspend fun replace(keywords: Set<String>) {
        store.edit { if (keywords.isEmpty()) it.remove(KEYWORDS) else it[KEYWORDS] = keywords }
    }

    private companion object {
        val KEYWORDS = stringSetPreferencesKey("join_keywords")
    }
}

/** What the updater remembers across restarts, the one an update causes included. */
class DataStoreUpdateStore(context: Context) : UpdateStore {
    private val store = context.applicationContext.relayPreferences

    override suspend fun snoozed(): Pair<String, Long>? {
        val p = store.data.first()
        return p[SNOOZE_VERSION]?.let { v -> p[SNOOZE_UNTIL]?.let { v to it } }
    }

    override suspend fun snooze(versionName: String, until: Long) {
        store.edit {
            it[SNOOZE_VERSION] = versionName
            it[SNOOZE_UNTIL] = until
        }
    }

    override suspend fun installing(versionName: String, notes: String?, wasOnline: Boolean) {
        store.edit {
            it[PENDING_VERSION] = versionName
            if (notes != null) it[PENDING_NOTES] = notes else it.remove(PENDING_NOTES)
            it[PENDING_ONLINE] = wasOnline
        }
    }

    override suspend fun pending(): PendingUpdate? {
        val p = store.data.first()
        return p[PENDING_VERSION]?.let { PendingUpdate(it, p[PENDING_NOTES], p[PENDING_ONLINE] ?: true) }
    }

    override suspend fun clearPending() {
        store.edit {
            it.remove(PENDING_VERSION)
            it.remove(PENDING_NOTES)
            it.remove(PENDING_ONLINE)
        }
    }

    override suspend fun whatsNew(): Pair<String, String>? {
        val p = store.data.first()
        return p[WHATS_NEW_VERSION]?.let { v -> p[WHATS_NEW_NOTES]?.let { v to it } }
    }

    override suspend fun setWhatsNew(versionName: String?, notes: String?) {
        store.edit {
            if (versionName != null && notes != null) {
                it[WHATS_NEW_VERSION] = versionName
                it[WHATS_NEW_NOTES] = notes
            } else {
                it.remove(WHATS_NEW_VERSION)
                it.remove(WHATS_NEW_NOTES)
            }
        }
    }

    private companion object {
        val SNOOZE_VERSION = stringPreferencesKey("update_snooze_version")
        val SNOOZE_UNTIL = longPreferencesKey("update_snooze_until")
        val PENDING_VERSION = stringPreferencesKey("update_pending_version")
        val PENDING_NOTES = stringPreferencesKey("update_pending_notes")
        val PENDING_ONLINE = booleanPreferencesKey("update_pending_online")
        val WHATS_NEW_VERSION = stringPreferencesKey("whats_new_version")
        val WHATS_NEW_NOTES = stringPreferencesKey("whats_new_notes")
    }
}
