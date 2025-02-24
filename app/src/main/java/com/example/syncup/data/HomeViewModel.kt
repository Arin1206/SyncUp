package com.example.syncup.data

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.syncup.data.BloodPressure
import com.example.syncup.data.BloodPressureRepository

class HomeViewModel : ViewModel() {
    val bloodPressure: LiveData<BloodPressure> = BloodPressureRepository.bloodPressureLiveData
}