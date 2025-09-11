package org.starx.spaceship.ui.home

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.VpnService
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import org.starx.spaceship.databinding.FragmentHomeBinding
import org.starx.spaceship.service.UnifiedVPNService
import org.starx.spaceship.store.Runtime
import org.starx.spaceship.store.Settings
import org.starx.spaceship.util.Resource
import spaceship_aar.Spaceship_aar

class HomeFragment : Fragment() {
    // Unified service
    private var unifiedService: UnifiedVPNService? = null
    private var isServiceBound = false

    companion object {
        const val TAG = "Home"
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Unified Service Connected")
            try {
                val binder = service as? UnifiedVPNService.LocalBinder
                binder?.let {
                    unifiedService = it.getService()
                    isServiceBound = true
                    setRunning()
                } ?: run {
                    Log.e(TAG, "Failed to cast service binder")
                    setNotRunning()
                }
            } catch (e: ClassCastException) {
                Log.e(TAG, "Failed to cast service binder", e)
                setNotRunning()
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Unified Service Disconnected")
            unifiedService = null
            isServiceBound = false
            setNotRunning()
        }
    }

    private var _binding: FragmentHomeBinding? = null
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var serviceSwitch: MaterialSwitch

    private var vpnPrepareLauncher: ActivityResultLauncher<Intent>? = null
    private var pendingServiceIntent: Intent? = null

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Listener to trigger when the user toggles the switch
    private val checkedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            toggleSwitch(isChecked)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // construct result receiver for VPN preparation
        vpnPrepareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            // Check if fragment is still attached
            if (!isAdded || context == null) {
                Log.w(TAG, "Fragment not attached, ignoring VPN preparation result")
                return@registerForActivityResult
            }
            
            if (result.resultCode == Activity.RESULT_OK) {
                // VPN permission granted, now start the service
                Log.d(TAG, "VPN permission granted")
                pendingServiceIntent?.let { serviceIntent ->
                    requireContext().startForegroundService(serviceIntent)
                    bindUnifiedService()
                    pendingServiceIntent = null
                    Snackbar.make(
                        requireActivity().findViewById(android.R.id.content),
                        "Service started", Snackbar.LENGTH_LONG
                    ).show()
                }
            } else {
                // VPN permission denied, reset switch
                Log.w(TAG, "VPN permission denied")
                pendingServiceIntent = null
                setNotRunning()
                serviceSwitch.isEnabled = true
                Snackbar.make(
                    requireActivity().findViewById(android.R.id.content),
                    "VPN permission denied", Snackbar.LENGTH_SHORT
                ).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val card = binding.card
        card.setOnLongClickListener {
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                "spaceship core version: ${Spaceship_aar.getVersionCode()}",
                Snackbar.LENGTH_SHORT
            ).show()
            true
        }
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        val profile = binding.profile
        homeViewModel.profile.observe(viewLifecycleOwner) {
            profile.text = it
        }

        // Observe local IP changes
        homeViewModel.localIp.observe(viewLifecycleOwner) { ip ->
            binding.localIpCaption.visibility = if (ip.isEmpty()) View.GONE else View.VISIBLE
            binding.localIp.visibility = if (ip.isEmpty()) View.GONE else View.VISIBLE
            binding.localIp.text = ip
        }

