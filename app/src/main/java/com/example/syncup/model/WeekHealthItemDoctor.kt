package com.example.syncup.model

import HealthData
import com.example.syncup.search.PatientData

sealed class WeekHealthItemDoctor {
    data class WeekHeader(val week: String) : WeekHealthItemDoctor()
    data class DataItem(val data: PatientData) : WeekHealthItemDoctor()
}
