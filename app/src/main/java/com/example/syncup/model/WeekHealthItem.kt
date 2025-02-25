package com.example.syncup.model

import HealthData

sealed class WeekHealthItem {
    data class WeekHeader(val weekTitle: String) : WeekHealthItem()
    data class DataItem(val healthData: HealthData) : WeekHealthItem()
}