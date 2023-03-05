package org.starx.spaceship.ui.notifications

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NotificationsViewModel : ViewModel() {
    private val _logs = MutableLiveData<String>().apply{
        value = ""
    }
    val logs = _logs

    fun addLogs(line: String){
        _logs.value += "$line\n"
    }

    fun logCatOutput() = liveData(viewModelScope.coroutineContext + Dispatchers.IO) {
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec("/system/bin/logcat -c")
        }
        withContext(Dispatchers.IO) {
            Runtime.getRuntime().exec("/system/bin/logcat")
        }
            .inputStream
            .bufferedReader()
            .useLines { lines -> lines.forEach { line -> emit(line)  }
            }
    }
}