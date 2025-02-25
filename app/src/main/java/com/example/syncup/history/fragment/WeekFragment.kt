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

        // Pindahkan Scroll Listener ke sini setelah inisialisasi RecyclerView
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItems = layoutManager.itemCount
                val visibleItems = layoutManager.findLastVisibleItemPosition()

                if (visibleItems >= totalItems / 2) {
                    recyclerView.smoothScrollToPosition(0)
                }
            }
        })

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
                    if (heartRate == 0) continue  // 🚀 Skip jika heart rate = 0

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
                    // **Hitung rata-rata untuk week ini**
                    val avgHeartRate = dataList.map { it.heartRate }.average().toInt()
                    val avgSystolicBP = dataList.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
                    val avgDiastolicBP = dataList.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
                    val avgBatteryLevel = dataList.map { it.batteryLevel }.average().toInt()

                    // **Simpan hanya 1 list per week dengan rata-rata**
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

                // **Menghitung rata-rata untuk minggu ini**
                val currentWeek = getCurrentWeek()
                val currentWeekData = weekMap[currentWeek] ?: emptyList()

                if (currentWeekData.isNotEmpty()) {
                    val avgHeartRate = currentWeekData.map { it.heartRate }.average().toInt()
                    val avgSystolicBP = currentWeekData.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
                    val avgDiastolicBP = currentWeekData.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
                    val avgBatteryLevel = currentWeekData.map { it.batteryLevel }.average().toInt()

                    avgHeartRateTextView.text = "$avgHeartRate"
                    avgBloodPressureTextView.text = "$avgSystolicBP/$avgDiastolicBP"
                    avgBatteryTextView.text = "$avgBatteryLevel%"
                }

                healthDataAdapter.updateData(groupedItems)

                // 🔹 Update tinggi RecyclerView
                updateRecyclerViewHeight()
            }
    }

    private fun updateRecyclerViewHeight() {
        recyclerView.post {
            val constraintLayout = view?.findViewById<ConstraintLayout>(R.id.main) ?: return@post
            val constraintSet = ConstraintSet()
            constraintSet.clone(constraintLayout)

            if (healthDataAdapter.itemCount > 0) {
                // Jika ada data, atur tinggi WRAP_CONTENT agar menyesuaikan isi
                constraintSet.constrainHeight(R.id.recycler_view_health, ConstraintLayout.LayoutParams.WRAP_CONTENT)
            } else {
                // Jika tidak ada data, biarkan tingginya menyesuaikan dengan parent (agar tidak kosong)
                constraintSet.constrainHeight(R.id.recycler_view_health, 0)
            }

            constraintSet.applyTo(constraintLayout)
        }
    }
    private fun getWeekOfMonth(timestamp: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp) ?: return "Unknown Week"
        val calendar = Calendar.getInstance().apply { time = date }

        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)

        // Tentukan awal dan akhir minggu
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(calendar.time)

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        return "Week $weekOfMonth ($startDate - $endDate)"
    }

    private fun getCurrentWeek(): String {
        val calendar = Calendar.getInstance()
        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)

        // Tentukan awal dan akhir minggu
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(calendar.time)

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        return "Week $weekOfMonth ($startDate - $endDate)"
    }

}
