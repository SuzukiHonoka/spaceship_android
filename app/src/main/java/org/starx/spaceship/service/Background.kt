package org.starx.spaceship.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starx.spaceship.util.ServiceUtil
import java.util.concurrent.atomic.AtomicBoolean


class Background : Service() {
    private var serviceJob: Job? = null
    private var serviceScope: CoroutineScope? = null
    private val binder = LocalBinder()

    private var isRunning = AtomicBoolean(false)
    private var isFailed = AtomicBoolean(false)

    private var launcher: spaceship_aar.LauncherWrapper? = null
    private var launcherConfig: String? = null

    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val TAG = "Background"
        const val WAKELOCK_TAG = "Background::ProxyWakeLock"
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

        // create notification channel
        ServiceUtil.createNotificationChannel(applicationContext, CHANNEL_ID, CHANNEL_NAME)

        // set service running status
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
                    launcher!!.launchFromString(launcherConfig)
                } catch (e: Exception) {
                    isFailed.set(true)
                    Log.e(TAG, "Start spaceship failed: ${e.message}")
                }

                // spaceship is stopped
                isRunning.set(false)

                // notify the user
                val msg = "spaceship exited ${if (isFailed.get()) "with internal error" else 
                    if (serviceJob?.isActive == true) "unexpectedly" else "normally"}"
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
        Log.d(TAG, "onStartCommand")
        if (intent == null) {
            Log.d(TAG, "intent is null")
            return START_NOT_STICKY
        }

        val config = intent.getStringExtra("config")
        launcherConfig = config

        if (launcherConfig.isNullOrEmpty()) START_NOT_STICKY

        // start foreground service
        val notification = ServiceUtil.buildNotification(applicationContext, CHANNEL_ID, "Service is running..")
        startForeground(NOTIFICATION_ID, notification)

        if (isRunning.get()) {
            Log.d(TAG,"Restarting spaceship")
            stopSpaceshipAndWait()
        }

        startSpaceship(startId)
        acquireWakeLock()
        Toast.makeText(applicationContext, "Service started", Toast.LENGTH_SHORT).show()
        return START_STICKY
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
        releaseWakeLock()

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

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock != null) {
            Log.d(TAG, "WakeLock already acquired: $wakeLock")
            return
        }

        Log.d(TAG, "Acquiring WakeLock")
        wakeLock =
            (getSystemService(POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        Log.d(TAG, "WakeLock acquired: $wakeLock")
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
                Log.d(TAG, "WakeLock released: $it")
            }
        }
        wakeLock = null
    }
}