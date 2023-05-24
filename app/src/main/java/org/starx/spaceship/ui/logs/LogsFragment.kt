package org.starx.spaceship.ui.logs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.starx.spaceship.databinding.FragmentLogsBinding

class LogsFragment : Fragment() {

    companion object{
        const val TAG = "LogsFragment"
    }
    private var _binding: FragmentLogsBinding? = null
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
        val logsViewModel =
            ViewModelProvider(this)[LogsViewModel::class.java]
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        logsView = binding.logsText
        logsView.setOnClickListener {
            autoScrolling = false
            Log.i(TAG, "autoScrolling disabled")
        }

        val logScroll = binding.logsScroll
        logsViewModel.logCatOutput().observe(viewLifecycleOwner) { line ->
            val mark = "GoLog   : "
            if (!line.contains(mark)) return@observe
            val log = line.substring(line.indexOf(mark)+mark.length)
            logsView.append("${log}\n")
            if (autoScrolling) logScroll.fullScroll(View.FOCUS_DOWN)
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}