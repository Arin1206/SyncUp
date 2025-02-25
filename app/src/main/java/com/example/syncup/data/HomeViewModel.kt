package com.example.syncup.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class HomeViewModel : ViewModel() {
    private val _bloodPressure = MutableLiveData<BloodPressure>()
    val bloodPressure: LiveData<BloodPressure> get() = _bloodPressure

    fun setBloodPressure(bp: BloodPressure) {
        _bloodPressure.postValue(bp)
    }
}
