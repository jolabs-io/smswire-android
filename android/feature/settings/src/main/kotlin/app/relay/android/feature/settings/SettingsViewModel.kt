package app.relay.android.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.relay.android.designsystem.component.ToastController
import app.relay.android.designsystem.format.Format
import app.relay.gateway.model.GatewaySettings
import app.relay.gateway.model.InboxSync
import app.relay.gateway.model.SimPreference
import app.relay.gateway.port.SettingsRepository
import app.relay.gateway.port.SimCardProvider
import app.relay.gateway.port.SmsQuota
import app.relay.gateway.service.DeviceSetup
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phone facts the settings screen reports. Implemented by the platform layer. */
interface DeviceStatusProvider {
    fun ignoresBatteryOptimisations(): Boolean

    /** Android's cap on outgoing SMS parts before it asks the user to confirm; null when exempt. */
    fun smsQuota(): SmsQuota?

    fun smsPermissionGranted(): Boolean

    /** False in the send-only build: no replies or STOP reach the web, whatever the settings say. */
    fun receivesSms(): Boolean = true

    /** Replies reach the web inbox only with this. */
    fun receiveSmsPermissionGranted(): Boolean

    /** Off, and the gateway's status notification (with its Exit) is hidden. */
    fun notificationsEnabled(): Boolean

    /** Whether being the default SMS app lifts the cap on this phone (Android 12+). */
    fun defaultSmsAppLiftsLimit(): Boolean

    fun isDefaultSmsApp(): Boolean
}

enum class ToggleKey(val label: String, val sub: String) {
    DELIVERY_REPORTS("Request delivery reports", "Ask the carrier to confirm each message"),
    KEEP_ALIVE("Keep alive in background", "Foreground service with a persistent notification"),
    START_ON_BOOT("Start on boot", "Reconnect automatically after a restart"),
    ONLY_CHARGING("Only send while charging", "Hold the queue on battery power"),
}

enum class CheckTone { OK, WARN, NEUTRAL }

enum class CheckAction { BATTERY, SMS_PERMISSION, RECEIVE_SMS_PERMISSION, NOTIFICATIONS, WORKSPACE, SMS_LIMIT, DEFAULT_SMS_APP }

data class DeviceCheckUi(val label: String, val value: String, val tone: CheckTone, val action: CheckAction?)

/** Android's send limit and the ways to lift it. [partsPerMinute] is null when there is none. */
data class SmsLimitUi(
    val partsPerMinute: Int? = null,
    val defaultAppLiftsLimit: Boolean = false,
    val isDefaultApp: Boolean = false,
    val adbCommand: String = ADB_RAISE_LIMIT,
) {
    companion object {
        // Android reads the limit once at boot, hence the reboot.
        const val ADB_RAISE_LIMIT = "adb shell settings put global sms_outgoing_check_max_count 1000\nadb reboot"
    }
}

