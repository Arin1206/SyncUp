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
import com.example.syncup.adapter.MonthHealthAdapter
import com.example.syncup.chart.MonthChartView
import com.example.syncup.model.MonthHealthItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.*

class MonthFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: MonthHealthAdapter
    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView
    private lateinit var monthChartView: MonthChartView

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private var healthDataListener: ListenerRegistration? = null // **Simpan listener untuk bisa dihapus**

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_month, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = MonthHealthAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)
        monthChartView = view.findViewById(R.id.heartRateChart)

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

        // **Hapus listener sebelumnya jika ada untuk mencegah duplikasi**
        healthDataListener?.remove()

        healthDataListener = firestore.collection("patient_heart_rate")
            .whereEqualTo("userId", userId)
            .addSnapshotListener { documents, error ->
                if (error != null) {
                    Log.e("MonthFragment", "Error fetching health data", error)
                    return@addSnapshotListener
                }

                if (!isAdded) return@addSnapshotListener // **Pastikan fragment masih aktif sebelum update UI**

                if (documents == null || documents.isEmpty) {
                    Log.w("MonthFragment", "No health data found")
                    requireActivity().runOnUiThread {
                        healthDataAdapter.updateData(emptyList())
                        updateAverageUI(null, null, null)
                        updateChartData(emptyMap()) // **Reset grafik jika tidak ada data**
                    }
                    return@addSnapshotListener
                }

                val monthMap = mutableMapOf<String, MutableList<Int>>()
                val bpMap = mutableMapOf<String, MutableList<String>>()
                val batteryMap = mutableMapOf<String, MutableList<Int>>()

                for (doc in documents) {
                    val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                    if (heartRate == 0) continue

                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0

                    val month = getCurrentMonth()

                    monthMap.getOrPut(month) { mutableListOf() }.add(heartRate)
                    bpMap.getOrPut(month) { mutableListOf() }.add("$systolicBP/$diastolicBP")
                    batteryMap.getOrPut(month) { mutableListOf() }.add(batteryLevel)
                }

                val monthItems = mutableListOf<MonthHealthItem>()
                val monthAverages = mutableMapOf<String, Int>()

                val sortedMonths = monthMap.entries.sortedBy { parseMonth(it.key) }

                sortedMonths.forEach { (month, heartRates) ->
                    val monthOnly = convertMonthToEnglish(month.split(" ")[0])
                    monthItems.add(MonthHealthItem.MonthHeader(monthOnly))

                    val avgHeartRate = heartRates.ifEmpty { listOf(0) }.average().toInt()
                    val avgBloodPressure = bpMap[month]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key ?: "N/A"
                    val avgBattery = batteryMap[month]?.ifEmpty { listOf(0) }?.average()?.toInt() ?: 0

                    monthItems.add(MonthHealthItem.MonthData(avgHeartRate, avgBloodPressure, avgBattery))
                    monthAverages[monthOnly] = avgHeartRate
                }

                val latestMonth = sortedMonths.lastOrNull()
                if (latestMonth != null) {
                    val latestHeartRates = latestMonth.value
                    val latestBloodPressure = bpMap[latestMonth.key]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key ?: "N/A"
                    val latestBattery = batteryMap[latestMonth.key]?.ifEmpty { listOf(0) }?.average()?.toInt() ?: 0

                    val avgLatestHeartRate = latestHeartRates.ifEmpty { listOf(0) }.average().toInt()

                    Log.d("MonthFragment", "Latest Avg Heart Rate: $avgLatestHeartRate")
                    Log.d("MonthFragment", "Latest Avg Blood Pressure: $latestBloodPressure")
                    Log.d("MonthFragment", "Latest Avg Battery: $latestBattery")

                    updateAverageUI(avgLatestHeartRate, latestBloodPressure, latestBattery)
                } else {
                    Log.w("MonthFragment", "No valid month data found")
                    updateAverageUI(null, null, null)
                }

                requireActivity().runOnUiThread {
                    healthDataAdapter.updateData(monthItems)
                    updateChartData(monthAverages)
                }
            }
    }

    private fun updateAverageUI(heartRate: Int?, bloodPressure: String?, battery: Int?) {
        if (!isAdded) return // **Cegah crash jika fragment tidak terpasang**

        requireActivity().runOnUiThread {
            avgHeartRateTextView.text = heartRate?.toString() ?: "N/A"
            avgBloodPressureTextView.text = bloodPressure ?: "N/A"
            avgBatteryTextView.text = battery?.let { "$it%" } ?: "N/A"
        }
    }

    private fun updateChartData(monthAverages: Map<String, Int>) {
        if (!isAdded) return // **Cegah update jika Fragment sudah tidak aktif**

        requireActivity().runOnUiThread {
            monthChartView.setData(monthAverages)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        healthDataListener?.remove() // **Hapus listener saat fragment dihancurkan**
    }

    private fun getCurrentMonth(): String {
        val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
        return outputFormat.format(Date())
    }

    private fun parseMonth(monthText: String): Date {
        return try {
            SimpleDateFormat("MMMM yyyy", Locale.ENGLISH).parse(monthText) ?: Date()
        } catch (e: Exception) {
            Date()
        }
    }

    private fun convertMonthToEnglish(month: String): String {
        return when (month.lowercase(Locale.ENGLISH)) {
            "januari", "january" -> "January"
            "februari", "february" -> "February"
            "maret", "march" -> "March"
            "april" -> "April"
            "mei", "may" -> "May"
            "juni", "june" -> "June"
            "juli", "july" -> "July"
            "agustus", "august" -> "August"
            "september" -> "September"
            "oktober", "october" -> "October"
            "november" -> "November"
            "desember" -> "December"
            else -> month
        }
    }
}
