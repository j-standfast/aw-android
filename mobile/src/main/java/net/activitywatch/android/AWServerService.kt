package net.activitywatch.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

private const val TAG = "AWServerService"
private const val CHANNEL_ID = "aw_server"
private const val NOTIFICATION_ID = 1
private const val ACTION_STOP = "net.activitywatch.android.action.STOP"
private const val REQ_TAP = 0
private const val REQ_STOP = 1

class AWServerService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            Log.i(TAG, "onStartCommand: stop action")
            RustInterface(this).stopServerTask()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        Log.i(TAG, "onStartCommand")

        ensureNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val prefs = AWPreferences(this)
        val host = if (prefs.isRemoteAccessEnabled()) "0.0.0.0" else "127.0.0.1"
        RustInterface(this).startServerTask(this, host)

        return START_STICKY
    }

    override fun onDestroy() {
        // Best-effort: covers the swipe-away path so port 5600 and the
        // serverStarted flag are released cleanly. Not guaranteed if the OS
        // kills the process outright. Idempotent w.r.t. the Stop-action path.
        Log.i(TAG, "onDestroy")
        RustInterface(this).stopServerTask()
        super.onDestroy()
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.aw_server_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.aw_server_notification_channel_description)
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val tapPending = PendingIntent.getActivity(
            this,
            REQ_TAP,
            tapIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val stopIntent = Intent(this, AWServerService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPending = PendingIntent.getService(
            this,
            REQ_STOP,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.aw_server_notification_title))
            .setContentText(getString(R.string.aw_server_notification_text))
            .setSmallIcon(R.mipmap.aw_launcher)
            .setOngoing(true)
            .setContentIntent(tapPending)
            .addAction(0, getString(R.string.aw_server_notification_stop), stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}
