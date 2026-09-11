package com.bizim.kiosk

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock

/** Point-in-time snapshot helpers used for the heartbeat payload. */
object DeviceInfo {

    fun batteryPct(context: Context): Int? {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val pct = bm?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: return null
        return if (pct in 0..100) pct else null
    }

    fun isCharging(context: Context): Boolean {
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    fun wifiRssi(context: Context): Int? = try {
        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        wm?.connectionInfo?.rssi
    } catch (e: SecurityException) {
        null
    }

    fun freeStorageMb(): Long =
        StatFs(Environment.getDataDirectory().path).availableBytes / (1024 * 1024)

    fun uptimeSec(): Long = SystemClock.elapsedRealtime() / 1000

    fun appVersionName(context: Context): String = try {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
    } catch (e: PackageManager.NameNotFoundException) {
        "?"
    }
}
