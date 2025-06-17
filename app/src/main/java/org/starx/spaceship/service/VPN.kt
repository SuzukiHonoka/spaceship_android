package org.starx.spaceship.service

import android.content.Intent
import android.net.IpPrefix
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.starx.spaceship.util.Resource
import org.starx.spaceship.util.ServiceUtil
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class VPN: VpnService() {
    private val binder = LocalBinder()
    private var isRunning = AtomicBoolean(false)

    private var serviceJob: Job? = null
    private var serviceScope: CoroutineScope? = null

    private var socksPort: Int? = null
    private var enableIpv6: Boolean? = null
    private var bypassRule: String? = null

    private var engine: spaceship_aar.Engine? = null
    private var vpnInterface: ParcelFileDescriptor? = null

    companion object {
        const val TAG = "VPN"
        const val CHANNEL_ID = "Spaceship VPN"
        const val CHANNEL_NAME = "Background VPN indicator"
        const val NOTIFICATION_ID = 1
        var isServiceRunning = false
            private set


        // Tunnel specific settings

        const val TUNNEL_ADDRESS_IPV4_NETWORK = "172.16.0.0"
        const val TUNNEL_ADDRESS_IPV4_NETWORK_PREFIX_LENGTH = 24
        const val TUNNEL_ADDRESS_IPV4 = "172.16.0.2"
        const val TUNNEL_ADDRESS_IPV6 = "fdbd:fb1f:7f90::2"

        const val TUNNEL_DNS_IPV4_PRIMARY = "8.8.8.8"
        const val TUNNEL_DNS_IPV4_SECONDARY = "8.8.4.4"
        const val TUNNEL_DNS_IPV6_PRIMARY = "2001:4860:4860::8888"
        const val TUNNEL_DNS_IPV6_SECONDARY = "2001:4860:4860::8844"
    }

    inner class LocalBinder : Binder() {
        fun getService(): VPN = this@VPN
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        // create notification channel
        ServiceUtil.createNotificationChannel(applicationContext, CHANNEL_ID, CHANNEL_NAME)

        // set running status
        isServiceRunning = true
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        return super.onUnbind(intent)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Toast.makeText(applicationContext, "Service max-run hour reached, shutting down..", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        if (intent == null) {
            Log.d(TAG, "intent is null")
            return START_NOT_STICKY
        }

        socksPort = intent.getIntExtra("port", -1)
        val isValid = (socksPort in 0..65535)
        if (!isValid) {
            Log.d(TAG, "invalid port: $socksPort")
            return START_NOT_STICKY
        }
        enableIpv6 = intent.getBooleanExtra("ipv6", false)
        bypassRule = intent.getStringExtra("bypass")

        // start foreground service
        val notification = ServiceUtil.buildNotification(applicationContext, CHANNEL_ID, "VPN Service is running..")
        startForeground(NOTIFICATION_ID, notification)

        try {
            buildTunnel()
        }catch (e: Exception) {
            Log.e(TAG, "buildTunnel: $e")
            Toast.makeText(applicationContext, "buildTunnel error: $e", Toast.LENGTH_SHORT).show()
            return START_NOT_STICKY
        }

        startTun2socks()
        return START_STICKY
    }

    private fun stop(){
        Log.d(TAG, "stopping")
        vpnInterface?.close()
        vpnInterface = null
        serviceJob?.cancel()
        //engine.Engine.stop()
    }

    fun stopVpn(){
        // Stop VPN interface and engine
        stop()

        // Remove the notification
        stopForeground(STOP_FOREGROUND_REMOVE)
        // Stop the service
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        stop()
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.d(TAG, "onRevoke")
        stop()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun parseCidrToIpPrefix(cidr: String): IpPrefix {
        // parse cidr string like: 8.8.8.8/32 get the ip block and prefix, convert to IpPrefix object
        val parts = cidr.split("/")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid CIDR format: $cidr")
        }
        val ipAddress = parts[0]
        val prefixLength = parts[1].toInt()
        val inetAddress = InetAddress.getByName(ipAddress)
        return IpPrefix(inetAddress, prefixLength)
    }

    private fun buildTunnel() {
        val builder = Builder()

        val localTunnel = builder
            .setMetered(false)
            .setMtu(1500)
            .addAddress(TUNNEL_ADDRESS_IPV4, 24)
            .addRoute("0.0.0.0", 0)
            .addDnsServer(TUNNEL_DNS_IPV4_PRIMARY)
            .addDnsServer(TUNNEL_DNS_IPV4_SECONDARY)
            .addDisallowedApplication(packageName)

        if (enableIpv6 == true) {
            localTunnel.addDnsServer(TUNNEL_DNS_IPV6_PRIMARY)
            localTunnel.addDnsServer(TUNNEL_DNS_IPV6_SECONDARY)
            localTunnel.addAddress(TUNNEL_ADDRESS_IPV6, 64)
            localTunnel.addRoute("::", 0)
        }

        if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !bypassRule.isNullOrEmpty()) {
            val ipPrefixList: MutableList<IpPrefix> = mutableListOf()

            if (bypassRule!!.contains("cn")) {
                Log.i(TAG, "adding bypass rule: cn")
                val fileList = mutableListOf(Resource.OPT_ASSET_CN_AGGREGATED_ZONE_V4)
                if (enableIpv6 == true) fileList += listOf(Resource.OPT_ASSET_CN_AGGREGATED_ZONE_V6)

                fileList.forEach { filename ->
                    val ins = Resource(applicationContext).getFile(filename)
                    val reader = ins.bufferedReader()
                    val lines = reader.readLines()
                    Log.d(TAG, "parse file $filename, cidr count: ${lines.size}")
                    for (line in lines) {
                        try {
                            val cidr = parseCidrToIpPrefix(line)
                            ipPrefixList.add(cidr)
                        }catch (e: Exception) {
                            Log.e(TAG, "parse cidr failed: $e")
                            continue
                        }
                    }
                    ins.close()
                    reader.close()
                }
            }

            if (bypassRule!!.contains("lan")) {
                Log.i(TAG, "adding bypass rule: lan")
                Resource.LAN_CIDR.forEach { cidr ->
                    try {
                        val ipPrefix = parseCidrToIpPrefix(cidr)
                        ipPrefixList.add(ipPrefix)
                    } catch (e: Exception) {
                        Log.e(TAG, "parse cidr failed: $e")
                    }
                }
            }

            Log.i(TAG, "applying bypass rule count: ${ipPrefixList.size}")
            ipPrefixList.forEach { ipPrefix ->
                localTunnel.excludeRoute(ipPrefix)
            }

            // preserve local-link
            Log.i(TAG, "applying local-link")
            localTunnel.addRoute(TUNNEL_ADDRESS_IPV4_NETWORK, TUNNEL_ADDRESS_IPV4_NETWORK_PREFIX_LENGTH)
        }

        Log.i(TAG, "establishing tunnel")
        vpnInterface = localTunnel.establish()
    }

    private fun startTun2socks() {
        serviceJob = Job()
        serviceScope = CoroutineScope(Dispatchers.IO + serviceJob!!)

        serviceScope!!.launch {
            try {
                val key = spaceship_aar.EngineKey()
                key.mark = 0
                key.mtu = 1500
                key.device = "fd://" + vpnInterface!!.fd
                key.setInterface("")
                key.logLevel = "error"
                key.proxy = "socks5://127.0.0.1:$socksPort"
                key.restAPI = ""
                key.tcpSendBufferSize = ""
                key.tcpReceiveBufferSize = ""
                key.tcpModerateReceiveBuffer = false
                Log.d(TAG, "Engine key: $key")

                engine = spaceship_aar.Engine()
                engine!!.insert(key)
                engine!!.start()
                Log.d(TAG, "Engine started")
                isRunning.set(true)
            } catch (e: Exception) {
                Log.d(TAG, "Engine error: $e")
                // toast message on main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "Engine error: $e", Toast.LENGTH_SHORT).show()
                }

                // stop service
                stopVpn()
            }
        }
    }
}