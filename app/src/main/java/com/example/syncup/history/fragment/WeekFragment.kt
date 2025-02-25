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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.adapter.WeekHealthAdapter
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
            .get()
            .addOnSuccessListener { documents ->
                val weekMap = LinkedHashMap<String, MutableList<HealthData>>()

                for (doc in documents) {
                    val heartRate = doc.getLong("heartRate")?.toInt() ?: 0
                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                    val timestamp = doc.getString("timestamp") ?: continue

                    val weekNumber = getWeekOfMonth(timestamp)

                    val healthData = HealthData(
                        heartRate = heartRate,
                        bloodPressure = "$systolicBP/$diastolicBP",
                        batteryLevel = batteryLevel,
                        timestamp = timestamp,
                        fullTimestamp = timestamp
                    )

                    weekMap.getOrPut(weekNumber) { mutableListOf() }.add(healthData)
                }

                val sortedWeeks = weekMap.toSortedMap()
                val groupedItems = mutableListOf<WeekHealthItem>()

                for ((week, dataList) in sortedWeeks) {
                    groupedItems.add(WeekHealthItem.WeekHeader(week))
                    groupedItems.addAll(dataList.map { WeekHealthItem.DataItem(it) })
                }

                // **Menghitung rata-rata untuk minggu ini**
                val currentWeek = getCurrentWeek()
                val currentWeekData = weekMap[currentWeek] ?: emptyList()

                if (currentWeekData.isNotEmpty()) {
                    val avgHeartRate = currentWeekData.map { it.heartRate }.average().toInt()
                    val avgSystolicBP = currentWeekData.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
                    val avgDiastolicBP = currentWeekData.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
                    val avgBatteryLevel = currentWeekData.map { it.batteryLevel }.average().toInt()

                    avgHeartRateTextView.text = "$avgHeartRate BPM"
                    avgBloodPressureTextView.text = "$avgSystolicBP/$avgDiastolicBP"
                    avgBatteryTextView.text = "$avgBatteryLevel%"
                }

                healthDataAdapter.updateData(groupedItems)
            }
    }

    private fun getWeekOfMonth(timestamp: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp) ?: return "Unknown Week"
        val calendar = Calendar.getInstance().apply { time = date }
        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        val month = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
        return "Week $weekOfMonth ($month)"
    }

    private fun getCurrentWeek(): String {
        val calendar = Calendar.getInstance()
        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        val month = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
        return "Week $weekOfMonth ($month)"
    }
}
