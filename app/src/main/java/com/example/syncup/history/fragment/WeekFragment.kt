package com.example.syncup.history.fragment

import HealthData
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.adapter.WeekHealthAdapter
import com.example.syncup.chart.WeekChartView
import com.example.syncup.model.WeekHealthItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WeekFragment : Fragment() {

    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView
    private lateinit var weekChartView: WeekChartView
    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: WeekHealthAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_week, container, false)

        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = WeekHealthAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        weekChartView = view.findViewById(R.id.heartRateChart)

        fetchHealthData()

        return view
    }

    private fun fetchHealthData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userId = currentUser.uid
        firestore.collection("patient_heart_rate")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e("WeekFragment", "Error fetching health data", error)
                    return@addSnapshotListener
                }

                if (documents == null || documents.isEmpty) {
                    Log.w("WeekFragment", "No health data found in Firestore")
                    return@addSnapshotListener
                }

                Log.d("WeekFragment", "Total documents retrieved: ${documents.size()}")

                val weekMap = LinkedHashMap<String, MutableList<HealthData>>()

                for (doc in documents) {
                    val heartRate = doc.getLong("heartRate")?.toInt() ?: 0
                    if (heartRate == 0) continue

                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                    val timestamp = doc.getString("timestamp") ?: continue

                    val weekNumber = getWeekOfMonth(timestamp)

                    Log.d("WeekFragment", "Data: HR=$heartRate, BP=$systolicBP/$diastolicBP, Battery=$batteryLevel, Week=$weekNumber")

                    val healthData = HealthData(
                        heartRate = heartRate,
                        bloodPressure = "$systolicBP/$diastolicBP",
                        batteryLevel = batteryLevel,
                        timestamp = timestamp,
                        fullTimestamp = timestamp
                    )

                    weekMap.getOrPut(weekNumber) { mutableListOf() }.add(healthData)
                }

                val currentMonthYear = getCurrentMonthYear()
                val firstWeekOfMarch = getFirstWeekOfCurrentMonth() // Mendapatkan rentang tanggal Week 1 di bulan Maret
                val filteredWeekMap = weekMap.filterKeys { week ->
                    val extractedMonthYear = extractMonthYearFromWeek(week)
                    val weekStartEnd = extractStartEndDateFromWeek(week)

                    Log.d("WeekFragment", "Checking week: $week, Month Extracted: $extractedMonthYear, Current: $currentMonthYear")

                    // Pastikan Week 1 juga termasuk dalam filter
                    extractedMonthYear == currentMonthYear || weekStartEnd.first.contains("Mar") || weekStartEnd.second.contains("Mar")
                }

                val chartData = filteredWeekMap.mapValues { (_, dataList) ->
                    dataList.map { it.heartRate }.average().toInt()
                }

                weekChartView.setData(chartData)
                weekChartView.invalidate()
                Log.d("WeekFragment", "Chart data set: $chartData")

                val sortedWeeks = filteredWeekMap.toSortedMap(compareByDescending { it })

                val groupedItems = mutableListOf<WeekHealthItem>()

                val latestWeek = filteredWeekMap.keys.maxByOrNull { week ->
                    extractStartEndDateFromWeek(week).first
                }  // Ambil minggu terbaru berdasarkan tanggal mulai

                val currentWeekData = latestWeek?.let { filteredWeekMap[it] }

                if (!currentWeekData.isNullOrEmpty()) {
                    val avgHeartRate = currentWeekData.map { it.heartRate }.average().toInt()
                    val avgSystolicBP = currentWeekData.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
                    val avgDiastolicBP = currentWeekData.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
                    val avgBatteryLevel = currentWeekData.map { it.batteryLevel }.average().toInt()

                    // Update UI
                    avgHeartRateTextView.text = "$avgHeartRate"
                    avgBloodPressureTextView.text = "$avgSystolicBP/$avgDiastolicBP"
                    avgBatteryTextView.text = "$avgBatteryLevel%"

                    Log.d("WeekFragment", "Updated Avg: HR=$avgHeartRate, BP=$avgSystolicBP/$avgDiastolicBP, Battery=$avgBatteryLevel, Week=$latestWeek")
                } else {
                    Log.w("WeekFragment", "No data found for the latest week ($latestWeek) to calculate averages.")
                }



                for ((week, dataList) in sortedWeeks) {
                    val avgHeartRate = dataList.map { it.heartRate }.average().toInt()
                    val avgSystolicBP = dataList.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
                    val avgDiastolicBP = dataList.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
                    val avgBatteryLevel = dataList.map { it.batteryLevel }.average().toInt()

                    val avgHealthData = HealthData(
                        heartRate = avgHeartRate,
                        bloodPressure = "$avgSystolicBP/$avgDiastolicBP",
                        batteryLevel = avgBatteryLevel,
                        timestamp = week,
                        fullTimestamp = week
                    )

                    groupedItems.add(WeekHealthItem.WeekHeader(week))
                    groupedItems.add(WeekHealthItem.DataItem(avgHealthData))
                }

                healthDataAdapter.updateData(groupedItems)
            }
    }
    private fun extractStartEndDateFromWeek(weekText: String): Pair<String, String> {
        return try {
            val regex = """\((\d{2} \w{3} \d{4}) - (\d{2} \w{3} \d{4})\)""".toRegex()
            val matchResult = regex.find(weekText)

            if (matchResult != null) {
                val startDate = matchResult.groupValues[1]
                val endDate = matchResult.groupValues[2]
                return Pair(startDate, endDate)
            }

            Log.e("WeekParsing", "Failed to extract start/end date from: $weekText")
            Pair("01 Jan 1900", "01 Jan 1900") // Return nilai default aman
        } catch (e: Exception) {
            Log.e("WeekParsing", "Error extracting start/end date from week: ${e.message}")
            Pair("01 Jan 1900", "01 Jan 1900") // Return nilai default aman
        }
    }


    private fun getFirstWeekOfCurrentMonth(): Pair<String, String> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        // Geser ke hari Senin pertama dalam bulan ini
        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }

        val startOfWeek = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        // Tambahkan 6 hari untuk mendapatkan akhir minggu
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endOfWeek = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        Log.d("WeekCalculation", "First Week of Current Month: $startOfWeek - $endOfWeek")

        return Pair(startOfWeek, endOfWeek)
    }



    private fun getCurrentMonthYear(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
    }

    private fun extractMonthYearFromWeek(weekText: String): String {
        return try {
            val regex = """\((\d{2} \w{3} \d{4}) - (\d{2} \w{3} \d{4})\)""".toRegex()
            val matchResult = regex.find(weekText)

            if (matchResult != null) {
                val startMonthYear = matchResult.groupValues[1].split(" ").takeLast(2).joinToString(" ")
                val endMonthYear = matchResult.groupValues[2].split(" ").takeLast(2).joinToString(" ")

                val currentMonthYear = getCurrentMonthYear()

                // Jika minggu ini berakhir di bulan yang sedang berjalan, anggap sebagai bulan berjalan
                return if (endMonthYear == currentMonthYear) endMonthYear else startMonthYear
            }

            Log.e("WeekParsing", "Failed to extract month from: $weekText")
            "Unknown Month"
        } catch (e: Exception) {
            Log.e("WeekParsing", "Error extracting month from week: ${e.message}")
            "Unknown Month"
        }
    }

    private fun getWeekOfMonth(timestamp: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp) ?: return "Unknown Week"
        val calendar = Calendar.getInstance().apply { time = date }

        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        val month = calendar.get(Calendar.MONTH)

        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)  // Set ke awal minggu
        val startDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        calendar.add(Calendar.DAY_OF_WEEK, 6)  // Tambah 6 hari ke akhir minggu
        val endDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        val isPartOfCurrentMonth = (calendar.get(Calendar.MONTH) == month)
        val weekLabel = if (isPartOfCurrentMonth) "Week $weekOfMonth ($startDate - $endDate)"
        else "Week 1 ($startDate - $endDate)"  // Jika masuk ke Maret, tetap dihitung Week 1

        Log.d("WeekCalculation", "Computed Week: $weekLabel, Original Date: $timestamp")

        return weekLabel
    }




}
