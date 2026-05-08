package net.activitywatch.android.watcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import net.activitywatch.android.AWPreferences
import net.activitywatch.android.AWServerService

private const val TAG = "AlarmReceiver"

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.w(TAG, "AlarmReceiver called")
        val usw = UsageStatsWatcher(context)
        if (intent.action == "android.intent.action.BOOT_COMPLETED") {
            Log.w(TAG, "Received BOOT_COMPLETED, setting up alarm")
            usw.setupAlarm()
            if (AWPreferences(context).isStartOnBootEnabled()) {
                Log.w(TAG, "Start-on-boot enabled, starting AWServerService")
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AWServerService::class.java),
                )
            }
        } else if(intent.action == "net.activitywatch.android.watcher.LOG_DATA") {
            Log.w(TAG, "Action ${intent.action}, running sendHeartbeats")
            if(UsageStatsWatcher.isUsageAllowed(context)) {
                usw.sendHeartbeats()
            }
        } else {
            Log.w(TAG, "Unknown intent $intent with action ${intent.action}, doing nothing")
        }
    }
}