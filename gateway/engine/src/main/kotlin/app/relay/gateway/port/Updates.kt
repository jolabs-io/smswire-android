package app.relay.gateway.port

import app.relay.protocol.AppUpdate

/** A downloaded .apk whose SHA-256 matched, waiting to be installed. */
data class DownloadedApk(val versionName: String, val path: String)

/** Downloads and installs new versions of the app. Android: OkHttp and PackageInstaller. */
interface AppInstaller {
    /** Fetches [update]'s .apk from the phone's server and checks its SHA-256; throws on any failure. */
    suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): DownloadedApk

    /**
     * Hands [apk] to the system installer and returns. On success the app is replaced and restarts;
     * a failure (or the user cancelling) comes back through [app.relay.gateway.service.AppUpdates.onInstallFailed].
     */
    suspend fun install(apk: DownloadedApk)

    /** Whether installing needs no tap from the user (Android 12+, with "Install unknown apps" allowed). */
    fun installsSilently(): Boolean
}

/** Tells the person holding the phone about an update. Android: notifications on their own channel. */
interface UpdateNotifier {
    /** A new version is out (urgent when critical). */
    fun available(update: AppUpdate)

    /** Downloaded and checked; a tap installs it. */
    fun ready(update: AppUpdate)

    fun failed(update: AppUpdate, reason: String)

    fun clear()
}

/** What the updater remembers across restarts, including the one an update causes. */
interface UpdateStore {
    /** The version "Later" was tapped on, and until when. */
    suspend fun snoozed(): Pair<String, Long>?
    suspend fun snooze(versionName: String, until: Long)

    /** Set just before installing: whether the gateway was online, to restore in the new version. */
    suspend fun installing(versionName: String, notes: String?, wasOnline: Boolean)

    /** What [installing] saved; null when no update is under way. */
    suspend fun pending(): PendingUpdate?
    suspend fun clearPending()

    /** The notes to show once the new version runs, until the user has seen them. */
    suspend fun whatsNew(): Pair<String, String>?
    suspend fun setWhatsNew(versionName: String?, notes: String?)
}

data class PendingUpdate(val versionName: String, val notes: String?, val wasOnline: Boolean)

/** No updates: for hosts that can't install (the JVM, tests that don't care). */
object NoAppInstaller : AppInstaller {
    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): DownloadedApk =
        throw UnsupportedOperationException("This build can't install updates")
    override suspend fun install(apk: DownloadedApk) = throw UnsupportedOperationException("This build can't install updates")
    override fun installsSilently() = false
}

object NoUpdateNotifier : UpdateNotifier {
    override fun available(update: AppUpdate) = Unit
    override fun ready(update: AppUpdate) = Unit
    override fun failed(update: AppUpdate, reason: String) = Unit
    override fun clear() = Unit
}
