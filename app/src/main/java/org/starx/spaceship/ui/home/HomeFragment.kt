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
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import org.starx.spaceship.databinding.FragmentHomeBinding
import org.starx.spaceship.service.Background
import org.starx.spaceship.service.VPN
import org.starx.spaceship.store.Settings
import spaceship_aar.Spaceship_aar


class HomeFragment : Fragment() {
    companion object {
        const val TAG = "Home"
    }

    // Background service
    private var backgroundService: Background? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "Service Connected")
            val binder = service as Background.LocalBinder
            backgroundService = binder.getService()
            isServiceBound = true
            setRunning()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "Service Disconnected")
            backgroundService = null
            isServiceBound = false
            setNotRunning()
        }
    }

    // VPN service
    private var vpnService: VPN? = null
    private var isVpnServiceBound = false

    private val vpnServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            Log.d(TAG, "VPN Service Connected")
            val binder = service as VPN.LocalBinder
            vpnService = binder.getService()
            isVpnServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.d(TAG, "VPN Service Disconnected")
            vpnService = null
            isVpnServiceBound = false
        }
    }

    private var _binding: FragmentHomeBinding? = null
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var serviceSwitch: MaterialSwitch

    private var vpnPrepareLauncher: ActivityResultLauncher<Intent>? = null

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

        // construct result receiver
        vpnPrepareLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val ctx = requireContext()
            if (result.resultCode == Activity.RESULT_OK) {
                // todo: wait for spaceship process actually start
                starVpnService()
                return@registerForActivityResult
            }
            stopService()
            Toast.makeText(ctx, "VPN not prepared", Toast.LENGTH_SHORT).show()
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
        if (Background.isServiceRunning && !isServiceBound) bindBackgroundService()
        if (VPN.isServiceRunning && !isVpnServiceBound) bindVpnService()
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        unbindBackgroundService()
        unbindVpnService()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun toggleSwitch(isChecked: Boolean) {
        Log.d(TAG, "toggleSwitch: $isChecked")
        // start
        if (isChecked) {
            if (backgroundService == null || backgroundService?.isRunning() == false) startService()
            return
        }

        if (backgroundService != null && backgroundService!!.isRunning()) stopService()
    }

    private fun setSwitch(isChecked: Boolean) {
        // Remove the listener, preventing the toggleSwitch callback
        serviceSwitch.setOnCheckedChangeListener(null)
        serviceSwitch.isChecked = isChecked
        // Re-add the listener to allow the user to toggle
        serviceSwitch.setOnCheckedChangeListener(checkedChangeListener)
    }

    private fun startService() {
        Log.i(TAG, "home: starting service")
        serviceSwitch.isEnabled = false
        val ctx = requireContext()

        // get config
        val settings = Settings(ctx)
        if (!settings.validate()) {
            setNotRunning()
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                "Configuration not valid", Snackbar.LENGTH_SHORT
            ).show()
            return
        }

        val configString = settings.toJson()
        val backgroundIntent = Intent(ctx, Background::class.java)
        backgroundIntent.putExtra("config", configString)
        ctx.startForegroundService(backgroundIntent)
        bindBackgroundService()

        // check if we need to prepare vpn service
        if (settings.enableVPN) {
            val intent = VpnService.prepare(ctx)
            // prepare if not prepared before
            if (intent != null) vpnPrepareLauncher!!.launch(intent) else starVpnService()
        }

        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service started", Snackbar.LENGTH_LONG
        ).show()

        serviceSwitch.isEnabled = true
    }

    private fun stopService() {
        Log.i(TAG, "home: stopping service")
        serviceSwitch.isEnabled = false

        val ctx = requireContext()

        // stop background service
        //unbindBackgroundService()
        val backgroundIntent = Intent(ctx, Background::class.java)
        ctx.stopService(backgroundIntent)

        // stop vpn service if any
        if (isVpnServiceBound) {
            stopVpnService()
        }

        // change status
        setNotRunning()

        // final prompt
        serviceSwitch.isEnabled = true
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service stopped", Snackbar.LENGTH_LONG
        ).show()
    }

    private fun starVpnService(){
        Log.i(TAG, "home: starting vpn service")
        val ctx = requireContext()
        val vpnIntent = Intent(ctx, VPN::class.java).apply {
            putExtra("port", Settings(ctx).socksPort)
            putExtra("ipv6", Settings(ctx).enableIpv6)
            putExtra("bypass", Settings(ctx).bypass)
        }
        ctx.startForegroundService(vpnIntent)
        bindVpnService()
    }

    private fun stopVpnService(){
        Log.i(TAG, "home: stopping vpn service")
        //val ctx = requireContext()
        //unbindVpnService()
        //val vpnIntent = Intent(ctx, VPN::class.java)
        //ctx.stopService(vpnIntent)
        vpnService?.stopVpn()
    }

    private fun unbindBackgroundService(){
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection)
            isServiceBound = false
            backgroundService = null
        }
    }

    private fun unbindVpnService(){
        if (isVpnServiceBound) {
            requireActivity().unbindService(vpnServiceConnection)
            isVpnServiceBound = false
            vpnService = null
        }
    }

    private fun bindBackgroundService() {
        val serviceIntent = Intent(requireContext(), Background::class.java)
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun bindVpnService() {
        val serviceIntent = Intent(requireContext(), VPN::class.java)
        requireContext().bindService(serviceIntent, vpnServiceConnection, Context.BIND_AUTO_CREATE)
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