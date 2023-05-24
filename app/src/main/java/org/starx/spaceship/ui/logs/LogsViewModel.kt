package org.starx.spaceship.ui.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LogsViewModel : ViewModel() {
    fun logCatOutput() = liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec("/system/bin/logcat -c")
        }
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec("/system/bin/logcat")
        }
            .inputStream
            .bufferedReader()
            .useLines { lines ->
                lines.forEach { line -> emit(line) }
            }
    }
}