package com.example.syncup.history.fragment

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.adapter.YearHealthAdapter
import com.example.syncup.chart.YearChartView
import com.example.syncup.model.YearHealthItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class YearFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: YearHealthAdapter
    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView
    private lateinit var yearChartView: YearChartView

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_year, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = YearHealthAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)
        yearChartView = view.findViewById(R.id.heartRateChart)

        fetchHealthData()

        return view
    }

    private fun fetchHealthData() {
        val currentUser = auth.currentUser ?: return

        firestore.collection("patient_heart_rate")
            .whereEqualTo("userId", currentUser.uid)
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e("YearFragment", "Error fetching health data", error)
                    return@addSnapshotListener
                }

                if (documents == null || documents.isEmpty) {
                    Log.w("YearFragment", "No health data found")
                    healthDataAdapter.updateData(emptyList())
                    updateAverageUI(null, null, null)
                    updateChartData(emptyMap())
                    return@addSnapshotListener
                }

                val yearMap = mutableMapOf<String, MutableList<Int>>()
                val bpMap = mutableMapOf<String, MutableList<String>>()
                val batteryMap = mutableMapOf<String, MutableList<Int>>()
                val yearAverages = mutableMapOf<String, Int>()

                for (doc in documents) {
                    val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                    if (heartRate == 0) continue

                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0

                    val year = getCurrentYear()
                    yearMap.getOrPut(year) { mutableListOf() }.add(heartRate)
                    bpMap.getOrPut(year) { mutableListOf() }.add("$systolicBP/$diastolicBP")
                    batteryMap.getOrPut(year) { mutableListOf() }.add(batteryLevel)
                }

                val yearItems = mutableListOf<YearHealthItem>()
                val sortedYears = yearMap.entries.sortedBy { it.key.toInt() }

                sortedYears.forEach { (year, heartRates) ->
                    yearItems.add(YearHealthItem.YearHeader(year))
                    val avgHeartRate = heartRates.average().toInt()
                    val avgBloodPressure = bpMap[year]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key ?: "N/A"
                    val avgBattery = batteryMap[year]?.ifEmpty { listOf(0) }?.average()?.toInt() ?: 0

                    yearItems.add(YearHealthItem.YearData(avgHeartRate, avgBloodPressure, avgBattery))
                    yearAverages[year] = avgHeartRate
                }

                val latestYear = sortedYears.lastOrNull()?.key
                latestYear?.let {
                    val avgLatestHeartRate = yearAverages[it] ?: 0
                    val avgLatestBloodPressure = bpMap[it]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key ?: "N/A"
                    val avgLatestBattery = batteryMap[it]?.ifEmpty { listOf(0) }?.average()?.toInt() ?: 0

                    updateAverageUI(avgLatestHeartRate, avgLatestBloodPressure, avgLatestBattery)
                }

                healthDataAdapter.updateData(yearItems)
                updateChartData(yearAverages)
            }
    }

    private fun updateAverageUI(heartRate: Int?, bloodPressure: String?, battery: Int?) {
        requireActivity().runOnUiThread {
            avgHeartRateTextView.text = heartRate?.let { "$it" } ?: "N/A" // **Tambahkan "bpm"**
            avgBloodPressureTextView.text = bloodPressure ?: "N/A"
            avgBatteryTextView.text = battery?.let { "$it%" } ?: "N/A" // **Tambahkan "%"**
        }
    }

    private fun updateChartData(yearAverages: Map<String, Int>) {
        requireActivity().runOnUiThread {
            yearChartView.setData(yearAverages)
        }
    }

    private fun getCurrentYear(): String {
        return SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
    }
}
