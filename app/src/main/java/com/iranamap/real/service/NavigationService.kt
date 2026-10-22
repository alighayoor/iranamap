package com.iranamap.real.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.iranamap.real.MapActivity
import com.iranamap.real.R

/**
 * سرویس پیش‌زمینه‌ی ناوبری: با یک اعلان پایدار، پردازش ناوبری و اعلام صوتی را
 * هنگام رفتن برنامه به پس‌زمینه زنده نگه می‌دارد.
 */
class NavigationService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val destination = intent?.getStringExtra(EXTRA_DESTINATION).orEmpty()
        val text = if (destination.isBlank()) {
            getString(R.string.notification_text)
        } else {
            getString(R.string.notification_title) + " • " + destination
        }
        val notification = buildNotification(text)
        startForeground(NOTIFICATION_ID, notification)
        return START_STICKY
    }

    private fun buildNotification(text: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MapActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_navigation)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(contentIntent)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .build()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_nav),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_nav)
                setShowBadge(false)
            }
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        try {
            stopForeground(true)
        } catch (e: Exception) {
            // نادیده گرفته می‌شود
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_DESTINATION = "destination"
        private const val CHANNEL_ID = "iranamap_navigation"
        private const val NOTIFICATION_ID = 1001
    }
}
