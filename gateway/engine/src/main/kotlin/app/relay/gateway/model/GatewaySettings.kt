package app.relay.gateway.model

enum class SimPreference { SIM1, SIM2, ALTERNATE }

/**
 * Which received SMS may leave the phone. Only the phone changes it: the server sees it but can't
 * widen it. Within it, each project's receive rules (set on the web) decide what goes up.
 */
enum class InboxSync {
    /**
     * What the projects' rules take: replies to them (STOP included), the senders they list, and
     * people texting a join keyword. A project set to every SMS still gets only those.
     */
    REPLIES,
    /** Also every SMS, for projects set to take everything on their SIM. */
    ALL,
    /** None; replies and STOP stay on the phone. */
    OFF,
}

/** How a new version of the app gets installed. The web can change it too. */
enum class UpdateMode {
    /** Notify; install when the user taps. */
    ASK,
    /** Download, then install once nothing is sending (silently where Android allows it). */
    AUTO,
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
    /**
     * Send from every SIM at once instead of one message at a time. Each SIM then sends at up to
     * [maxRatePerMinute], so a dual-SIM phone gets through a batch about twice as fast -- and each
     * SIM carries twice the traffic it did while they took turns. Off until asked for.
     */
    val parallelSims: Boolean = false,
    val inboxSync: InboxSync = InboxSync.REPLIES,
    val updateMode: UpdateMode = UpdateMode.ASK,
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
        /** A sender a project texted within this window counts as replying. */
        const val REPLY_WINDOW_MS = 30L * 24 * 60 * 60 * 1000
    }
}
