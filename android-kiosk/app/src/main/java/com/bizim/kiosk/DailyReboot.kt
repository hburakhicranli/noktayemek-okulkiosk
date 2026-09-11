package com.bizim.kiosk

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import java.util.Calendar

/**
 * Schedules the tablet to reboot itself once a day. A kiosk that's deliberately never allowed
 * to sleep (see MainActivity.keepScreenAlwaysOn) can otherwise accumulate memory/graphics-driver
 * cruft over weeks of uninterrupted uptime -- a clean nightly reboot resets that. BootReceiver
 * relaunches the app (and it re-locks to whatever the last config was) automatically afterward.
 */
object DailyReboot {
    private const val TAG = "DailyReboot"
    private const val REBOOT_HOUR = 0 // midnight

    fun scheduleNext(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            Intent(context, DailyRebootReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, REBOOT_HOUR)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        runCatching {
            // Inexact-but-idle-aware: fires within a short window of the target time, no
            // SCHEDULE_EXACT_ALARM needed. A few minutes of drift around midnight doesn't matter
            // here, and on this ROM the device-owner exemption for exact alarms doesn't apply.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.timeInMillis, pendingIntent)
        }.onSuccess {
            Log.i(TAG, "scheduled for ${next.time}")
        }.onFailure {
            Log.e(TAG, "failed to schedule alarm", it)
        }
    }
}
