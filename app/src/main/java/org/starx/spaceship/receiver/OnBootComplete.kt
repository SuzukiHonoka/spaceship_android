package org.starx.spaceship.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import org.starx.spaceship.service.Background
import org.starx.spaceship.store.Settings

class OnBootComplete : BroadcastReceiver() {
    companion object {
        const val TAG = "OnBootComplete"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = Settings(context)
        if (!settings.autoStart && !settings.validate()) return
        val configString = settings.toJson()
        val s = Intent(context, Background::class.java)
        s.putExtra("config", configString)
        context.startForegroundService(s)
        Toast.makeText(context, "Auto start complete", Toast.LENGTH_SHORT).show()
    }
}