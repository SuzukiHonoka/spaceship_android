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
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
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
    // AtomicBoolean fields are inherently thread-safe — use val, the reference never changes.
    private val proxyIsRunning = AtomicBoolean(false)
    private val proxyIsFailed = AtomicBoolean(false)
    // These are written/read from different threads — @Volatile for visibility.
    @Volatile private var proxyJob: Job? = null
    @Volatile private var launcher: spaceship_aar.LauncherWrapper? = null
    private var launcherConfig: String? = null

    // VPN tunnel state
    // vpnStopSignal is the single source of truth: created when VPN starts, completed
    // (from any thread) when we want the VPN job to stop. No polling needed.
    @Volatile private var vpnStopSignal: CompletableDeferred<Unit>? = null
    @Volatile private var vpnJob: Job? = null
    // Written by the VPN coroutine (IO), read/nulled by stopVpnService (any thread).
    @Volatile private var vpnInterface: ParcelFileDescriptor? = null
    @Volatile private var engine: spaceship_aar.Engine? = null
    
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
        
        @Volatile
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

        // Cache for bypass-route IpPrefixes, keyed by "$bypassRule|$enableIpv6".
        // Parsing 5500+ CIDRs from disk and creating IpPrefix objects takes ~750ms and
        // causes a ~5ms GC pause. The CIDR files never change at runtime, so the cache
        // persists for the process lifetime and is safe to reuse across service restarts.
        @Volatile
        private var ipPrefixCache: Pair<String, List<IpPrefix>>? = null
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
        
        // Initialize coroutine scope — SupervisorJob ensures one child failure
        // doesn't cancel sibling jobs (proxy vs VPN are independent)
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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

        // Stop any previously running services (e.g., unexpected system restart of a sticky
        // service). On a clean first start nothing is running — skip the lock overhead.
        if (proxyIsRunning.get() || vpnStopSignal != null) {
            stopProxyService()
            stopVpnService()
        }

        // Start proxy service (always needed)
        startProxyService()
        
        // Start VPN service if enabled
        if (enableVpnMode) {
            startVpnService()
        }
        
        acquireWakeLock()
        
        val message = if (enableVpnMode) "VPN Service started" else "Proxy Service started"
        Toast.makeText(applicationContext, message, Toast.LENGTH_SHORT).show()
        
        return START_STICKY
    }

    private fun startProxyService() {
        if (launcherConfig.isNullOrEmpty()) return

        if (proxyIsRunning.compareAndSet(false, true)) {
            proxyJob = serviceScope?.launch {
                Log.d(TAG, "Starting proxy service (config length: ${launcherConfig?.length})")
                // Capture whether we exited naturally (not via cancellation / explicit stop)
                val selfStopped: Boolean
                try {
                    launcher = spaceship_aar.Spaceship_aar.newLauncher()
                    launcher!!.launchFromString(launcherConfig)  // blocks until Go exits or Stop() is called
                    // proxyIsRunning is set to false by stopProxyService() BEFORE launcher.stop()
                    // unblocks launchFromString. So if it's still true here, Go exited on its own.
                    // (We cannot use isActive: cancel() is called AFTER stop(), so isActive is
                    //  still true at this point even during an explicit stop.)
                    selfStopped = proxyIsRunning.get()
                } catch (e: Exception) {
                    proxyIsFailed.set(true)
                    Log.e(TAG, "Start proxy failed: ${e.message}")
                    proxyIsRunning.set(false)
                    return@launch
                }

                // Proxy service stopped
                proxyIsRunning.set(false)

                val msg = "Proxy service exited ${
                    if (proxyIsFailed.get()) "with internal error"
                    else if (selfStopped) "unexpectedly"
                    else "normally"
                }"
                Log.d(TAG, msg)

                // Use NonCancellable so cleanup runs even if coroutine was canceled
                withContext(NonCancellable + Dispatchers.Main) {
                    Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
                    if (selfStopped) {
                        // Proxy died on its own — bring everything down cleanly
                        if (vpnStopSignal != null) stopVpnService()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
            return
        }
        Log.d(TAG, "Proxy service is already started.")
    }

    private fun startVpnService() {
        if (vpnStopSignal != null) {
            Log.d(TAG, "VPN service is already started.")
            return
        }

        val signal = CompletableDeferred<Unit>()
        vpnStopSignal = signal

        vpnJob = serviceScope?.launch {
            val tunFd: Int
            try {
                tunFd = buildTunnel()
                startTun2socks(tunFd)
            } catch (e: Exception) {
                Log.e(TAG, "VPN service error: $e")
                // VPN failed to start — tear everything down so the proxy doesn't
                // keep running headless and the UI switch resets correctly.
                stopVpnService()
                stopProxyService()
                withContext(NonCancellable + Dispatchers.Main) {
                    Toast.makeText(applicationContext, "VPN error: $e", Toast.LENGTH_SHORT).show()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@launch
            }

            // Suspend here until stopVpnService() fires the signal.
            // No polling — zero CPU overhead, instant wakeup.
            // Whoever fires the signal has already called stopVpnService() and
            // is responsible for service lifecycle — do NOT call stopVpnService()
            // again here or we double-close the VPN fd (fdsan SIGABRT).
            signal.await()
        }
    }

    private suspend fun buildTunnel(): Int {
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
        dnsServerSet.forEach {
            localTunnel.addDnsServer(it)
        }

        if (enableRemoteDns) {
            dnsServerSet.forEach {
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
        // establish() is a blocking JNI call — withTimeout alone won't interrupt it.
        // runInterruptible wraps it in a thread-interrupt-aware block so the timeout fires correctly.
        vpnInterface = withTimeoutOrNull(10_000) {
            runInterruptible { localTunnel.establish() }
        } ?: throw RuntimeException("VPN tunnel establishment timed out or returned null")

        Log.i(TAG, "tunnel established successfully")

        // Dup the fd and transfer the dup to Go with no fdsan owner.
        // Java keeps the original ParcelFileDescriptor (tagged by Android's fdsan).
        // Go gets an untagged dup it can freely close — no SIGABRT on engine.stop().
        return vpnInterface!!.dup().detachFd()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun applyBypassRules(builder: Builder) {
        val cacheKey = "${bypassRule}|${enableIpv6}"
        val cached = ipPrefixCache

        val prefixes: List<IpPrefix>
        if (cached != null && cached.first == cacheKey) {
            Log.i(TAG, "applying ${cached.second.size} bypass routes (cached, skipping disk I/O)")
            prefixes = cached.second
        } else {
            // Pre-size to avoid the ~13 ArrayList reallocations growing to 5506 entries.
            val list = ArrayList<IpPrefix>(6000)

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
                                        list.add(parseCidrToIpPrefix(line))
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
                        list.add(parseCidrToIpPrefix(cidr))
                    } catch (e: Exception) {
                        Log.e(TAG, "parse cidr failed: $e")
                    }
                }
            }

            Log.i(TAG, "applying bypass rule count: ${list.size}")
            // toList() returns an immutable snapshot safe to share across threads/restarts.
            prefixes = list.toList()
            ipPrefixCache = Pair(cacheKey, prefixes)
        }

        prefixes.forEach { builder.excludeRoute(it) }

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

    private fun startTun2socks(tunFd: Int) {
        val key = spaceship_aar.EngineKey()
        key.mark = 0
        key.mtu = 1500
        key.device = "fd://$tunFd"
        key.setInterface("")
        key.logLevel = "error"
        key.proxy = "socks5://127.0.0.1:$socksPort"
        key.restAPI = ""
        key.tcpSendBufferSize = ""
        key.tcpReceiveBufferSize = ""
        key.tcpModerateReceiveBuffer = false
        key.udpDisabled = true
        key.dnsAddr = if (enableRemoteDns) TUNNEL_ADDRESS_IPV4_DNS else ""
        Log.d(TAG, "Engine key: $key")
        engine = spaceship_aar.Engine()
        engine!!.insert(key)
        engine!!.start()
        Log.d(TAG, "VPN Engine started")
    }

    private fun stopProxyService() {
        Log.d(TAG, "Stopping proxy service")
        proxyIsRunning.set(false)

        // Stop the Go launcher FIRST (synchronously) so launchFromString unblocks.
        // Only then cancel the coroutine — otherwise the scope can be torn down
        // before the async stop-coroutine runs, permanently leaking Go goroutines.
        // Snapshot-and-null before calling stop() so a concurrent call sees null
        // and skips, preventing double-stop.
        val l = launcher
        launcher = null
        if (l != null) {
            try {
                l.stop()
                Log.d(TAG, "Proxy launcher stopped")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping proxy launcher: $e")
            }
        }

        proxyJob?.cancel()
        proxyJob = null
    }

    @Synchronized
    private fun stopVpnService() {
        Log.d(TAG, "Stopping VPN service")

        vpnStopSignal?.complete(Unit)
        vpnStopSignal = null

        vpnJob?.cancel()
        vpnJob = null

        // Snapshot references and null the fields BEFORE closing.
        // This makes the function idempotent: a concurrent or repeated call
        // will get null for both fields and skip the close entirely,
        // preventing the fdsan double-close SIGABRT.
        val eng = engine;       engine = null
        val iface = vpnInterface; vpnInterface = null

        try {
            eng?.stop()
            Log.d(TAG, "VPN engine stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping engine: $e")
        }

        try {
            iface?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing VPN interface: $e")
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
            return
        }

        // VPN stop is quick (closes fd + cancels job)
        stopVpnService()

        // Proxy stop (launcher.stop()) may block briefly while Go tears down connections.
        // Dispatch to background so the UI/binder thread is not stalled.
        serviceScope?.launch(Dispatchers.IO) {
            stopProxyService()
            withContext(NonCancellable + Dispatchers.Main) {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
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
        return isServiceRunning && vpnStopSignal != null
    }

    fun isRunning(): Boolean {
        return isServiceRunning && (proxyIsRunning.get() || vpnStopSignal != null)
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
