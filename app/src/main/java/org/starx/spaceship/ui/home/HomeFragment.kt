package org.starx.spaceship.ui.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import org.starx.spaceship.action.Message
import org.starx.spaceship.action.Status
import org.starx.spaceship.databinding.FragmentHomeBinding
import org.starx.spaceship.service.Background
import org.starx.spaceship.store.Settings
import spaceship_aar.Spaceship_aar


class HomeFragment : Fragment() {
    companion object {
        const val TAG = "Home"
    }

    private val receiver = Receiver()

    private var running = false
    private var ignoreState = false

    private var _binding: FragmentHomeBinding? = null
    private lateinit var homeViewModel: HomeViewModel
    private lateinit var serviceSwitch: MaterialSwitch

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
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
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (!ignoreState) toggleSwitch(isChecked)
        }
        registerReceiver()
        acquireServiceStatus()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        requireContext().unregisterReceiver(receiver)
    }

    private fun acquireServiceStatus() {
        Log.i(TAG, "acquireServiceStatus")
        // if service is alive, it will broadcast status back
        val intent = Intent(Message.ACQUIRE_SERVICE_STATUS.action)
        intent.`package` = requireContext().packageName
        requireContext().sendBroadcast(intent)
    }

    private fun toggleSwitch(isChecked: Boolean) {
        // start
        if (isChecked) {
            if (!running) startService()
        }else{
            // close
            stopService()
        }
    }

    private fun setSwitch(isChecked: Boolean) {
        ignoreState = true
        serviceSwitch.isChecked = isChecked
        ignoreState = false
    }

    private fun startService() {
        val ctx = requireContext()
        // get config
        val settings = Settings(ctx)
        if (!settings.validate()) {
            setSwitch(false)
            Snackbar.make(
                requireActivity().findViewById(android.R.id.content),
                "Configuration not valid", Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        setRunning()
        val configString = settings.toJson()
        val s = Intent(ctx, Background::class.java)
        s.putExtra("config", configString)
        ctx.startForegroundService(s)
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service started", Snackbar.LENGTH_LONG
        ).show()
    }

    private fun stopService() {
        setNotRunning()
        val ctx = requireContext()
        val s = Intent(ctx, Background::class.java)
        ctx.stopService(s)
        Snackbar.make(
            requireActivity().findViewById(android.R.id.content),
            "Service stopped", Snackbar.LENGTH_LONG
        ).show()
    }

    private fun registerReceiver(){
        val filter = IntentFilter()
        val actions = setOf(
            Status.SERVICE_OK.action,
            Status.SERVICE_START.action,
            Status.SERVICE_STOP.action
        )
        actions.forEach { action -> filter.addAction(action) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        }else {
            ContextCompat.registerReceiver(requireContext(), receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
    }

    private fun setRunning(){
        running = true
        setSwitch(true)
        homeViewModel.setProfile(Settings(requireContext()).profileName)
    }

    private fun setNotRunning(){
        running = false
        setSwitch(false)
        homeViewModel.setProfile("")
    }

    inner class Receiver: BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "onReceive: ${intent.action}")
            when (intent.action)
            {
                Status.SERVICE_START.action -> setRunning()
                Status.SERVICE_OK.action -> setRunning()
                Status.SERVICE_STOP.action -> setNotRunning()
            }
        }
    }
}