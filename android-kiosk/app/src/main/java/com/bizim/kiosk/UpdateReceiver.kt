package com.bizim.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Receives the result of a PackageInstaller session started by UpdateChecker. */
class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            // Shouldn't normally happen for a device owner app, but if this ROM still wants a
            // confirmation screen (see the SCHEDULE_EXACT_ALARM lesson -- don't assume), show
            // it rather than silently failing.
            @Suppress("DEPRECATION")
            val confirmIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            confirmIntent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { confirmIntent?.let { context.startActivity(it) } }
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val repo = KioskSession.repo(context.applicationContext)
                if (status == PackageInstaller.STATUS_SUCCESS) {
                    Log.i(TAG, "update installed successfully")
                    repo.logEvent("update_installed")
                } else {
                    Log.e(TAG, "update install failed: status=$status message=$message")
                    repo.logEvent("update_failed", message ?: "status=$status")
                }
            }
            pendingResult.finish()
        }
    }

    companion object {
        private const val TAG = "UpdateReceiver"
    }
}
