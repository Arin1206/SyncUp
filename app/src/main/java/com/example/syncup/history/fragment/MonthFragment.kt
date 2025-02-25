package com.example.syncup.history.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.adapter.MonthHealthAdapter
import com.example.syncup.model.MonthHealthItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class MonthFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: MonthHealthAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_month, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = MonthHealthAdapter(emptyList())
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
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e("MonthFragment", "Error fetching health data", error)
                    return@addSnapshotListener
                }

                if (documents == null || documents.isEmpty) {
                    Log.w("MonthFragment", "No health data found")
                    healthDataAdapter.updateData(emptyList())
                    return@addSnapshotListener
                }

                val monthMap = mutableMapOf<String, MutableList<Int>>()
                val bpMap = mutableMapOf<String, MutableList<String>>()
                val batteryMap = mutableMapOf<String, MutableList<Int>>()

                for (doc in documents) {
                    val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                    if (heartRate == 0) continue // **Abaikan heart rate 0 saat menghitung rata-rata**

                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                    val timestamp = doc.getString("timestamp") ?: continue

                    val month = extractMonth(timestamp)

                    monthMap.getOrPut(month) { mutableListOf() }.add(heartRate)
                    bpMap.getOrPut(month) { mutableListOf() }.add("$systolicBP/$diastolicBP")
                    batteryMap.getOrPut(month) { mutableListOf() }.add(batteryLevel)
                }

                val monthItems = mutableListOf<MonthHealthItem>()

                // **Urutkan bulan dari yang terlama ke terbaru**
                monthMap.entries.sortedBy { parseMonth(it.key) }.forEach { (month, heartRates) ->
                    val monthOnly = month.split(" ")[0] // **Ambil hanya bulan tanpa tahun**
                    monthItems.add(MonthHealthItem.MonthHeader(monthOnly)) // **Tambahkan Header untuk Bulan**

                    val avgHeartRate = heartRates.average().toInt()
                    val avgBloodPressure = bpMap[month]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key ?: "N/A"
                    val avgBattery = batteryMap[month]?.average()?.toInt() ?: 0

                    monthItems.add(MonthHealthItem.MonthData(avgHeartRate, avgBloodPressure, avgBattery))
                }

                // **Update RecyclerView dengan data terbaru**
                healthDataAdapter.updateData(monthItems)
            }
    }

    private fun extractMonth(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: Date())
        } catch (e: Exception) {
            Log.e("DateFormatError", "Error parsing timestamp: ${e.message}")
            "Unknown Month"
        }
    }

    private fun parseMonth(monthText: String): Date {
        return try {
            SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).parse(monthText) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }
}
