package org.starx.spaceship.ui.logs

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

class LogsViewModel : ViewModel() {
    private val _logs = MutableLiveData<String>()
    val logs: LiveData<String> = _logs

    private var collectJob: Job? = null

    companion object {
        const val TAG = "LogsViewModel"
    }

    fun startLogCollection(tag: String?) {
        // Don't start if already collecting
        if (collectJob?.isActive == true) return

        collectJob = MainScope().launch {
            withContext(Dispatchers.IO) {
                val logBuffer = StringBuilder()
                try {
                    val command = if (tag != null) {
                        arrayOf("/system/bin/logcat", "-v", "raw", "-s", tag)
                    } else {
                        arrayOf("/system/bin/logcat", "-v", "raw")
                    }

                    val process = Runtime.getRuntime().exec(command)
                    val reader = BufferedReader(InputStreamReader(process.inputStream))

                    var line: String?
                    while (isActive) {
                        line = reader.readLine()
                        if (line != null) {
                            logBuffer.append(line).append("\n")

                            // Prevent buffer from growing too large
                            if (logBuffer.length > 100000) {
                                val newStart = logBuffer.length - 50000
                                logBuffer.delete(0, newStart)
                            }

                            withContext(Dispatchers.Main) {
                                _logs.value = logBuffer.toString()
                            }
                        }
                    }
                    process.destroy()
                } catch (e: Exception) {
                    Log.e(TAG, "Error reading logcat", e)
                    val errorMsg = "Error reading logs: ${e.message}\n"
                    logBuffer.append(errorMsg)

                    withContext(Dispatchers.Main) {
                        _logs.value = logBuffer.toString()
                    }
                }
            }
        }
    }

    fun stopLogCollection() {
        _logs.value = ""
        collectJob?.cancel()
        collectJob = null
    }

    override fun onCleared() {
        super.onCleared()
        stopLogCollection()
    }
}