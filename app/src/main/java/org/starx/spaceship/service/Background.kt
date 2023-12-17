package org.starx.spaceship.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import org.starx.spaceship.MainActivity
import org.starx.spaceship.R
import org.starx.spaceship.action.Message
import org.starx.spaceship.action.Status
import org.starx.spaceship.helper.Helper
import org.starx.spaceship.helper.IStatusListener


class Background : Service() {
    companion object {
        const val TAG = "Service"
        const val CHANNEL_ID = "Spaceship"
        const val CHANNEL_NAME = "Background indicator"
        const val NOTIFICATION_ID = 1
    }

    private val receiver = Receiver()

    private var running = false
    private var failed = false

    private var helper: Helper? = null

    override fun onLowMemory() {
        Log.i(TAG, "onLowMemory")
    }

    override fun onCreate() {
        Log.i(TAG, "onCreate")
        registerReceiver()
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        if (running) return START_NOT_STICKY
        applyForeground()
        running = true
        val config = intent.getStringExtra("config")!!
        helper = newHelper(config)
        helper!!.start()
        val status = Intent(Status.SERVICE_START.action)
        status.`package` = applicationContext.packageName
        sendBroadcast(status)
        Toast.makeText(applicationContext, "Service started", Toast.LENGTH_SHORT).show()
        return START_NOT_STICKY
    }

    private fun newHelper(config: String):Helper {
        val helper = Helper(config, object : IStatusListener {
            override fun onExit() {
                if (!running) return
                stopSelf()
                Handler(applicationContext.mainLooper).post {
                    Toast.makeText(
                        applicationContext,
                        "spaceship exited ${if (failed) "with internal error" else "unexpectedly"}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onError() {
                failed = true
            }
        })
        return helper
    }

    private fun applyForeground(): Boolean {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(serviceChannel)
        if (!notificationManager.areNotificationsEnabled()) {
            Toast.makeText(
                this,
                "Notifications not enabled, please allow it for running foreground service",
                Toast.LENGTH_SHORT
            ).show()
            return false
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
                foregroundServiceBehavior = Notification.FOREGROUND_SERVICE_IMMEDIATE
            }
        }.build()
        startForeground(NOTIFICATION_ID, notification)
        return true
    }


    override fun onDestroy() {
        running = false
        helper!!.stop()
        unregisterReceiver(receiver)
        val intent = Intent(Status.SERVICE_STOP.action)
        intent.`package` = applicationContext.packageName
        sendBroadcast(intent)
        Toast.makeText(applicationContext, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    private fun registerReceiver(){
        val filter = IntentFilter(Message.ACQUIRE_SERVICE_STATUS.action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }else {
            ContextCompat.registerReceiver(this, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    inner class Receiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive: ${intent.action}")
            if (intent.action != Message.ACQUIRE_SERVICE_STATUS.action) return
            val status = Intent(Status.SERVICE_OK.action)
            status.`package` = applicationContext.packageName
            sendBroadcast(status)
        }
    }
}