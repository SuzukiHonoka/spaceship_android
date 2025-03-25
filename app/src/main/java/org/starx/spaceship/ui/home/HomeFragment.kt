package org.starx.spaceship.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import org.starx.spaceship.databinding.FragmentHomeBinding
import org.starx.spaceship.service.Background
import org.starx.spaceship.store.Settings
import spaceship_aar.Spaceship_aar


class HomeFragment : Fragment() {
    companion object {
        const val TAG = "Home"
    }

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

    private var _binding: FragmentHomeBinding? = null
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var serviceSwitch: MaterialSwitch

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    // Listener to trigger when the user toggles the switch
    private val checkedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            toggleSwitch(isChecked)
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
    }

    override fun onStop() {
        super.onStop()
        Log.d(TAG, "onStop")
        unbindBackgroundService()
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
        val s = Intent(ctx, Background::class.java)
        s.putExtra("config", configString)
        ctx.startForegroundService(s)

        if (!isServiceBound) bindBackgroundService()
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service started", Snackbar.LENGTH_LONG
        ).show()
    }

    private fun stopService() {
        Log.i(TAG, "home: stopping service")
        setNotRunning()
        val ctx = requireContext()
        val s = Intent(ctx, Background::class.java)
        unbindBackgroundService()
        ctx.stopService(s)
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service stopped", Snackbar.LENGTH_LONG
        ).show()
    }

    private fun unbindBackgroundService(){
        if (isServiceBound) {
            requireActivity().unbindService(serviceConnection)
            isServiceBound = false
            backgroundService = null
        }
    }

    private fun  bindBackgroundService() {
        val serviceIntent = Intent(requireContext(), Background::class.java)
        requireContext().bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
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