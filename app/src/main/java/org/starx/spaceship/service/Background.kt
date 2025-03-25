package org.starx.spaceship.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starx.spaceship.MainActivity
import org.starx.spaceship.R
import java.util.concurrent.atomic.AtomicBoolean


class Background : Service() {
    private var serviceJob: Job? = null
    private var serviceScope: CoroutineScope? = null
    private val binder = LocalBinder()

    private var isRunning = AtomicBoolean(false)
    private var isFailed = AtomicBoolean(false)

    private var launcher: spaceship_aar.LauncherWrapper? = null
    private var launcherConfig: String? = null

    companion object {
        const val TAG = "Service"
        const val CHANNEL_ID = "Spaceship"
        const val CHANNEL_NAME = "Background indicator"
        const val NOTIFICATION_ID = 1
        var isServiceRunning = false
            private set
    }

    inner class LocalBinder : Binder() {
        fun getService(): Background = this@Background
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        isServiceRunning = true
    }

    private fun startSpaceship(startId: Int) {
        if (launcherConfig.isNullOrEmpty()) return

        serviceJob = Job()
        serviceScope = CoroutineScope(Dispatchers.IO + serviceJob!!)

        if (isRunning.compareAndSet(false, true)) {
            serviceScope!!.launch {
                Log.d(TAG, "Starting spaceship with config: $launcherConfig startId: $startId")
                try {
                    launcher = spaceship_aar.Spaceship_aar.newLauncher()
                    val ret = launcher!!.launchFromString(launcherConfig)
                    if (!ret) isFailed.set(true)
                } catch (e: Exception) {
                    isFailed.set(true)
                    Log.e(TAG, "Start spaceship failed: ${e.message}")
                }

                // spaceship is stopped
                isRunning.set(false)

                // notify the user
                val msg = "spaceship exited ${if (isFailed.get()) "with internal error" else "unexpectedly"}"
                Log.d(TAG, msg)

                // toast message on main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }

                // stop service
                stopForeground(STOP_FOREGROUND_REMOVE)
            }
            return
        }
        Log.d(TAG, "spaceship is already started.")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val config = intent.getStringExtra("config")
            launcherConfig = config
        }

        if (launcherConfig.isNullOrEmpty()) START_NOT_STICKY

        if (!applyForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }

        if (isRunning.get()) {
            Log.d(TAG,"Restarting spaceship")
            stopSpaceshipAndWait()
        }

        startSpaceship(startId)
        Toast.makeText(applicationContext, "Service started", Toast.LENGTH_SHORT).show()
        return START_STICKY
    }

    private fun createNotificationChannel(): Boolean {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        )
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(serviceChannel)
        if (!notificationManager.areNotificationsEnabled()) {
        Toast.makeText(
            this,
            "Notifications not enabled, please allow it for running foreground service",
            Toast.LENGTH_SHORT
        ).show()
        return false
        }
        return true
    }

    private fun buildNotification(): Notification {
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
        }
        return notification.build()
    }

    private fun applyForeground(): Boolean {
        if (!createNotificationChannel()) return false
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)
        return true
    }

    private fun stopSpaceshipAndWait() {
        stopSpaceship()
        // wait isRunning to false
        while (isRunning.get()) {
            Log.d(TAG, "Waiting for spaceship to stop")
            Thread.sleep(100)
        }
    }

    private fun stopSpaceship() {
        if (launcher != null) {
            launcher!!.stop()
        }
        serviceJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        isServiceRunning = false
        stopSpaceship()

        Toast.makeText(applicationContext, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)
    }

    fun isRunning(): Boolean {
        return isServiceRunning && isRunning.get()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Toast.makeText(applicationContext, "Service max-run hour reached, shutting down..", Toast.LENGTH_SHORT).show()
        stopSelf()
    }
}