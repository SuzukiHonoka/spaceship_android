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
    private var _binding: FragmentLogsBinding? = null

    private lateinit var logsViewModel: LogsViewModel
    private lateinit var logsView: TextView

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var autoScrolling = true

    companion object{
        const val TAG = "LogsFragment"
        const val TAG_LOGS = "GoLog"
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        logsViewModel =
            ViewModelProvider(this)[LogsViewModel::class.java]
        _binding = FragmentLogsBinding.inflate(inflater, container, false)
        val root: View = binding.root
        logsView = binding.logsText
        logsView.setOnClickListener {
            autoScrolling = false
            Log.i(TAG, "autoScrolling disabled")
        }

        val logScroll = binding.logsScroll

        // Observe logs LiveData
        logsViewModel.logs.observe(viewLifecycleOwner) { logs ->
            logsView.text = logs
            if (autoScrolling) {
                logScroll.post {
                    logScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        logsViewModel.startLogCollection(TAG_LOGS)
        return root
    }

    override fun onStart() {
        super.onStart()
        // Start log collection when fragment is visible
        Log.i(TAG, "Starting log collection")
        logsViewModel.startLogCollection(TAG_LOGS)
    }

    override fun onStop() {
        super.onStop()
        // Stop log collection when fragment is not visible
        Log.i(TAG, "Stopping log collection")
        logsViewModel.stopLogCollection()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}