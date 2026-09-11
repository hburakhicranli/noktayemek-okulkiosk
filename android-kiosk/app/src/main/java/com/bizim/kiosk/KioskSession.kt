package com.bizim.kiosk

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Lazily establishes this tablet's identity/session and hands out a shared repo. */
object KioskSession {
    private val mutex = Mutex()

    @Volatile
    private var repoInstance: FirebaseRepo? = null

    suspend fun repo(context: Context): FirebaseRepo {
        repoInstance?.let { return it }
        return mutex.withLock {
            repoInstance?.let { return@withLock it }
            connectWithRetry(context).also { repoInstance = it }
        }
    }

    // Boot-time Wi-Fi/DNS hiccups and transient rules/network errors are routine on
    // unattended kiosk hardware -- retry instead of letting the process crash.
    private suspend fun connectWithRetry(context: Context): FirebaseRepo {
        var attempt = 0
        while (true) {
            try {
                val deviceId = DeviceIdentity.deviceId(context)
                val authUid = DeviceIdentity.ensureSignedIn()
                val repo = FirebaseRepo(deviceId, authUid)
                repo.ensureDeviceDocument()
                return repo
            } catch (e: Exception) {
                attempt++
                delay(minOf(30_000L, 2_000L * attempt))
            }
        }
    }
}
