package org.starx.spaceship.ui.notifications

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.starx.spaceship.databinding.FragmentNotificationsBinding

class NotificationsFragment : Fragment() {

    private var _binding: FragmentNotificationsBinding? = null
    private lateinit var logsView: TextView

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var autoScrolling = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val notificationsViewModel =
            ViewModelProvider(this)[NotificationsViewModel::class.java]
        _binding = FragmentNotificationsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        logsView = binding.logsText
        logsView.setOnClickListener {
            autoScrolling = false
            //Toast.makeText(requireContext(), "ok", Toast.LENGTH_SHORT).show()
        }

        val logScroll  = binding.logsScroll

        notificationsViewModel.logs.observe(viewLifecycleOwner){
            logs ->
            logsView.text = logs
            if (autoScrolling) logScroll.fullScroll(View.FOCUS_DOWN)
        }
        notificationsViewModel.logCatOutput().observe(viewLifecycleOwner){
            line ->
            if (line.contains("GoLog")) notificationsViewModel.addLogs(line)
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}