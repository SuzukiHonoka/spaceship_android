package org.starx.spaceship.ui.home

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import org.starx.spaceship.util.Connectivity

class HomeViewModel : ViewModel() {
    companion object {
        const val TAG = "HomeViewModel"
    }

    private val _profile = MutableLiveData<String>().apply {
        value = ""
    }
    val profile: LiveData<String> = _profile
    fun setProfile(profile: String) {
        _profile.value = profile
    }

    private val _localIp = MutableLiveData<String>().apply {
        value = ""
    }
    val localIp: LiveData<String> = _localIp
    fun setLocalIp(ip: String) {
        _localIp.value = ip
    }

    private var connectivity: Connectivity? = null
    private var isDiscoveryActive = false

    fun startConnectivityDiscovery(context: android.content.Context) {
        if (connectivity != null && isDiscoveryActive) {
            Log.d(TAG, "Connectivity discovery already started.")
            return
        }

        // Stop existing discovery first to prevent multiple instances
        stopConnectivityDiscovery()

        connectivity = Connectivity(context)
        isDiscoveryActive = true

        connectivity!!.discoverWifiNetworkInfo { info ->
            // Use main thread handler safely
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                // Check if ViewModel is still active and callback should be processed
                if (!isDiscoveryActive || connectivity == null) {
                    return@post
                }

                if (info != null && info.ipAddresses != null && info.ipAddresses.isNotEmpty()) {
                    // Find the first IPv4 address
                    val ip = info.ipAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: ""
                    setLocalIp(ip) // Use setter for consistency
                } else {
                    setLocalIp("")
                }
            }
        }
    }

    fun stopConnectivityDiscovery() {
        isDiscoveryActive = false
        connectivity?.stopWifiNetworkDiscovery()
        connectivity = null
        setLocalIp("")
    }

    override fun onCleared() {
        super.onCleared()
        stopConnectivityDiscovery()
    }
}