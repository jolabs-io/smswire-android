package app.relay.android.platform.updates

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import app.relay.gateway.port.AppInstaller
import app.relay.gateway.port.DownloadedApk
import app.relay.gateway.port.SettingsRepository
import app.relay.protocol.AppUpdate
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Downloads a new version from the phone's own server (checked against its SHA-256) and hands it
 * to Android's PackageInstaller. Android also checks it's signed with this app's key.
 */
class AndroidAppInstaller(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
    /** Before pairing, the server the app was built for. */
    private val fallbackServerUrl: String,
) : AppInstaller {

    override suspend fun download(update: AppUpdate, onProgress: (Float) -> Unit): DownloadedApk = withContext(Dispatchers.IO) {
        val server = settings.current().serverUrl.ifBlank { fallbackServerUrl }.trimEnd('/')
        val url = server.toHttpUrl().resolve(update.url) ?: throw IOException("Bad update link: ${update.url}")
        // Only ever the one being installed: older downloads go.
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "smswire-${update.versionName}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        try {
            http.newCall(Request.Builder().url(url).build()).execute().use { res ->
                if (!res.isSuccessful) throw IOException("Download failed (HTTP ${res.code})")
                val body = res.body ?: throw IOException("Download failed (empty)")
                val total = body.contentLength().takeIf { it > 0 } ?: update.size
                body.byteStream().use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read = 0L
                        var reported = 0
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            ensureActive()
                            output.write(buffer, 0, n)
                            digest.update(buffer, 0, n)
                            read += n
                            val percent = (read * 100 / total).toInt()
                            if (percent != reported) {
                                reported = percent
                                onProgress((read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
            val sha256 = digest.digest().joinToString("") { "%02x".format(it) }
            if (update.sha256 != null && !sha256.equals(update.sha256, ignoreCase = true)) {
                throw IOException("The download didn't match its checksum; try again")
            }
        } catch (e: Exception) {
            file.delete()
            throw e
        }
        DownloadedApk(update.versionName, file.path)
    }

    override suspend fun install(apk: DownloadedApk) = withContext(Dispatchers.IO) {
        val file = File(apk.path)
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            // Where Android allows it (Android 12+, "Install unknown apps" on, and this app the installer of
            // record, which it becomes with its first own update), no confirmation tap.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            file.inputStream().use { input ->
                session.openWrite("base.apk", 0, file.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
            val result = Intent(context, InstallStatusReceiver::class.java).setAction(InstallStatusReceiver.ACTION)
            session.commit(PendingIntent.getBroadcast(context, id, result, flags).intentSender)
        }
    }

    override fun installsSilently() =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && context.packageManager.canRequestPackageInstalls()

    private companion object {
        const val DIR = "updates"
    }
}
