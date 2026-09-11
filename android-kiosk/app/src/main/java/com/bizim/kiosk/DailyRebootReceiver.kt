package com.bizim.kiosk

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/** Fires once a day at midnight (see DailyReboot) -- reboots, then reschedules itself. */
class DailyRebootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "daily reboot alarm fired")
        DailyReboot.scheduleNext(context)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val admin = ComponentName(context, DeviceAdminReceiver::class.java)
        if (dpm.isDeviceOwnerApp(context.packageName)) {
            dpm.reboot(admin)
        }
    }

    companion object {
        private const val TAG = "DailyRebootReceiver"
    }
}
