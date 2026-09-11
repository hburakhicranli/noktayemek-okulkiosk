package com.bizim.kiosk

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Stable per-tablet identifier and Firebase Anonymous Auth session. */
object DeviceIdentity {
    private const val PREFS = "kiosk_prefs"
    private const val KEY_DEVICE_ID = "device_id"

    fun deviceId(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
    }

    suspend fun ensureSignedIn(): String {
        val auth = Firebase. auth
        val user = auth.currentUser ?: auth.signInAnonymously().await().user
        return requireNotNull(user) { "Anonim girişten kullanıcı dönmedi" }.uid
    }
}
