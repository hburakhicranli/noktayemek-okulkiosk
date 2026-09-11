package com.bizim.kiosk

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.launch

/**
 * Acts as the device's HOME/launcher. On boot it signs in, waits for an admin-assigned
 * `targetPackage` or `webUrl`, then locks the task to that app (or our own fullscreen
 * WebView). If the locked target ever dies and control falls back to us, onResume treats
 * that as a crash and relaunches it.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var dpm: DevicePolicyManager
    private lateinit var adminComponent: ComponentName
    private lateinit var statusText: TextView
    private lateinit var appListView: ListView

    /** What we're currently locking the device to -- a native app, or our own WebView. */
    private sealed class LockTarget {
        data class App(val pkg: String) : LockTarget()
        data class Web(val url: String) : LockTarget()
    }

    private var deviceListener: ListenerRegistration? = null
    private var currentTarget: LockTarget? = null
    private var showingPinPrompt = false
    private var pendingUnlock = false
    private var relockRequestedByAdmin = false
    private var launchableAppsCache: List<Pair<String, String>> = emptyList()

    // null = the device doc hasn't been read even once yet. Distinct from currentTarget because
    // that's cleared to null for a *blank* config too -- this tracks the raw pair so we can tell
    // "still blank, no change" apart from "genuinely just changed".
    private var lastAppliedConfig: Pair<String, String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or WindowManager.LayoutParams.FLAG_FULLSCREEN,
        )
        setContentView(R.layout.activity_main)
        statusText = findViewById(R.id.statusText)
        appListView = findViewById(R.id.appListView)
        appListView.setOnItemClickListener { _, _, position, _ ->
            val pkg = launchableAppsCache.getOrNull(position)?.second ?: return@setOnItemClickListener
            packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
        }

        dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        adminComponent = ComponentName(this, DeviceAdminReceiver::class.java)

        requestNotificationPermissionIfNeeded()
        ContextCompat.startForegroundService(this, Intent(this, KioskForegroundService::class.java))
        registerAsDefaultLauncher()
        keepScreenAlwaysOn()
        DailyReboot.scheduleNext(this)

        lifecycleScope.launch {
            val repo = KioskSession.repo(applicationContext)
            deviceListener = repo.listenDevice { snap ->
                val pkg = snap.getString("targetPackage").orEmpty()
                val webUrl = snap.getString("webUrl").orEmpty()
                val label = snap.getString("label").orEmpty()
                if (label.isNotBlank()) title = label
                // The device doc also changes every ~45s from our own heartbeat writes --
                // only actually relaunch when the admin-assigned config really changed.
                val config = pkg to webUrl
                if (config != lastAppliedConfig) {
                    lastAppliedConfig = config
                    onTargetConfigUpdated(pkg, webUrl)
                }
            }
        }

        lifecycleScope.launch {
            // CREATED, not STARTED: the locked target app fully covers us for basically all of
            // normal operation, which stops us at onStop (state CREATED) -- panel commands and
            // the exit-PIN request both arrive through this collector and must not go silent then.
            repeatOnLifecycle(Lifecycle.State.CREATED) {
                KioskEvents.events.collect { event -> handleEvent(event) }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // stopLockTask(), like startLockTask(), only reliably takes effect while THIS
        // activity is actually the foreground one -- beginUnlock() brings us here first and
        // sets this flag, so onResume is where the unlock itself actually happens.
        if (pendingUnlock) {
            pendingUnlock = false
            startUnlockWindow()
            return
        }
        // We only get resumed while a target is set if the locked app died/closed -- unless
        // we're the one who just brought ourselves forward for the PIN prompt.
        val target = currentTarget
        if (target != null && !showingPinPrompt) {
            val wasAdminRelock = relockRequestedByAdmin
            relockRequestedByAdmin = false
            fireAndForget {
                KioskSession.repo(applicationContext).logEvent(
                    if (wasAdminRelock) "command_ack" else "crash",
                    if (wasAdminRelock) {
                        "Admin panelden kilitlendi"
                    } else {
                        "Hedef ön planda değildi, yeniden başlatıldı"
                    },
                )
            }
            launchCurrentTarget(target)
        }
    }

    override fun onDestroy() {
        deviceListener?.remove()
        super.onDestroy()
    }

    private fun registerAsDefaultLauncher() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.addPersistentPreferredActivity(adminComponent, filter, ComponentName(this, MainActivity::class.java))
    }

    /**
     * Device-wide, not tied to our own window: the locked target is usually a third-party
     * app's or WebKioskActivity's window, neither of which we can force to stay bright/awake
     * from here. setSystemSetting()/setGlobalSetting() are the device-owner-only APIs Android
     * exposes for exactly this -- there's no way (by design) to also disable the physical
     * power button's short-press screen-off, but with sleep/dimming turned off at the source
     * there should never be a reason to reach for it.
     */
    private fun keepScreenAlwaysOn() {
        if (!dpm.isDeviceOwnerApp(packageName)) return
        runCatching {
            dpm.setGlobalSetting(adminComponent, Settings.Global.STAY_ON_WHILE_PLUGGED_IN, "3")
            dpm.setSystemSetting(adminComponent, Settings.System.SCREEN_OFF_TIMEOUT, Int.MAX_VALUE.toString())
            dpm.setSystemSetting(
                adminComponent,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL.toString(),
            )
            dpm.setSystemSetting(adminComponent, Settings.System.SCREEN_BRIGHTNESS, "255")
        }
    }

    /** webUrl wins when both are set -- it's the more specific ask ("show this page"). */
    private fun onTargetConfigUpdated(pkg: String, webUrl: String) {
        if (webUrl.isNotBlank()) {
            currentTarget = LockTarget.Web(webUrl)
            if (dpm.isDeviceOwnerApp(packageName)) {
                dpm.setLockTaskPackages(adminComponent, arrayOf(packageName))
            }
            launchCurrentTarget(LockTarget.Web(webUrl))
            return
        }
        currentTarget = pkg.ifBlank { null }?.let { LockTarget.App(it) }
        if (pkg.isBlank()) {
            statusText.text = "Kurulum bekleniyor\nCihaz ID: ${DeviceIdentity.deviceId(this)}"
            return
        }
        if (!isPackageInstalled(pkg)) {
            statusText.text = "Hedef uygulama bulunamadı:\n$pkg"
            return
        }
        if (dpm.isDeviceOwnerApp(packageName)) {
            dpm.setLockTaskPackages(adminComponent, arrayOf(packageName, pkg))
        }
        launchCurrentTarget(LockTarget.App(pkg))
    }

    private fun launchCurrentTarget(target: LockTarget) {
        val launchIntent = when (target) {
            is LockTarget.App -> packageManager.getLaunchIntentForPackage(target.pkg) ?: run {
                statusText.text = "Hedef uygulama açılamadı:\n${target.pkg}"
                return
            }
            is LockTarget.Web -> Intent(this, WebKioskActivity::class.java)
                .putExtra(WebKioskActivity.EXTRA_URL, target.url)
        }
        statusText.text = ""
        appListView.visibility = View.GONE
        // Back to locked: the right-half touch zone can safely come back too.
        PinPromptState.set(false)
        // startLockTask() only succeeds while THIS activity is the foreground one, so it has
        // to run before startActivity() hands focus to the target -- calling it after throws
        // "Invalid task, not in foreground" and silently leaves the device unlocked.
        if (dpm.isDeviceOwnerApp(packageName) && !isInLockTaskMode()) {
            startLockTask()
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        startActivity(launchIntent)
        fireAndForget { KioskSession.repo(applicationContext).setLocked(true) }
    }

    private fun isPackageInstalled(pkg: String): Boolean = try {
        packageManager.getPackageInfo(pkg, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    private fun isInLockTaskMode(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
    }

    private suspend fun handleEvent(event: KioskEvent) {
        when (event) {
            is KioskEvent.RelaunchTarget -> currentTarget?.let { launchCurrentTarget(it) }
            is KioskEvent.Unlock -> beginUnlock()
            is KioskEvent.Lock -> beginRelock()
            is KioskEvent.RequestExitPin -> promptForPin()
        }
    }

    /**
     * Entry point for a remote "unlock" command, which can arrive while some other
     * (allowlisted) activity is in front of us. Brings this activity forward; onResume()
     * does the actual unlocking once we're guaranteed to be the foreground activity.
     */
    private fun beginUnlock() {
        pendingUnlock = true
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    /**
     * Admin-triggered immediate re-lock, independent of the "return to kiosk relocks
     * automatically" behavior. Just bringing ourselves forward is enough: onResume's own
     * watchdog does the relaunch+relock, since nothing marks this as a pending unlock.
     */
    private fun beginRelock() {
        pendingUnlock = false
        relockRequestedByAdmin = true
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
    }

    /** Local escape hatch: right half of the screen held for 5s (see KioskForegroundService). */
    private fun promptForPin() {
        if (showingPinPrompt) return
        showingPinPrompt = true
        PinPromptState.set(true)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))

        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            imeOptions = EditorInfo.IME_ACTION_DONE
        }

        lateinit var dialog: AlertDialog

        fun submit() {
            showingPinPrompt = false
            dialog.dismiss()
            // Leave PinPromptState suppressed either way: startUnlockWindow() keeps it that
            // way for the unlocked session, launchCurrentTarget() restores it on relock.
            if (input.text.toString() == BuildConfig.EXIT_PIN) {
                startUnlockWindow()
            } else {
                Toast.makeText(this, "Yanlış PIN", Toast.LENGTH_SHORT).show()
                currentTarget?.let { launchCurrentTarget(it) }
            }
        }

        fun cancel() {
            showingPinPrompt = false
            dialog.dismiss()
            currentTarget?.let { launchCurrentTarget(it) }
        }

        // The keyboard's own "Done" key submits too -- on top of the dialog's own buttons,
        // in case those ever end up covered by the keyboard on some device/orientation.
        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                submit()
                true
            } else {
                false
            }
        }

        dialog = AlertDialog.Builder(this)
            .setTitle("Kilidi açmak için PIN gir")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Onayla") { _, _ -> submit() }
            .setNegativeButton("İptal") { _, _ -> cancel() }
            .create()
        // The soft keyboard doesn't auto-show for a dialog's EditText over our fullscreen
        // window -- request focus and pop it explicitly once the dialog is actually up.
        dialog.setOnShowListener {
            input.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
        }
        dialog.show()
    }

    /**
     * Call only once this activity is confirmed to be in the foreground (from onResume's
     * pendingUnlock branch, or directly after a correct PIN since the dialog never left us).
     * Unlocks with no fixed timer: normal Android behavior continues until either the admin
     * sends "lock", or the kiosk/home is revisited (home button, reopening the launcher) --
     * at that point it's a genuine new onResume call, so the watchdog re-locks on its own.
     */
    private fun startUnlockWindow() {
        if (isInLockTaskMode()) stopLockTask()
        // Stays suppressed for the whole unlocked session (not just the PIN dialog) --
        // otherwise it sits over the right half of the app list below and eats scrolls/taps.
        PinPromptState.set(true)
        statusText.text = "Kilit açık — bir uygulama seç.\nKiosk'a dönülünce ya da admin kilitleyince tekrar kilitlenir."
        launchableAppsCache = launchableApps()
        appListView.adapter = ArrayAdapter(
            this,
            R.layout.list_item_app,
            launchableAppsCache.map { it.first },
        )
        appListView.visibility = View.VISIBLE
        fireAndForget { KioskSession.repo(applicationContext).setLocked(false) }
    }

    /** Fire-and-forget Firestore write: never let a rejected/offline write crash the app. */
    private fun fireAndForget(block: suspend () -> Unit) {
        lifecycleScope.launch { runCatching { block() } }
    }

    /** Every app with a launcher icon, excluding ourselves -- shown while unlocked. */
    private fun launchableApps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(intent, 0)
            .asSequence()
            .filter { it.activityInfo.packageName != packageName }
            .map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }
            .sortedBy { it.first.lowercase() }
            .toList()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }
}
