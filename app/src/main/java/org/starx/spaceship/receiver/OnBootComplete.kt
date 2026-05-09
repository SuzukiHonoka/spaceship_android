package org.starx.spaceship.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import org.starx.spaceship.service.UnifiedVPNService
import org.starx.spaceship.store.Settings
import org.starx.spaceship.util.ServiceUtil

class OnBootComplete : BroadcastReceiver() {
    companion object {
        const val TAG = "OnBootComplete"
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val settings = Settings(context)
        if (!settings.autoStart || !settings.validate()) return

        val dnsPort = if (settings.enableRemoteDns) ServiceUtil.findFreePort() else null
        val configString = settings.toJson(dnsPort)
        val serviceIntent =
            Intent(context, UnifiedVPNService::class.java).apply {
                putExtra("config", configString)
                putExtra("port", settings.socksPort)
                putExtra("remote_dns", settings.enableRemoteDns)
                putExtra("ipv6", settings.enableIpv6)
                putExtra("bypass", settings.bypass)
                putExtra("vpn_mode", settings.enableVPN)
                if (dnsPort != null) putExtra("dns_port", dnsPort)
            }

        context.startForegroundService(serviceIntent)
        Log.i(TAG, "onReceive: unified service started")
        Toast.makeText(context, "Auto start complete", Toast.LENGTH_SHORT).show()
    }
}
