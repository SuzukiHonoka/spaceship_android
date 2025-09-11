package org.starx.spaceship.service

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.net.IpPrefix
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.PowerManager
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.starx.spaceship.MainActivity
import org.starx.spaceship.util.Resource
import org.starx.spaceship.util.ServiceUtil
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Unified VPN Service that combines proxy (SOCKS5) server and VPN tunnel functionality
 * This eliminates the need for separate Background and VPN services, reducing resource usage
 * and avoiding Android's "noisy service" warnings.
 */
@SuppressLint("VpnServicePolicy")
class UnifiedVPNService : VpnService() {
    private val binder = LocalBinder()
    
    // Proxy (SOCKS5) server state
    private var proxyIsRunning = AtomicBoolean(false)
    private var proxyIsFailed = AtomicBoolean(false)
    private var proxyJob: Job? = null
    private var launcher: spaceship_aar.LauncherWrapper? = null
    private var launcherConfig: String? = null
    
    // VPN tunnel state
    private var vpnIsRunning = AtomicBoolean(false)
    private var vpnJob: Job? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var engine: spaceship_aar.Engine? = null
    
    // Service configuration
    private var socksPort: Int = 10818
    private var enableRemoteDns: Boolean = true
    private var enableIpv6: Boolean = false
    private var bypassRule: String? = null
    private var enableVpnMode: Boolean = false
    
    // Common resources
    private var serviceScope: CoroutineScope? = null
    private var wakeLock: PowerManager.WakeLock? = null

    companion object {
        const val TAG = "UnifiedVPNService"
        const val WAKELOCK_TAG = "UnifiedVPNService::ProxyWakeLock"
        const val CHANNEL_ID = "SpaceshipUnified"
        const val CHANNEL_NAME = "Spaceship VPN Service"
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

        const val TUNNEL_ADDRESS_IPV4_DNS = "127.0.0.1:58632"
    }

    inner class LocalBinder : Binder() {
        fun getService(): UnifiedVPNService = this@UnifiedVPNService
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")

        // Create notification channel
        ServiceUtil.createNotificationChannel(applicationContext, CHANNEL_ID, CHANNEL_NAME)

        // Set service running status
        isServiceRunning = true
        
        // Initialize coroutine scope
        serviceScope = CoroutineScope(Dispatchers.IO)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        
        if (intent == null) {
            Log.d(TAG, "intent is null")
            return START_NOT_STICKY
        }

        // Extract configuration from intent
        launcherConfig = intent.getStringExtra("config")
        socksPort = intent.getIntExtra("port", 10818)
        enableRemoteDns = intent.getBooleanExtra("remote_dns", true)
        enableIpv6 = intent.getBooleanExtra("ipv6", false)
        bypassRule = intent.getStringExtra("bypass")
        enableVpnMode = intent.getBooleanExtra("vpn_mode", false)

        // Validate configuration
        if (launcherConfig.isNullOrEmpty()) {
            Log.e(TAG, "Invalid launcher config")
            return START_NOT_STICKY
        }
        
        if (socksPort !in 1..65535) {
            Log.e(TAG, "Invalid SOCKS port: $socksPort")
            return START_NOT_STICKY
        }

        // Start foreground service with unified notification
        val notificationText = if (enableVpnMode) {
            "VPN and Proxy Service running"
        } else {
            "Proxy Service running"
        }
        val notification = ServiceUtil.buildNotification(applicationContext, CHANNEL_ID, notificationText)
        startForeground(NOTIFICATION_ID, notification)

        // Stop existing services if running
        stopProxyService()
        stopVpnService()

        // Start proxy service (always needed)
        startProxyService(startId)
        
        // Start VPN service if enabled
        if (enableVpnMode) {
            startVpnService()
        }
        
        acquireWakeLock()
        
        val message = if (enableVpnMode) "VPN Service started" else "Proxy Service started"
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        
        return START_STICKY
    }

