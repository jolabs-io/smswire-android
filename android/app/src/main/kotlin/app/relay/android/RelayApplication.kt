package app.relay.android

import android.app.Application
import app.relay.android.telephony.SmsResultTracker
import app.relay.android.telephony.SmsResultTrackerProvider

class RelayApplication : Application(), SmsResultTrackerProvider {
    val container: AppContainer by lazy { AppContainer(this) }

    override fun onCreate() {
        super.onCreate()
        CrashReporting.start(this)
    }

    override val smsResultTracker: SmsResultTracker get() = container.smsResults
}
