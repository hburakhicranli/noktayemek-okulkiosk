package com.bizim.kiosk

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        context.startForegroundService(Intent(context, KioskForegroundService::class.java))
        // AlarmManager alarms don't survive a reboot -- re-arm the daily reboot alarm every time.
        DailyReboot.scheduleNext(context)
    }
}
