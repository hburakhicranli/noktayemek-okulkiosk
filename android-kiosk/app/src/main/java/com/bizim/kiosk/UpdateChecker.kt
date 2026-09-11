package com.bizim.kiosk

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks `config/update` (set from the panel's Güncelleme page) for a newer versionCode than
 * what's installed. If found, downloads the APK and installs it via PackageInstaller -- device
 * owner apps can commit a session without the normal "install this app?" confirmation screen, so
 * the whole thing runs unattended. Called once per heartbeat cycle (KioskForegroundService).
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val PREFS = "update_checker"
    private const val KEY_LAST_VERSION = "last_attempted_version"
    private const val KEY_LAST_ATTEMPT_AT = "last_attempt_at"

    // Re-attempting a failing download/install every 5-minute heartbeat would hammer the
    // network and spam failure events for no benefit -- wait a while between retries of the
    // *same* target version. A genuinely new version (different versionCode) is never held back.
    private const val RETRY_COOLDOWN_MS = 30 * 60 * 1000L

    suspend fun checkAndInstall(context: Context, repo: FirebaseRepo) {
        val snap = runCatching {
            Firebase.firestore.collection("config").document("update").get().await()
        }.getOrNull() ?: return
        val remoteVersion = snap.getLong("versionCode")?.toInt() ?: return
        val apkUrl = snap.getString("apkUrl")?.takeIf { it.isNotBlank() } ?: return

        val currentVersion = PackageInfoCompat.getLongVersionCode(
            context.packageManager.getPackageInfo(context.packageName, 0),
        ).toInt()
        if (remoteVersion <= currentVersion) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastVersion = prefs.getInt(KEY_LAST_VERSION, -1)
        val lastAttemptAt = prefs.getLong(KEY_LAST_ATTEMPT_AT, 0)
        if (lastVersion == remoteVersion && System.currentTimeMillis() - lastAttemptAt < RETRY_COOLDOWN_MS) {
            return
        }
        prefs.edit()
            .putInt(KEY_LAST_VERSION, remoteVersion)
            .putLong(KEY_LAST_ATTEMPT_AT, System.currentTimeMillis())
            .apply()

        Log.i(TAG, "new version $remoteVersion available (current $currentVersion), downloading")
        runCatching { downloadAndInstall(context, apkUrl) }
            .onFailure {
                Log.e(TAG, "update download/install failed", it)
                runCatching { repo.logEvent("update_failed", it.message) }
            }
    }

    private suspend fun downloadAndInstall(context: Context, apkUrl: String) = withContext(Dispatchers.IO) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            val connection = URL(apkUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.connect()
            check(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "HTTP ${connection.responseCode} downloading APK"
            }
            connection.inputStream.use { input ->
                session.openWrite("kiosk_update", 0, connection.contentLengthLong).use { out ->
                    input.copyTo(out)
                    session.fsync(out)
                }
            }
            connection.disconnect()

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                Intent(context, UpdateReceiver::class.java),
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            session.commit(pendingIntent.intentSender)
        }
    }
}
