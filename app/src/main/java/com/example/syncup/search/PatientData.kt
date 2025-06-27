package com.example.syncup.search

data class PatientData(
    val id: String,
    val name: String,
    val age: String,
    val gender: String,
    var heartRate: String,
    var systolicBP: String,
    var diastolicBP: String,
    val photoUrl: String,
    val email: String,
    val phoneNumber: String,
    var isAssigned: Boolean = false,
    val fullTimestamp: String? = null,
    var batteryLevel: String? = null
)
