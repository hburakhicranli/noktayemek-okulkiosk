package com.bizim.kiosk

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Applies a command that was written to this device's `commands` subcollection by the panel. */
class CommandExecutor(
    private val context: Context,
    private val repo: FirebaseRepo,
    private val scope: CoroutineScope,
    private val onShowMessage: (String) -> Unit,
) {
    private val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
    private val adminComponent = ComponentName(context, DeviceAdminReceiver::class.java)

    fun execute(commandId: String, type: String, payload: Map<String, Any?>) {
        scope.launch {
            // Mark done (and log) BEFORE acting: "reboot" kills the process before control
            // ever returns here, so if the write happened after, the command would stay
            // "pending" forever and get replayed -- and re-rebooted -- on every next boot.
            repo.markCommandDone(commandId)
            repo.logEvent("command_ack", type)
            when (type) {
                "restart_app" -> KioskEvents.emit(KioskEvent.RelaunchTarget)
                "reboot" -> if (dpm.isDeviceOwnerApp(context.packageName)) dpm.reboot(adminComponent)
                "show_message" -> {
                    val text = payload["text"] as? String ?: ""
                    if (text.isNotBlank()) onShowMessage(text)
                }
                "unlock" -> KioskEvents.emit(KioskEvent.Unlock)
                "lock" -> KioskEvents.emit(KioskEvent.Lock)
            }
        }
    }
}
