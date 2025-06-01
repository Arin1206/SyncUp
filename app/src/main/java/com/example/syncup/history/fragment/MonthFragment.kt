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

    private var healthDataListener: ListenerRegistration? = null

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

    private fun getUserAge(onResult: (Int?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(null)
            return
        }

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        if (email != null) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    val age = documents.firstOrNull()?.getString("age")?.toInt()
                    onResult(age)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else if (phoneNumber != null) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    val age = documents.firstOrNull()?.getLong("age")?.toInt()
                    onResult(age)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else {
            onResult(null)
        }
    }


    private fun getActualPatientUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else if (phoneNumber != null) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else {
            onResult(null)
        }
    }




    private fun fetchHealthData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        getActualPatientUID { patientUserId ->
            if (patientUserId == null) {
                Log.w("MonthFragment", "Patient UID not found")
                requireActivity().runOnUiThread {
                    healthDataAdapter.updateData(emptyList())
                    updateAverageUI(null, null, null)
                    updateChartData(emptyMap())
                }
                return@getActualPatientUID
            }

            // 🔽 Tambahkan panggilan getUserAge di sini
            getUserAge { age ->
                Log.d("MonthFragment", "User Age: ${age ?: "Unknown"}")
                // Kamu bisa update UI juga di sini jika perlu
                // Contoh: updateAgeUI(age)
            }

            // 🔽 Listener untuk data kesehatan tetap seperti sebelumnya
            healthDataListener?.remove()

            healthDataListener = firestore.collection("patient_heart_rate")
                .whereEqualTo("userId", patientUserId)
                .addSnapshotListener { documents, error ->
                    if (error != null) {
                        Log.e("MonthFragment", "Error fetching health data", error)
                        return@addSnapshotListener
                    }

                    if (!isAdded) return@addSnapshotListener

                    if (documents == null || documents.isEmpty) {
                        Log.w("MonthFragment", "No health data found")
                        requireActivity().runOnUiThread {
                            healthDataAdapter.updateData(emptyList())
                            updateAverageUI(null, null, null)
                            updateChartData(emptyMap())
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

                        val timestamp = doc.getString("timestamp") ?: continue
                        val monthYear = extractMonthYearFromTimestamp(timestamp)

                        monthMap.getOrPut(monthYear) { mutableListOf() }.add(heartRate)
                        bpMap.getOrPut(monthYear) { mutableListOf() }.add("$systolicBP/$diastolicBP")
                        batteryMap.getOrPut(monthYear) { mutableListOf() }.add(batteryLevel)
                    }

                    val monthItems = mutableListOf<MonthHealthItem>()
                    val monthAverages = mutableMapOf<String, Int>()

                    val sortedMonths = monthMap.entries.sortedByDescending { parseMonth(it.key) }

                    sortedMonths.forEach { (month, heartRates) ->
                        val monthOnly = convertMonthToEnglish(month.split(" ")[0])
                        monthItems.add(MonthHealthItem.MonthHeader(monthOnly))

                        val avgHeartRate = heartRates.ifEmpty { listOf(0) }.average().toInt()
                        val avgBloodPressure = bpMap[month]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key ?: "N/A"
                        val avgBattery = batteryMap[month]?.ifEmpty { listOf(0) }?.average()?.toInt() ?: 0

                        monthItems.add(MonthHealthItem.MonthData(avgHeartRate, avgBloodPressure, avgBattery))
                        monthAverages[monthOnly] = avgHeartRate
                    }

                    val currentMonth = getCurrentMonth()
                    val currentMonthData = monthMap[currentMonth]

                    if (!currentMonthData.isNullOrEmpty()) {
                        val avgLatestHeartRate = currentMonthData.average().toInt()
                        val latestBloodPressure = bpMap[currentMonth]?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key ?: "N/A"
                        val latestBattery = batteryMap[currentMonth]?.ifEmpty { listOf(0) }?.average()?.toInt() ?: 0

                        updateAverageUI(avgLatestHeartRate, latestBloodPressure, latestBattery)
                    } else {
                        updateAverageUI(null, null, null)
                    }

                    requireActivity().runOnUiThread {
                        healthDataAdapter.updateData(monthItems)
                        updateChartData(monthAverages)
                        // 🔽 Misalnya kamu mau tampilkan age di UI di sini juga bisa
                        // updateAgeUI(age)
                    }
                }
        }

    }

    private fun updateAverageUI(heartRate: Int?, bloodPressure: String?, battery: Int?) {
        if (!isAdded) return

        requireActivity().runOnUiThread {
            avgHeartRateTextView.text = heartRate?.toString() ?: "N/A"
            avgBloodPressureTextView.text = bloodPressure ?: "N/A"
            avgBatteryTextView.text = battery?.let { "$it%" } ?: "N/A"
        }
    }

    private fun updateChartData(monthAverages: Map<String, Int>) {
        if (!isAdded) return

        requireActivity().runOnUiThread {
            monthChartView.setData(monthAverages)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        healthDataListener?.remove()
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
            "desember", "december" -> "December"
            else -> month
        }
    }

    private fun extractMonthYearFromTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestamp) ?: return "Unknown Month"
            val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            outputFormat.format(date)
        } catch (e: Exception) {
            Log.e("MonthFragment", "Error parsing timestamp: ${e.message}")
            "Unknown Month"
        }
    }
}
