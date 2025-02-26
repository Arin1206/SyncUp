package com.example.syncup.model
sealed class YearHealthItem {
    data class YearHeader(val year: String) : YearHealthItem()
    data class YearData(val avgHeartRate: Int, val avgBloodPressure: String, val avgBattery: Int) : YearHealthItem()
}
