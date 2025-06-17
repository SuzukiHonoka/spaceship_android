package org.starx.spaceship.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import java.net.Inet4Address
import java.net.InetAddress

data class WifiNetworkInfo(
    val ipAddresses: List<InetAddress>?, // Kept this from your original data class
)

class Connectivity(private val context: Context) {
    private var wifiNetworkCallback: ConnectivityManager.NetworkCallback? = null
    // To store the latest capabilities and link properties for the current Wi-Fi network
    private var currentWifiNetwork: Network? = null
//    private var currentWifiCapabilities: NetworkCapabilities? = null
    private var currentWifiLinkProperties: LinkProperties? = null
    private var listener: ((WifiNetworkInfo?) -> Unit)? = null
    private var foundWifiForThisRequest = false

    companion object {
        const val TAG = "Connectivity"
    }

    fun discoverWifiNetworkInfo(listener: (WifiNetworkInfo?) -> Unit) {
        this.listener = listener
        this.foundWifiForThisRequest = false // Reset for new discovery
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        wifiNetworkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Error unregistering previous network callback", e)
            }
        }
        // Reset current states
        currentWifiNetwork = null
//        currentWifiCapabilities = null
        currentWifiLinkProperties = null

        val wifiNetworkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()

        wifiNetworkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                // DO NOT call getNetworkCapabilities or getLinkProperties here.
                // Store the network and wait for onCapabilitiesChanged / onLinkPropertiesChanged.
                Log.d(TAG, "Wi-Fi network available (candidate): $network")
                // We only care about the *first* Wi-Fi network that becomes available for this request session
                if (!foundWifiForThisRequest) {
                    currentWifiNetwork = network
                }
                // The actual processing will happen in onCapabilitiesChanged and onLinkPropertiesChanged
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                super.onCapabilitiesChanged(network, networkCapabilities)

                // Only proceed if this is the Wi-Fi network we are tracking for this request
                if (network != currentWifiNetwork) {
                    Log.w(TAG, "Capabilities changed for an unexpected network: $network. Current target: $currentWifiNetwork")
                    return // Ignore changes for networks we are not tracking
                }

                // Check if it is actually a Wi-Fi network.
                val hasTransportWifi = networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                Log.d(TAG, "Wi-Fi capabilities changed for $network. Has TRANSPORT_WIFI: $hasTransportWifi")
                if (hasTransportWifi) {
//                    currentWifiCapabilities = networkCapabilities
//                    // If LinkProperties are already available, try to process.
//                    // Otherwise, wait for onLinkPropertiesChanged.
//                    currentWifiLinkProperties?.let { props ->
//                        processWifiNetworkUpdate(network, networkCapabilities, props)
//                    }
                    return
                }

                // It was our network, but it's no longer Wi-Fi (highly unlikely, but good to handle)
                clearCurrentWifiStateAndNotify()
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                Log.d(TAG, "Wi-Fi link properties changed for $network")
                // Only proceed if this is the Wi-Fi network we are tracking.
                if (network == currentWifiNetwork) {
                    currentWifiLinkProperties = linkProperties
                    // If Capabilities are already available, try to process.
                    // Otherwise, wait for onCapabilitiesChanged.
//                    currentWifiCapabilities?.let { caps ->
//                        processWifiNetworkUpdate(network, caps, linkProperties)
//                    }
                    processWifiNetworkUpdate(network, linkProperties)
                }
            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.d(TAG, "Wi-Fi network lost: $network")
                if (network == currentWifiNetwork) {
                    // The specific Wi-Fi network we were reporting was lost
                    clearCurrentWifiStateAndNotify()
                }
            }
        }

        connectivityManager.registerNetworkCallback(wifiNetworkRequest, wifiNetworkCallback!!)
        Log.d(TAG, "Registered Wi-Fi network callback.")
    }

    private fun processWifiNetworkUpdate(
        network: Network,
        linkProperties: LinkProperties
    ) {
        // Ensure we only process and call the listener once per discoverWifiNetworkInfo call
        if (foundWifiForThisRequest) {
            // If you want continuous updates, you'd remove this check and adjust listener expectations
            Log.d(TAG, "Already found Wi-Fi for this request. Ignoring further updates for now. Network: $network")
            return
        }

        // Defensive check, though onAvailable should ensure currentWifiNetwork is set.
        if (currentWifiNetwork == null || network != currentWifiNetwork) {
            Log.w(TAG, "processWifiNetworkUpdate called for an unexpected network: $network. Current target: $currentWifiNetwork")
            return
        }

        Log.d(TAG, "Processing Wi-Fi update for network: $network")
        foundWifiForThisRequest = true // Mark that we've processed it for this request

        val wifiIpAddresses = mutableListOf<InetAddress>()
        linkProperties.linkAddresses.forEach { linkAddress ->
            val address = linkAddress.address
            // Your original code filters for non-loopback. Good.
            // You might also want to specifically filter for Inet4Address if that's what you need.
            if (!address.isLoopbackAddress && address is Inet4Address) { // Example: Only IPv4
                wifiIpAddresses.add(address)
            }
        }


        Log.d(TAG, "Wi-Fi Info: IPs=${wifiIpAddresses}")
        listener?.invoke(WifiNetworkInfo(ipAddresses = wifiIpAddresses.toList()))
    }

    private fun clearCurrentWifiStateAndNotify() {
        if (foundWifiForThisRequest || currentWifiNetwork != null) { // Only notify if we previously found something or were tracking
            Log.d(TAG, "Clearing Wi-Fi state and notifying listener.")
            listener?.invoke(null) // Notify that the Wi-Fi info is no longer valid
        }
        currentWifiNetwork = null
//        currentWifiCapabilities = null
        currentWifiLinkProperties = null
        // foundWifiForThisRequest is reset when discoverWifiNetworkInfo is called again
    }

    fun stopWifiNetworkDiscovery() {
        wifiNetworkCallback?.let {
            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                connectivityManager.unregisterNetworkCallback(it)
                Log.d(TAG, "Unregistered Wi-Fi network callback.")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering network callback", e)
            }
            wifiNetworkCallback = null
        }
        // Clear out state when stopping discovery explicitly
        currentWifiNetwork = null
//        currentWifiCapabilities = null
        currentWifiLinkProperties = null
        listener = null
        foundWifiForThisRequest = false
    }
}