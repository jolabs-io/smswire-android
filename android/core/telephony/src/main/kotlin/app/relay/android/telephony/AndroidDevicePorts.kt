package app.relay.android.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import app.relay.gateway.model.SimCard
import app.relay.gateway.port.PowerMonitor
import app.relay.gateway.port.PowerState
import app.relay.gateway.port.SimCardProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Active SIMs, from SubscriptionManager (needs READ_PHONE_STATE; empty without it). */
class AndroidSimCardProvider(private val context: Context) : SimCardProvider {
    override fun available(): List<SimCard> = try {
        val manager = context.getSystemService(SubscriptionManager::class.java)
        manager?.activeSubscriptionInfoList.orEmpty().map {
            SimCard(
                slot = it.simSlotIndex + 1,
                carrier = (it.carrierName ?: it.displayName ?: "SIM").toString(),
                subscriptionId = it.subscriptionId,
            )
        }.sortedBy { it.slot }
    } catch (_: SecurityException) {
        emptyList()
    }
}

/** Battery level and charging state from the sticky ACTION_BATTERY_CHANGED broadcast. */
class AndroidPowerMonitor(context: Context) : PowerMonitor {
    private val _state = MutableStateFlow(PowerState(batteryPercent = 100, charging = true))
    override val state: StateFlow<PowerState> = _state.asStateFlow()

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = update(intent)
    }

    init {
        ContextCompat.registerReceiver(
            context.applicationContext,
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )?.let(::update)
    }

    private fun update(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
        val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
        if (level >= 0 && scale > 0) _state.value = PowerState(level * 100 / scale, plugged)
    }
}