        serviceSwitch = binding.serviceSwitch
        serviceSwitch.setOnCheckedChangeListener(null)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated")
        // Schedule the listener to be added after state restoration
        serviceSwitch.post {
            serviceSwitch.setOnCheckedChangeListener(checkedChangeListener)
        }
    }

    override fun onStart() {
        super.onStart()
        Log.d(TAG, "onStart")

        if (UnifiedVPNService.isServiceRunning) {
            if (!isServiceBound) bindUnifiedService()

            // Restart connectivity discovery if needed
            val settings = Settings(requireContext())
            if (settings.allowOther) {
                homeViewModel.startConnectivityDiscovery(requireContext())
            }
        }
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        unbindUnifiedService()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun toggleSwitch(isChecked: Boolean) {
        Log.d(TAG, "toggleSwitch: $isChecked")

        // check if resource is extracted
        if (Runtime(requireContext()).resourceVersion < Resource.VERSION) {
            if (isChecked) setSwitch(false)
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                "Please wait for the resource extraction", Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // start
        if (isChecked) {
            if (unifiedService == null || unifiedService?.isRunning() == false) startService()
            return
        }

        if (unifiedService != null && unifiedService!!.isRunning()) stopService()
    }

    private fun setSwitch(isChecked: Boolean) {
        // Remove the listener, preventing the toggleSwitch callback
        serviceSwitch.setOnCheckedChangeListener(null)
        serviceSwitch.isChecked = isChecked
        // Re-add the listener to allow the user to toggle
        serviceSwitch.setOnCheckedChangeListener(checkedChangeListener)
    }

    private fun startService() {
        Log.i(TAG, "home: starting unified service")
        serviceSwitch.isEnabled = false
        val ctx = requireContext()

        // get and validate config
        val settings = Settings(ctx)
        if (!settings.validate()) {
            setNotRunning()
            serviceSwitch.isEnabled = true
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                "Configuration not valid", Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        // check connectivity if needed
        if (settings.allowOther) {
            Log.d(TAG, "Starting connectivity discovery")
            homeViewModel.startConnectivityDiscovery(ctx)
        }

        val configString = settings.toJson()
        val serviceIntent = Intent(ctx, UnifiedVPNService::class.java).apply {
            putExtra("config", configString)
            putExtra("port", settings.socksPort)
            putExtra("remote_dns", settings.enableRemoteDns)
            putExtra("ipv6", settings.enableIpv6)
            putExtra("bypass", settings.bypass)
            putExtra("vpn_mode", settings.enableVPN)
        }

        // check if we need to prepare vpn service
        if (settings.enableVPN) {
            val intent = VpnService.prepare(ctx)
            // prepare if not prepared before
            if (intent != null) {
                // Store the service intent for later use after VPN preparation
                pendingServiceIntent = serviceIntent
                vpnPrepareLauncher!!.launch(intent)
                // Don't show success message yet, wait for VPN permission
                serviceSwitch.isEnabled = true
                return
            } else {
                // VPN already prepared, start service
                ctx.startForegroundService(serviceIntent)
                bindUnifiedService()
            }
        } else {
            // No VPN needed, just start the proxy service
            ctx.startForegroundService(serviceIntent)
            bindUnifiedService()
        }

        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service started", Snackbar.LENGTH_LONG
        ).show()

        serviceSwitch.isEnabled = true
    }

    private fun stopService() {
        Log.i(TAG, "home: stopping unified service")
        serviceSwitch.isEnabled = false

        // stop connectivity if needed
        homeViewModel.stopConnectivityDiscovery()

        val ctx = requireContext()

        // Tell the service to stop itself cleanly first
        unifiedService?.stopService(false)
        
        // stop unified service
        unbindUnifiedService()
        val serviceIntent = Intent(ctx, UnifiedVPNService::class.java)
        ctx.stopService(serviceIntent)

        // change status
        setNotRunning()

        // final prompt
        serviceSwitch.isEnabled = true
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service stopped", Snackbar.LENGTH_LONG
        ).show()
    }

    private fun unbindUnifiedService() {
        if (isServiceBound) {
            try {
                requireActivity().unbindService(serviceConnection)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Service was not bound", e)
            } finally {
                isServiceBound = false
                unifiedService = null
            }
        }
    }

    private fun bindUnifiedService() {
        if (!isServiceBound) {
            val serviceIntent = Intent(requireContext(), UnifiedVPNService::class.java)
            try {
                val bound = requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
                if (!bound) {
                    Log.w(TAG, "Failed to bind to service")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error binding to service", e)
            }
        }
    }

    private fun setRunning(){
        setSwitch(true)
        homeViewModel.setProfile(Settings(requireContext()).profileName)
    }

    private fun setNotRunning(){
        setSwitch(false)
        homeViewModel.setProfile("")
    }
}
