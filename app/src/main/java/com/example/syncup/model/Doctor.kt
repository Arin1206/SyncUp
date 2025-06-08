package com.example.syncup.model

data class Doctor(
    val name: String = "",
    val imageUrl: String = "",
    val doctorUid: String = "",  // Add doctorUid to the model
    val patientId: String = "",  // Add patientId to the model if necessary
    val phoneNumber: String = "",
    val patientName: String = ""// Add phoneNumber field if necessary
)
