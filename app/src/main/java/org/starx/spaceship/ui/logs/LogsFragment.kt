package org.starx.spaceship.ui.logs

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import org.starx.spaceship.databinding.FragmentLogsBinding

class LogsFragment : Fragment() {
    private var _binding: FragmentLogsBinding? = null

    private lateinit var logsViewModel: LogsViewModel
    private lateinit var logsView: TextView
    private lateinit var logScroll: ScrollView

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!
    private var autoScrolling = true
    private var lastDisplayedLength = 0  // Track what's already displayed

    companion object {
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
        logScroll = binding.logsScroll
        
        logsView.setOnClickListener {
            autoScrolling = false
            Log.i(TAG, "autoScrolling disabled")
        }
        
        // Double tap to re-enable auto scrolling
        logsView.setOnLongClickListener {
            autoScrolling = true
            Log.i(TAG, "autoScrolling re-enabled")
            logScroll.post {
                logScroll.fullScroll(View.FOCUS_DOWN)
            }
            true
        }

        // Observe logs LiveData
        logsViewModel.logs.observe(viewLifecycleOwner) { logs ->
            // Check if this is new content or a full reset
            if (logs.isEmpty()) {
                // Log cleared - reset everything
                logsView.text = ""
                lastDisplayedLength = 0
            } else if (logs.length < lastDisplayedLength) {
                // Buffer was trimmed in ViewModel - need to refresh completely
                logsView.text = logs
                lastDisplayedLength = logs.length
            } else if (logs.length > lastDisplayedLength) {
                // New content - append only the new part
                val newContent = logs.substring(lastDisplayedLength)
                logsView.append(newContent)
                lastDisplayedLength = logs.length
            }
            // If logs.length == lastDisplayedLength, no change needed
            
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
        
        // Reset tracking when fragment starts
        lastDisplayedLength = 0
        autoScrolling = true
        
        logsViewModel.startLogCollection(TAG_LOGS)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}