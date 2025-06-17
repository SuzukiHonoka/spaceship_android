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
        if (connectivity != null) {
            Log.d(TAG, "Connectivity discovery already started.")
            // Even if we already have a connectivity instance, we should ensure the callback is active
            isDiscoveryActive = true
            return
        }

        connectivity = Connectivity(context)
        isDiscoveryActive = true

        connectivity!!.discoverWifiNetworkInfo { info ->
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                // Check if callback should still be processed
                if (!isDiscoveryActive) {
                    return@post
                }

                if (info != null && info.ipAddresses != null && info.ipAddresses.isNotEmpty()) {
                    // Find the first IPv4 address
                    val ip = info.ipAddresses.firstOrNull { it is java.net.Inet4Address }?.hostAddress ?: ""
                    _localIp.postValue(ip) // Using postValue for safety from background threads
                    return@post
                }

                _localIp.postValue("")
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