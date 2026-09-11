package com.bizim.kiosk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the device "present": pushes a heartbeat every [HEARTBEAT_INTERVAL_MS] and listens
 * for pending commands from the panel, independent of what MainActivity is doing. Also hosts
 * the always-on-top corner touch target for the local PIN escape hatch, since it has to work
 * even while the locked target app fully covers MainActivity.
 */
class KioskForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var commandListener: ListenerRegistration? = null
    private var exitOverlay: View? = null
    private var messageOverlay: View? = null
    private val messageHideRunnable = Runnable { removeMessageOverlay() }

    private val exitHoldRunnable = Runnable {
        scope.launch { KioskEvents.emit(KioskEvent.RequestExitPin) }
    }

    // Waking from screen-off sometimes leaves the overlay's touch channel unresponsive on
    // this device/ROM -- re-adding it fresh on every wake is cheap insurance against that.
    private val screenOnReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "screen on, refreshing exit overlay")
            if (!PinPromptState.showing.value) {
                removeExitGestureOverlay()
                addExitGestureOverlay()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NOTIFICATION_ID, buildNotification())
        registerReceiver(screenOnReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        // The touch zone that requests the PIN dialog must get out of the way once it's up,
        // or it sits on top of the dialog/keyboard and eats taps meant for them. StateFlow
        // replays its current (initially false) value immediately, which adds the overlay on
        // startup -- no separate direct addExitGestureOverlay() call needed.
        CoroutineScope(SupervisorJob() + Dispatchers.Main).launch {
            PinPromptState.showing.collect { showing ->
                if (showing) removeExitGestureOverlay() else addExitGestureOverlay()
            }
        }
        scope.launch {
            val repo = KioskSession.repo(applicationContext)
            val executor = CommandExecutor(applicationContext, repo, scope) { text ->
                showMessageOverlay(text)
            }
            commandListener = repo.listenPendingCommands { id, type, payload ->
                executor.execute(id, type, payload)
            }
            repo.logEvent("boot")
            heartbeatLoop(repo)
        }
    }

    /**
     * A banner shown on top of whatever's currently locked/open (native app or our WebView),
     * without stealing focus or blocking touches -- purely informational, up for
     * [MESSAGE_DURATION_MS]. A new message replaces whatever's currently showing.
     */
    private fun showMessageOverlay(text: String) {
        mainHandler.post {
            removeMessageOverlay()
            val density = resources.displayMetrics.density
            val card = TextView(this).apply {
                this.text = text
                setTextColor(Color.WHITE)
                textSize = 15f
                setPadding((20 * density).toInt(), (14 * density).toInt(), (20 * density).toInt(), (14 * density).toInt())
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#E6161D2B"))
                    cornerRadius = 14 * density
                }
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT,
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                y = (28 * density).toInt()
            }
            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            runCatching { wm.addView(card, params) }.onSuccess { messageOverlay = card }
            mainHandler.removeCallbacks(messageHideRunnable)
            mainHandler.postDelayed(messageHideRunnable, MESSAGE_DURATION_MS)
        }
    }

    private fun removeMessageOverlay() {
        messageOverlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        messageOverlay = null
    }

    private suspend fun heartbeatLoop(repo: FirebaseRepo) {
        while (true) {
            runCatching {
                repo.heartbeat(
                    mapOf(
                        "batteryPct" to DeviceInfo.batteryPct(applicationContext),
                        "charging" to DeviceInfo.isCharging(applicationContext),
                        "wifiRssi" to DeviceInfo.wifiRssi(applicationContext),
                        "storageFreeMb" to DeviceInfo.freeStorageMb(),
                        "uptimeSec" to DeviceInfo.uptimeSec(),
                        "appVersionName" to DeviceInfo.appVersionName(applicationContext),
                    ),
                )
            }
            runCatching { UpdateChecker.checkAndInstall(applicationContext, repo) }
                .onFailure { Log.w(TAG, "update check failed", it) }
            delay(HEARTBEAT_INTERVAL_MS)
        }
    }

    /**
     * An invisible touch target covering the right half of the screen, above every other
     * window. Holding anywhere in it for [EXIT_HOLD_MS] requests the PIN prompt; releasing
     * early cancels. Requires SYSTEM_ALERT_WINDOW, granted at provisioning time via adb (see
     * README) since device owner apps don't get the usual "draw over other apps" toggle.
     *
     * Inset from the top/bottom/right edges and gesture-excluded: WebKioskActivity hides the
     * system bars in immersive mode, and a touch target flush against an edge there gets eaten
     * by the system's own "swipe from edge" gesture before it ever reaches us. Covering half
     * the screen (not just a corner) trades off blocking normal touches on the target's right
     * side while held -- acceptable since this is deliberately an admin/staff gesture.
     */
    private fun addExitGestureOverlay() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val density = resources.displayMetrics.density
        val metrics = resources.displayMetrics
        val insetPx = (24 * density).toInt()
        val widthPx = metrics.widthPixels / 2 - insetPx
        val heightPx = metrics.heightPixels - insetPx * 2
        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.END or Gravity.CENTER_VERTICAL }

        val view = View(this).apply {
            setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        Log.d(TAG, "exit overlay touch down")
                        mainHandler.postDelayed(exitHoldRunnable, EXIT_HOLD_MS)
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                        mainHandler.removeCallbacks(exitHoldRunnable)
                }
                true
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                systemGestureExclusionRects = listOf(Rect(0, 0, widthPx, heightPx))
            }
        }
        runCatching { wm.addView(view, params) }
            .onSuccess { exitOverlay = view }
            .onFailure { Log.w(TAG, "addExitGestureOverlay failed", it) }
    }

    private fun removeExitGestureOverlay() {
        exitOverlay?.let { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(it) }
        exitOverlay = null
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Kiosk durumu", NotificationManager.IMPORTANCE_MIN)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Kiosk modu etkin")
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        commandListener?.remove()
        mainHandler.removeCallbacks(exitHoldRunnable)
        mainHandler.removeCallbacks(messageHideRunnable)
        runCatching { unregisterReceiver(screenOnReceiver) }
        removeExitGestureOverlay()
        removeMessageOverlay()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "KioskForegroundService"
        private const val CHANNEL_ID = "kiosk_status"
        private const val NOTIFICATION_ID = 1
        private const val HEARTBEAT_INTERVAL_MS = 5 * 60 * 1000L
        private const val EXIT_HOLD_MS = 5_000L
        private const val MESSAGE_DURATION_MS = 10 * 60 * 1000L
    }
}
