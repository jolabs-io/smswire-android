package app.relay.android

import android.content.Context
import io.sentry.Breadcrumb
import io.sentry.Hint
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import io.sentry.android.core.SentryAndroidOptions

/**
 * Crashes and ANRs to Sentry, when the build carries a DSN (CI's ANDROID_SENTRY_DSN). Reports
 * never include message text, phone numbers or what the app sent over the network: no
 * screenshots, no view hierarchy, no tracing, no logs, and only breadcrumbs about the app's and
 * the system's state, stripped of their data.
 */
object CrashReporting {
    /** Breadcrumb categories kept: lifecycle and system state, nothing about what the app handles. */
    private val KEPT_CATEGORIES = setOf("app.lifecycle", "ui.lifecycle", "device.event", "device.orientation", "network.event")

    fun start(context: Context) {
        if (BuildConfig.SENTRY_DSN.isBlank()) return
        SentryAndroid.init(context) { configure(it, BuildConfig.SENTRY_DSN, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE) }
    }

    fun configure(options: SentryAndroidOptions, dsn: String, versionName: String, versionCode: Int) {
        options.dsn = dsn
        options.release = "smswire-android@$versionName+$versionCode"
        options.environment = "production"
        options.isSendDefaultPii = false
        options.isAttachScreenshot = false
        options.isAttachViewHierarchy = false
        options.tracesSampleRate = null
        options.isEnableUserInteractionBreadcrumbs = false
        options.isEnableUserInteractionTracing = false
        options.isAnrEnabled = true
        options.beforeBreadcrumb = SentryOptions.BeforeBreadcrumbCallback { breadcrumb, _: Hint -> keep(breadcrumb) }
    }

    /** The breadcrumb without its data, or null to drop it. */
    fun keep(breadcrumb: Breadcrumb): Breadcrumb? {
        if (breadcrumb.category !in KEPT_CATEGORIES) return null
        breadcrumb.data.clear()
        return breadcrumb
    }
}
