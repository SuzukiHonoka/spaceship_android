package org.starx.spaceship.helper

import android.util.Log

class Helper(private val config: String, private var listener: IStatusListener? = null) {

    companion object {
        const val TAG = "Helper"
    }

    private val launcher = spaceship_aar.Spaceship_aar.newLauncher()
    private val process = Thread {
        val ret = launcher.launchFromString(config)
        if (!ret) listener?.onError()
        listener?.onExit()
    }

    fun start() {
        if (process.isAlive) {
            Log.d(TAG, "process thread already running")
            return
        }
        process.start()
        Log.i(TAG, "process thread started")
    }

    fun stop() {
        if (!process.isAlive) {
            Log.d(TAG, "process thread not running")
            return
        }
        launcher.stop()
        process.interrupt()
        Log.i(TAG, "process thread stopped")
    }
}