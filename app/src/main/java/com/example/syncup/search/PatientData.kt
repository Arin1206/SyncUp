package com.example.syncup.search

data class PatientData(
    val id: String,
    val name: String,
    val age: String,
    val gender: String,
    val heartRate: String,
    val systolicBP: String,
    val diastolicBP: String,
    val photoUrl: String,
    val email: String,
    val phoneNumber: String,
    var isAssigned: Boolean = false
)
