package org.starx.spaceship.service

import android.app.Notification.FOREGROUND_SERVICE_IMMEDIATE
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import org.starx.spaceship.MainActivity
import org.starx.spaceship.R
import org.starx.spaceship.helper.Helper
import org.starx.spaceship.helper.IStatusListener

class Background : Service() {
    companion object{
        const val TAG = "Service"
        const val CHANNEL_ID = "Spaceship"
        const val CHANNEL_NAME = "Background indicator"
    }

    private var running = false

    private lateinit var helper: Helper

    override fun onLowMemory() {
        Log.i(TAG, "onLowMemory")
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        applyForeground()
        if (intent != null && !running) {
            running = true
            val s = intent.getStringExtra("config")!!
            helper = Helper(s, object : IStatusListener {
                override fun onExit() {
                    if (running) {
                        stopSelf()
                        Handler(applicationContext.mainLooper).post {
                            Toast.makeText(
                                applicationContext,
                                "spaceship exited ${if (failed) "with internal error" else "unexpectedly"}",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }

                override fun onError() {
                    failed = true
                }
            })
            helper.start()
            Toast.makeText(applicationContext, "Service thread started", Toast.LENGTH_SHORT)
                .show()
        }
        return START_NOT_STICKY
    }

    private fun applyForeground(){
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(serviceChannel)
        if (!notificationManager.areNotificationsEnabled()) {
            Toast.makeText(this,
                "Notifications not enabled, please allow it to run foreground service",
                Toast.LENGTH_SHORT).show()
        }
        val notificationIntent = Intent(this, MainActivity::class.java)

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID).apply {
            //setContentTitle("Spaceship")
            setContentText("Service is running..")
            setSmallIcon(R.drawable.cloud_sync)
            setContentIntent(pendingIntent)
            setOngoing(true)
            //setSmallIcon(R.drawable.ic_launcher_nav)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                foregroundServiceBehavior = FOREGROUND_SERVICE_IMMEDIATE
            }
        }.build()
        startForeground(1, notification)
    }



    override fun onDestroy() {
        running = false
        helper.stop()
        Toast.makeText(applicationContext, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}