data class SettingsUiState(
    val sims: List<Pair<SimPreference, String>> = emptyList(),
    val simCarriers: Map<SimPreference, String> = emptyMap(),
    val sim: SimPreference = SimPreference.SIM1,
    val rate: Int = GatewaySettings.DEFAULT_RATE,
    val rateWarning: Boolean = false,
    /** Android's limit, when it's below the chosen rate and the app holds to it instead. */
    val rateCap: Int? = null,
    val retries: Int = 2,
    val inboxSync: InboxSync = InboxSync.REPLIES,
    /** False in the send-only build: the inbox setting is replaced by a note. */
    val receivesSms: Boolean = true,
    val toggles: List<Pair<ToggleKey, Boolean>> = emptyList(),
    val checks: List<DeviceCheckUi> = emptyList(),
    val serverUrl: String = "",
    /** The web workspace this phone is paired into. */
    val workspace: String = "",
    val smsLimit: SmsLimitUi = SmsLimitUi(),
)

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val deviceSetup: DeviceSetup,
    private val sims: SimCardProvider,
    private val device: DeviceStatusProvider,
    private val toast: ToastController,
    ticker: Flow<Unit>,
) : ViewModel() {
    /** Bumped when the screen resumes: device checks may have changed in system settings. */
    private val refreshes = MutableStateFlow(0)

    val state: StateFlow<SettingsUiState> = combine(settings.settings, deviceSetup.identity, ticker, refreshes) { s, identity, _, _ ->
        render(s, identity?.workspaceName.orEmpty())
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    private val _smsLimitOpen = MutableStateFlow(false)
    val smsLimitOpen: StateFlow<Boolean> = _smsLimitOpen.asStateFlow()

    private val _unpairOpen = MutableStateFlow(false)
    val unpairOpen: StateFlow<Boolean> = _unpairOpen.asStateFlow()

    fun setSim(value: SimPreference) = update { it.copy(defaultSim = value) }

    fun setRate(value: Int) = update { it.copy(maxRatePerMinute = value.coerceIn(GatewaySettings.MIN_RATE, GatewaySettings.MAX_RATE)) }

    fun setInboxSync(value: InboxSync) = update { it.copy(inboxSync = value) }

    fun changeRetries(delta: Int) = update { it.copy(maxRetries = (it.maxRetries + delta).coerceIn(0, GatewaySettings.MAX_RETRIES)) }

    fun toggle(key: ToggleKey) = update {
        when (key) {
            ToggleKey.DELIVERY_REPORTS -> it.copy(requestDeliveryReports = !it.requestDeliveryReports)
            ToggleKey.KEEP_ALIVE -> it.copy(keepAlive = !it.keepAlive)
            ToggleKey.START_ON_BOOT -> it.copy(startOnBoot = !it.startOnBoot)
            ToggleKey.ONLY_CHARGING -> it.copy(onlySendWhileCharging = !it.onlySendWhileCharging)
        }
    }

    fun refresh() {
        refreshes.value++
    }

    fun openSmsLimit() {
        _smsLimitOpen.value = true
    }

    fun dismissSmsLimit() {
        _smsLimitOpen.value = false
    }

    fun openUnpair() {
        _unpairOpen.value = true
    }

    fun dismissUnpair() {
        _unpairOpen.value = false
    }

    /** Forgets the pairing; the app returns to the pairing screen. */
    fun unpair() {
        viewModelScope.launch {
            _unpairOpen.value = false
            deviceSetup.unpair()
            toast.show("Phone unpaired")
        }
    }

    private fun update(transform: (GatewaySettings) -> GatewaySettings) {
        viewModelScope.launch { settings.update(transform) }
    }

    private fun render(s: GatewaySettings, workspace: String): SettingsUiState {
        val cards = sims.available()
        val slots = mapOf(SimPreference.SIM1 to 1, SimPreference.SIM2 to 2)
        val limit = device.smsQuota()?.let { (it.maxParts * 60_000L / it.windowMs.coerceAtLeast(1)).toInt() }
        return SettingsUiState(
            sims = listOf(SimPreference.SIM1 to "SIM 1", SimPreference.SIM2 to "SIM 2", SimPreference.ALTERNATE to "Alternate"),
            simCarriers = slots.mapNotNull { (pref, slot) -> cards.firstOrNull { it.slot == slot }?.let { pref to it.carrier } }.toMap(),
            sim = s.defaultSim,
            rate = s.maxRatePerMinute,
            rateWarning = s.maxRatePerMinute > GatewaySettings.SAFE_RATE,
            rateCap = limit?.takeIf { it < s.maxRatePerMinute },
            retries = s.maxRetries,
            inboxSync = s.inboxSync,
            receivesSms = device.receivesSms(),
            toggles = listOf(
                ToggleKey.DELIVERY_REPORTS to s.requestDeliveryReports,
                ToggleKey.KEEP_ALIVE to s.keepAlive,
                ToggleKey.START_ON_BOOT to s.startOnBoot,
                ToggleKey.ONLY_CHARGING to s.onlySendWhileCharging,
            ),
            checks = listOfNotNull(
                device.ignoresBatteryOptimisations().let { ok ->
                    DeviceCheckUi("Battery optimisation", if (ok) "Unrestricted" else "Optimised", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.BATTERY)
                },
                // Relay holds sends under this limit; above it Android would stop to ask the user.
                if (limit == null) {
                    DeviceCheckUi("Android SMS limit", "None", CheckTone.OK, CheckAction.SMS_LIMIT)
                } else {
                    DeviceCheckUi("Android SMS limit", "${Format.count(limit)} / min", if (limit >= s.maxRatePerMinute) CheckTone.OK else CheckTone.WARN, CheckAction.SMS_LIMIT)
                },
                device.smsPermissionGranted().let { ok ->
                    DeviceCheckUi("Send SMS", if (ok) "Granted" else "Not granted", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.SMS_PERMISSION)
                },
                device.receiveSmsPermissionGranted().takeIf { device.receivesSms() }?.let { ok ->
                    DeviceCheckUi("Receive SMS", if (ok) "Granted" else "Replies lost", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.RECEIVE_SMS_PERMISSION)
                },
                device.notificationsEnabled().let { ok ->
                    DeviceCheckUi("Notifications", if (ok) "On" else "Off", if (ok) CheckTone.OK else CheckTone.WARN, if (ok) null else CheckAction.NOTIFICATIONS)
                },
                DeviceCheckUi("Workspace", workspace.ifEmpty { s.serverUrl.substringAfter("://") }, CheckTone.NEUTRAL, CheckAction.WORKSPACE),
            ),
            serverUrl = s.serverUrl,
            workspace = workspace,
            smsLimit = SmsLimitUi(limit, device.defaultSmsAppLiftsLimit(), device.isDefaultSmsApp()),
        )
    }
}
