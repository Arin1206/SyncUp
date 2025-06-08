package com.example.syncup.model

import com.example.syncup.search.PatientData

sealed class MonthHealthItemDoctor {
    data class MonthHeader(val month: String) : MonthHealthItemDoctor()
    data class DataItem(val patientData: PatientData) : MonthHealthItemDoctor()
}
