package app.relay.gateway.model

enum class SimPreference { SIM1, SIM2, ALTERNATE }

/**
 * Which received SMS go up to the web inbox. Only the phone changes it: the server can see it
 * but never widen it, so a bank's codes can't be pulled off the phone remotely.
 */
enum class InboxSync {
    /** Only senders this phone texted lately: replies and STOP, never bank codes or personal texts. */
    REPLIES,
    /** Every SMS the phone receives. */
    ALL,
    /** None; replies and STOP stay on the phone. */
    OFF,
}

data class GatewaySettings(
    /** User-facing on/off switch. Off = the server holds new requests and nothing is sent. */
    val gatewayOnline: Boolean = true,
    val defaultSim: SimPreference = SimPreference.SIM1,
    val maxRatePerMinute: Int = DEFAULT_RATE,
    val maxRetries: Int = 2,
    val requestDeliveryReports: Boolean = true,
    val keepAlive: Boolean = true,
    val startOnBoot: Boolean = true,
    val onlySendWhileCharging: Boolean = false,
    val inboxSync: InboxSync = InboxSync.REPLIES,
    /** The relay server this phone pairs with; empty until paired (the app suggests its build's default). */
    val serverUrl: String = "",
) {
    companion object {
        const val DEFAULT_RATE = 30
        /** Above this, carriers may flag the SIM as a spam source. */
        const val SAFE_RATE = 30
        const val MIN_RATE = 1
        const val MAX_RATE = 60
        const val MAX_RETRIES = 5
        /** With [InboxSync.REPLIES], a sender this phone texted within this window counts as replying. */
        const val REPLY_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}
