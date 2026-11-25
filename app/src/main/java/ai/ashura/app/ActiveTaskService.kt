package ai.ashura.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder

class ActiveTaskService : Service() {
    override fun onCreate() {
        super.onCreate()
        val channel = "active_tasks"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(channel, "Active tasks", NotificationManager.IMPORTANCE_LOW)
        )
        val openChat = PendingIntent.getActivity(this, 0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        startForeground(41, Notification.Builder(this, channel)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Ashura task in progress").setContentText("Tap to return to Ashura")
            .setContentIntent(openChat).setOngoing(true).build())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int) = START_NOT_STICKY
    override fun onTimeout(startId: Int, fgsType: Int) { stopSelf() }
    override fun onBind(intent: Intent?): IBinder? = null
}
