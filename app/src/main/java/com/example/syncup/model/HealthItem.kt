package com.example.syncup.model

import HealthData

sealed class HealthItem {
    data class DataItem(val healthData: HealthData) : HealthItem()
    data class DateHeader(val date: String) : HealthItem()
}
