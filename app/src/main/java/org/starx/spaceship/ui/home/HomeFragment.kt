package org.starx.spaceship.ui.home

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.materialswitch.MaterialSwitch
import com.google.android.material.snackbar.Snackbar
import org.starx.spaceship.databinding.FragmentHomeBinding
import org.starx.spaceship.service.Background
import org.starx.spaceship.store.Settings
import org.starx.spaceship.util.ServiceUtil


class HomeFragment : Fragment() {
    companion object{
        const val TAG = "Home"
    }

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
        homeViewModel = ViewModelProvider(this)[HomeViewModel::class.java]
        val profile = binding.profile
        homeViewModel.profile.observe(viewLifecycleOwner){
            profile.text = it
        }
        serviceSwitch = binding.serviceSwitch
        setState()
        serviceSwitch.setOnCheckedChangeListener { _, isChecked ->
            toggleSwitch(isChecked)
        }
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onResume() {
        super.onResume()
        setState()
    }

    private fun setState() {
        val running = serviceRunning()
        setSwitch(running)
        if (running) homeViewModel.setProfile(Settings(requireContext()).profileName)
    }

    private fun serviceRunning() = ServiceUtil.isMyServiceRunning(requireContext(), Background::class.java)

    private fun toggleSwitch(isChecked: Boolean) {
        if (ignoreState) return
        // start
        if (isChecked) {
            if (serviceRunning()) return
            startService()
            return
        }
        // close
        stopService()
    }

    private fun setSwitch(isChecked: Boolean){
        ignoreState = true
        serviceSwitch.isChecked=isChecked
        ignoreState = false
    }

    private fun startService(){
        val ctx = requireContext()
        // get config
        val settings = Settings(ctx)
        if (!settings.validate()) {
            setSwitch(false)
            Snackbar.make(requireActivity().findViewById(android.R.id.content),
                "Configuration not valid", Snackbar.LENGTH_SHORT).show()
            return
        }
        homeViewModel.setProfile(settings.profileName)
        val configString = settings.toJson()
        Log.i(TAG, "startService: \n$configString")
        val s = Intent(ctx, Background::class.java)
        s.putExtra("config", configString)
        ctx.startForegroundService(s)
        Snackbar.make(requireActivity().findViewById(android.R.id.content),
            "Service started", Snackbar.LENGTH_LONG).show()
    }

    private fun stopService(){
        homeViewModel.setProfile("")
        val ctx = requireContext()
        val s = Intent(ctx, Background::class.java)
        ctx.stopService(s)
        Snackbar.make(requireActivity().findViewById(android.R.id.content),
            "Service stopped", Snackbar.LENGTH_LONG).show()
    }
}