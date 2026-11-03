package com.laki.daofortune.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.laki.daofortune.data.ProfileState

/**
 * Puts the standing notifications back after a reboot. WorkManager survives
 * a reboot on its own on most devices, but not when the app is force-stopped
 * and the vendor kills the work; this receiver is the belt to that brace.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (ProfileState(context).dailyReadingEnabled) {
            NotificationScheduler.schedule(context)
        }
    }
}
