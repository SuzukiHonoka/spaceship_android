package org.starx.spaceship.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.starx.spaceship.MainActivity
import org.starx.spaceship.R

class ServiceUtil {
    companion object {
        fun createNotificationChannel(ctx: Context, id: String, name: CharSequence, importance: Int = NotificationManager.IMPORTANCE_DEFAULT) {
            val serviceChannel = NotificationChannel(id, name, importance)
            val notificationManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(serviceChannel)

            if (!notificationManager.areNotificationsEnabled()) {
                Toast.makeText(
                    ctx,
                    "Notifications for channel: $name not enabled, please allow it for running foreground service",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        fun buildNotification(ctx: Context, channelId: String, msg: CharSequence): Notification {
            val notificationIntent = Intent(ctx, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                ctx,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(ctx, channelId).apply {
                //setContentTitle("Spaceship")
                setContentText(msg)
                setSmallIcon(R.drawable.cloud_sync)
                setContentIntent(pendingIntent)
                setOngoing(true)
                //setSmallIcon(R.drawable.ic_launcher_nav)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
                }
            }
            return notification.build()
        }
    }
}