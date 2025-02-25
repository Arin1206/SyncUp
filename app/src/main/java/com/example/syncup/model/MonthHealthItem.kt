package com.example.syncup.model
sealed class MonthHealthItem {
    data class MonthHeader(val month: String) : MonthHealthItem()
    data class MonthData(val avgHeartRate: Int, val avgBloodPressure: String, val avgBattery: Int) : MonthHealthItem()
}

