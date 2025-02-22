package com.example.syncup.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.example.syncup.data.HeartRateRepository

class HeartRateViewModel : ViewModel() {
    val heartRate: LiveData<Int> = HeartRateRepository.heartRateLiveData
}
