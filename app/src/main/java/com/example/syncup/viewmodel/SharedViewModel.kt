package com.example.syncup.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.syncup.search.PatientData

class SharedViewModel : ViewModel() {
    private val _offlinePatients = MutableLiveData<List<PatientData>>()
    val offlinePatients: LiveData<List<PatientData>> = _offlinePatients

    private var lastOfflineData: List<PatientData> = emptyList()

    fun updateOfflinePatients(patients: List<PatientData>) {
        lastOfflineData = patients
        _offlinePatients.value = patients
    }

    fun getLastOfflinePatients(): List<PatientData> = lastOfflineData
}