    private fun startProxyService(startId: Int) {
        if (launcherConfig.isNullOrEmpty()) return

        if (proxyIsRunning.compareAndSet(false, true)) {
            proxyJob = serviceScope?.launch {
                Log.d(TAG, "Starting proxy service with config: $launcherConfig")
                try {
                    launcher = spaceship_aar.Spaceship_aar.newLauncher()
                    launcher!!.launchFromString(launcherConfig)
                } catch (e: Exception) {
                    proxyIsFailed.set(true)
                    Log.e(TAG, "Start proxy failed: ${e.message}")
                }

                // Proxy service stopped
                proxyIsRunning.set(false)

                // Notify the user
                val msg = "Proxy service exited ${
                    if (proxyIsFailed.get()) "with internal error" 
                    else if (proxyJob?.isActive == true) "unexpectedly" 
                    else "normally"
                }"
                Log.d(TAG, msg)

                // Toast message on main thread
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                }

                // If proxy fails and VPN is not running, stop the service
                if (!vpnIsRunning.get()) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            return
        }
        Log.d(TAG, "Proxy service is already started.")
    }

    private fun startVpnService() {
        if (vpnIsRunning.get()) {
            Log.d(TAG, "VPN service is already started.")
            return
        }

        vpnJob = serviceScope?.launch {
            try {
                buildTunnel()
                startTun2socks()
            } catch (e: Exception) {
                Log.e(TAG, "VPN service error: $e")
                withContext(Dispatchers.Main) {
                    Toast.makeText(applicationContext, "VPN error: $e", Toast.LENGTH_SHORT).show()
                }
                stopVpnService()
            }
        }
    }

    private suspend fun buildTunnel() {
        Log.i(TAG, "buildTunnel")

        // Check if VPN permission is granted
        val vpnIntent = prepare(this)
        if (vpnIntent != null) {
            Log.e(TAG, "VPN permission not granted")
            throw SecurityException("VPN permission required")
        }

        val builder = Builder()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        val localTunnel = builder
            .setSession("Spaceship VPN")
            .setConfigureIntent(pendingIntent)
            .setMetered(false)
            .setMtu(1500)
            .addAddress(TUNNEL_ADDRESS_IPV4, 24)
            .addRoute("0.0.0.0", 0)
            .addDisallowedApplication(packageName)

        val dnsServerSet = mutableSetOf(TUNNEL_DNS_IPV4_PRIMARY, TUNNEL_DNS_IPV4_SECONDARY)
        if (enableIpv6) {
            dnsServerSet.addAll(setOf(TUNNEL_DNS_IPV6_PRIMARY, TUNNEL_DNS_IPV6_SECONDARY))
        }
        dnsServerSet.forEach { it->
            localTunnel.addDnsServer(it)
        }

        if (enableRemoteDns) {
            dnsServerSet.forEach { it->
                localTunnel.addRoute(it, if (InetAddress.getByName(it) is Inet4Address) 32 else 128)
            }
        }

        if (enableIpv6) {
            localTunnel.addAddress(TUNNEL_ADDRESS_IPV6, 64)
            localTunnel.addRoute("::", 0)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !bypassRule.isNullOrEmpty()) {
            applyBypassRules(localTunnel)
        }

        Log.i(TAG, "establishing tunnel")
        // Add timeout for tunnel establishment
        vpnInterface = withTimeout(10000) { // 10 second timeout
            localTunnel.establish()
        } ?: throw RuntimeException("Failed to establish VPN tunnel")

        if (vpnInterface == null) {
            throw RuntimeException("Failed to establish VPN tunnel - interface is null")
        }

        Log.i(TAG, "tunnel established successfully")
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyBypassRules(builder: Builder) {
        val ipPrefixList: MutableList<IpPrefix> = mutableListOf()

        if (bypassRule!!.contains("cn")) {
            Log.i(TAG, "adding bypass rule: cn")
            val fileList = mutableListOf(Resource.OPT_ASSET_CN_AGGREGATED_ZONE_V4)
            if (enableIpv6) fileList += listOf(Resource.OPT_ASSET_CN_AGGREGATED_ZONE_V6)

            fileList.forEach { filename ->
                try {
                    Resource(applicationContext).getFile(filename).use { ins ->
                        ins.bufferedReader().use { reader ->
                            val lines = reader.readLines()
                            Log.d(TAG, "parse file $filename, cidr count: ${lines.size}")
                            
                            lines.forEach { line ->
                                try {
                                    val cidr = parseCidrToIpPrefix(line)
                                    ipPrefixList.add(cidr)
                                } catch (e: Exception) {
                                    Log.e(TAG, "parse cidr failed: $e")
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading bypass file $filename: $e")
                }
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
            builder.excludeRoute(ipPrefix)
        }

        // preserve local-link
        Log.i(TAG, "applying local-link")
        builder.addRoute(TUNNEL_ADDRESS_IPV4_NETWORK, TUNNEL_ADDRESS_IPV4_NETWORK_PREFIX_LENGTH)
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun parseCidrToIpPrefix(cidr: String): IpPrefix {
        val parts = cidr.split("/")
        if (parts.size != 2) {
            throw IllegalArgumentException("Invalid CIDR format: $cidr")
        }
        val ipAddress = parts[0]
        val prefixLength = parts[1].toInt()
        val inetAddress = InetAddress.getByName(ipAddress)
        return IpPrefix(inetAddress, prefixLength)
    }

    private fun startTun2socks() {
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
            key.udpDisabled = true // Disable UDP support by default
            key.dnsAddr = if (enableRemoteDns) TUNNEL_ADDRESS_IPV4_DNS else ""
            Log.d(TAG, "Engine key: $key")

            engine = spaceship_aar.Engine()
            engine!!.insert(key)
            engine!!.start()
            Log.d(TAG, "VPN Engine started")
            vpnIsRunning.set(true)
        } catch (e: Exception) {
            Log.e(TAG, "Engine error: $e")
            throw e
        }
    }

    private fun stopProxyService() {
        Log.d(TAG, "Stopping proxy service")
        
        // Set stopping flag first to prevent race conditions
        proxyIsRunning.set(false)
        
        // Cancel the proxy job
        proxyJob?.cancel()
        
        // Stop launcher asynchronously to avoid blocking
        launcher?.let { launcherRef ->
            serviceScope?.launch {
                try {
                    launcherRef.stop()
                    Log.d(TAG, "Proxy service stopped")
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping proxy: $e")
                } finally {
                    launcher = null
                }
            }
        } ?: run {
            launcher = null
        }
    }

    private fun stopVpnService() {
        Log.d(TAG, "Stopping VPN service")
        
        // Set stopping flag first to prevent race conditions
        vpnIsRunning.set(false)
        
        // Cancel VPN job first
        vpnJob?.cancel()
        
        // Stop engine
        try {
            engine?.let {
                // Note: Engine.stop() method might not be available
                // engine!!.stop()
                Log.d(TAG, "VPN engine stopped")
                engine = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine: $e")
        }
        
        // Close VPN interface
        try {
            vpnInterface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface: $e")
        } finally {
            vpnInterface = null
        }
    }

    fun stopService(stopVpnOnly: Boolean = false) {
        Log.d(TAG, "stopService called - stopVpnOnly: $stopVpnOnly")
        
        if (stopVpnOnly) {
            stopVpnService()
            // Update notification to reflect proxy-only mode
            val notification = ServiceUtil.buildNotification(
                applicationContext, 
                CHANNEL_ID, 
                "Proxy Service running"
            )
            startForeground(NOTIFICATION_ID, notification)
        } else {
            // Stop services asynchronously to avoid blocking
            stopVpnService()
            stopProxyService()
            
            // Schedule service shutdown to ensure it happens even if cleanup takes time
            serviceScope?.launch {
                try {
                    // Give a brief moment for cleanup, but don't wait too long
                    kotlinx.coroutines.delay(500) // 0.5 second max wait
                } catch (e: Exception) {
                    Log.e(TAG, "Error during service shutdown delay: $e")
                } finally {
                    withContext(Dispatchers.Main) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")

        isServiceRunning = false
        
        // Stop services synchronously in onDestroy to ensure proper cleanup
        try {
            stopVpnService()
            stopProxyService()
        } catch (e: Exception) {
            Log.e(TAG, "Error during service cleanup in onDestroy: $e")
        } finally {
            releaseWakeLock()
            // Cancel coroutine scope
            serviceScope?.cancel()
            serviceScope = null
        }

        Toast.makeText(applicationContext, "Service stopped", Toast.LENGTH_SHORT).show()
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.d(TAG, "onBind")
        return binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "onUnbind")
        // Return false to indicate we don't want onRebind to be called
        // This allows the service to be stopped cleanly without waiting for cleanup
        return false
    }

    override fun onRevoke() {
        super.onRevoke()
        Log.d(TAG, "onRevoke - VPN permission revoked")
        stopVpnService()
        
        // Continue with proxy-only mode
        val notification = ServiceUtil.buildNotification(
            applicationContext, 
            CHANNEL_ID, 
            "Proxy Service running (VPN disabled)"
        )
        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        Toast.makeText(applicationContext, "Service max-run hour reached, shutting down..", Toast.LENGTH_SHORT).show()
        stopSelf()
    }

    fun isProxyRunning(): Boolean {
        return isServiceRunning && proxyIsRunning.get()
    }

    fun isVpnRunning(): Boolean {
        return isServiceRunning && vpnIsRunning.get()
    }

    fun isRunning(): Boolean {
        return isServiceRunning && (proxyIsRunning.get() || vpnIsRunning.get())
    }

    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) {
            Log.d(TAG, "WakeLock already acquired: $wakeLock")
            return
        }

        Log.d(TAG, "Acquiring WakeLock")
        try {
            wakeLock = (getSystemService(POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG).apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
            Log.d(TAG, "WakeLock acquired: $wakeLock")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire WakeLock: $e")
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            try {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released: $it")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error releasing WakeLock: $e")
            }
        }
        wakeLock = null
    }
}
