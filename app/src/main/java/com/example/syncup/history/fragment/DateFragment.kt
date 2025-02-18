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
import com.example.syncup.chart.HeartRateChartView
import com.example.syncup.model.HealthAdapter
import com.example.syncup.model.HealthItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class DateFragment : Fragment() {

    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: HealthAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var heartRateChart: HeartRateChartView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_date, container, false)
        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        heartRateChart = view.findViewById(R.id.heartRateChart)

        healthDataAdapter = HealthAdapter(emptyList())
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
        Log.d("FirestoreDebug", "Fetching data for user: $userId")

        firestore.collection("patient_heart_rate")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { documents ->
                val groupedItems = mutableListOf<HealthItem>()
                val groupedMap = LinkedHashMap<String, MutableList<HealthItem.DataItem>>()
                val dataList = mutableListOf<HealthData>()

                for (doc in documents) {
                    val heartRate = doc.getLong("heartRate")?.toInt() ?: 0
                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                    val timestamp = doc.getString("timestamp") ?: continue

                    val formattedDate = extractDate(timestamp)
                    val formattedTime = extractTime(timestamp)

                    Log.d("FirestoreDebug", "Data: $heartRate BPM, BP: $systolicBP/$diastolicBP, Battery: $batteryLevel%, Date: $formattedDate, Time: $formattedTime")

                    val healthData = HealthData(
                        heartRate = heartRate,
                        bloodPressure = "$systolicBP/$diastolicBP",
                        batteryLevel = batteryLevel,
                        timestamp = formattedTime,
                        fullTimestamp = timestamp
                    )

                    dataList.add(healthData)

                    if (!groupedMap.containsKey(formattedDate)) {
                        groupedMap[formattedDate] = mutableListOf()
                    }
                    groupedMap[formattedDate]?.add(HealthItem.DataItem(healthData))
                }

                // 🔹 **Cek apakah ada data yang ditemukan**
                if (dataList.isEmpty()) {
                    Log.w("FirestoreDebug", "No health data found for user: $userId")
                    return@addOnSuccessListener
                }

                // 🔹 **Urutkan data berdasarkan timestamp terbaru**
                val sortedData = dataList.sortedByDescending { it.fullTimestamp }
                Log.d("FirestoreDebug", "Sorted Data Count: ${sortedData.size}")

                // 🔹 **Ambil tanggal terbaru**
                val latestDate = sortedData.firstOrNull()?.let { extractDate(it.fullTimestamp) }

                if (latestDate != null) {
                    val latestData = groupedMap[latestDate] ?: emptyList()

                    if (latestData.isNotEmpty()) {
                        Log.d("FirestoreDebug", "Calculating averages for date: $latestDate")

                        // 🔹 **Hitung rata-rata indikator untuk tanggal terbaru**
                        val avgHeartRate = latestData.map { it.healthData.heartRate }.average().toInt()
                        val avgSystolicBP = latestData.map { it.healthData.bloodPressure.split("/")[0].toInt() }.average().toInt()
                        val avgDiastolicBP = latestData.map { it.healthData.bloodPressure.split("/")[1].toInt() }.average().toInt()
                        val avgBatteryLevel = latestData.map { it.healthData.batteryLevel }.average().toInt()

                        Log.d("FirestoreDebug", "Avg Heart Rate: $avgHeartRate, BP: $avgSystolicBP/$avgDiastolicBP, Battery: $avgBatteryLevel%")

                        if (isAdded) {
                            activity?.runOnUiThread {
                                avgHeartRateTextView.text = "$avgHeartRate"
                                avgBloodPressureTextView.text = "$avgSystolicBP/$avgDiastolicBP"
                                avgBatteryTextView.text = "$avgBatteryLevel%"

                                // ✅ **Set Data ke Chart**
                                heartRateChart.setData(latestData.map { it.healthData })
                            }
                        }

                    } else {
                        Log.w("FirestoreDebug", "No data found for latest date: $latestDate")
                    }
                } else {
                    Log.w("FirestoreDebug", "No valid date found in the dataset")
                }

                // 🔹 **Masukkan ke dalam RecyclerView**
                for ((date, items) in groupedMap) {
                    groupedItems.add(HealthItem.DateHeader(date))
                    groupedItems.addAll(items)
                }

                healthDataAdapter.updateData(groupedItems)
            }
            .addOnFailureListener { exception ->
                Log.e("FirestoreError", "Failed to fetch data: ${exception.message}")
            }
    }

    // **Fungsi untuk mengambil hanya tanggal (misalnya "24 Jan 2025")**
    private fun extractDate(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())  // 🔹 Format Tanggal
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: "")
        } catch (e: Exception) {
            Log.e("DateFormatError", "Error parsing timestamp: ${e.message}")
            timestamp  // Jika gagal, gunakan timestamp asli
        }
    }

    // **Fungsi untuk mengambil jam & menit saja dari timestamp**
    private fun extractTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())  // 🔹 Format Jam:Menit
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: "")
        } catch (e: Exception) {
            Log.e("TimeFormatError", "Error parsing timestamp: ${e.message}")
            timestamp
        }
    }
}
