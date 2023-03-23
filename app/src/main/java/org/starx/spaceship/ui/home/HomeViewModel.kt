package org.starx.spaceship.ui.home

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val _profile = MutableLiveData<String>().apply {
        value = ""
    }
    val profile = _profile

    fun setProfile(profile: String) {
        _profile.value = profile
    }
}