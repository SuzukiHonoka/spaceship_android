package org.starx.spaceship.util

import android.app.ActivityManager
import android.content.Context


class ServiceUtil {
    companion object {
        @Suppress("DEPRECATION")
        fun isMyServiceRunning(ctx: Context, serviceClass: Class<*>): Boolean {
            val manager = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
            return false
        }
    }
}