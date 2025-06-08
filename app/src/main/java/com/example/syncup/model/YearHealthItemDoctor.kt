package com.example.syncup.model

import com.example.syncup.search.PatientData

sealed class YearHealthItemDoctor {
    data class YearHeader(val year: String) : YearHealthItemDoctor()
    data class YearData(val patientData: PatientData) : YearHealthItemDoctor()
}
