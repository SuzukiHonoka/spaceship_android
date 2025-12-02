package org.starx.spaceship.ui.logs

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogsViewModel : ViewModel() {
    private val _logs = MutableLiveData<String>()
    val logs: LiveData<String> = _logs

    private var collectJob: Job? = null
    private var logProcess: Process? = null

    companion object {
        const val TAG = "LogsViewModel"
        const val MAX_BUFFER_SIZE = 100000
        const val TRIM_TO_SIZE = 50000
    }

    fun setLogs(logData: String) {
        _logs.value = logData
    }

    fun startLogCollection(tag: String?) {
        // Don't start if already collecting
        if (collectJob?.isActive == true) {
            Log.d(TAG, "Log collection already active")
            return
        }

        collectJob = viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val logBuffer = StringBuilder()
                var reader: BufferedReader? = null
                
                try {
                    val command = tag?.let {
                        arrayOf("/system/bin/logcat", "-v", "time", "-T", "1", "-s", it)
                    } ?: arrayOf("/system/bin/logcat", "-v", "time", "-T", "1")

                    logProcess = Runtime.getRuntime().exec(command)
                    reader = BufferedReader(InputStreamReader(logProcess!!.inputStream))

                    var line: String?
                    while (isActive && logProcess?.isAlive == true) {
                        line = reader.readLine()
                        line?.let {
                            logBuffer.append(it).append("\n")

                            // Prevent buffer from growing too large
                            if (logBuffer.length > MAX_BUFFER_SIZE) {
                                val newStart = logBuffer.length - TRIM_TO_SIZE
                                logBuffer.delete(0, newStart)
                                logBuffer.insert(0, "... (log truncated) ...\n")
                            }

                            withContext(Dispatchers.Main) {
                                _logs.value = logBuffer.toString()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading logcat", e)
                    val errorMsg = "Error reading logs: ${e.message}\n"
                    logBuffer.append(errorMsg)

                    withContext(Dispatchers.Main) {
                        _logs.value = logBuffer.toString()
                    }
                } finally {
                    try {
                        reader?.close()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error closing reader", e)
                    }
                    
                    try {
                        logProcess?.destroy()
                    } catch (e: Exception) {
                        Log.w(TAG, "Error destroying process", e)
                    }
                    logProcess = null
                }
            }
        }
    }

    fun stopLogCollection() {
        Log.d(TAG, "Stopping log collection")
        
        // Cancel the collection job
        collectJob?.cancel()
        collectJob = null
        
        // Stop and cleanup the logcat process
        try {
            logProcess?.destroy()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping logcat process", e)
        } finally {
            logProcess = null
        }
        
        // Clear logs
        _logs.value = ""
    }

    override fun onCleared() {
        super.onCleared()
        Log.d(TAG, "ViewModel cleared")
        stopLogCollection()
    }
}