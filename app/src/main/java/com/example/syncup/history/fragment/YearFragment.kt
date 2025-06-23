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
import com.example.syncup.home.HomeFragment
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

    private fun getActualPatientUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        var phoneNumber = currentUser.phoneNumber

        // Format the phone number if it starts with "+62"
        phoneNumber = formatPhoneNumber(phoneNumber)

        val firestore = FirebaseFirestore.getInstance()

        Log.d("ProfilePatient", "Current User Email: $email")
        Log.d("ProfilePatient", "Formatted Phone: $phoneNumber")

        if (!email.isNullOrEmpty()) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfilePatient", "Email query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfilePatient", "No user document found for email")
                        onResult(null)  // No user document found for email
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfilePatient", "Found userId for email: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfilePatient", "Error querying email", e)
                    onResult(null)
                }
        } else if (!phoneNumber.isNullOrEmpty()) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfilePatient", "Phone number query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfilePatient", "No user document found for phone number")
                        onResult(null)  // No user document found for phone number
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfilePatient", "Found userId for phone number: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfilePatient", "Error querying phone number", e)
                    onResult(null)
                }
        } else {
            Log.e("ProfilePatient", "No email or phone number found for the current user")
            onResult(null)  // If neither email nor phone is available
        }
    }


    // Helper function to format phone number
    private fun formatPhoneNumber(phoneNumber: String?): String? {
        return phoneNumber?.let {
            if (it.startsWith("+62")) {
                "0" + it.substring(3)  // Replace +62 with 0
            } else {
                it  // Return phone number as is if it doesn't start with +62
            }
        }
    }
    private fun getUserAge(onResult: (Int?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Log.e(HomeFragment.TAG, "User is not logged in.")
            onResult(null)
            return
        }

        val email = currentUser.email
        var phoneNumber = currentUser.phoneNumber

        // Format the phone number if it starts with "+62"
        phoneNumber = formatPhoneNumber(phoneNumber)

        val firestore = FirebaseFirestore.getInstance()

        Log.d(HomeFragment.TAG, "Current User Email: $email")
        Log.d(HomeFragment.TAG, "Formatted Phone Number: $phoneNumber")

        if (!email.isNullOrEmpty()) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d(HomeFragment.TAG, "Email query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e(HomeFragment.TAG, "No user document found for email.")
                        onResult(null)  // No user document found for email
                    } else {
                        val age = documents.firstOrNull()?.getString("age")?.toInt()
                        Log.d(HomeFragment.TAG, "Found age for email: $age")
                        onResult(age)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(HomeFragment.TAG, "Error querying email", e)
                    onResult(null)
                }
        } else if (!phoneNumber.isNullOrEmpty()) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d(HomeFragment.TAG, "Phone number query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e(HomeFragment.TAG, "No user document found for phone number.")
                        onResult(null)  // No user document found for phone number
                    } else {
                        val age = documents.firstOrNull()?.getString("age")?.toInt()
                        Log.d(HomeFragment.TAG, "Found age for phone number: $age")
                        onResult(age)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e(HomeFragment.TAG, "Error querying phone number", e)
                    onResult(null)
                }
        } else {
            Log.e(HomeFragment.TAG, "No email or phone number found for the current user.")
            onResult(null)  // If neither email nor phone is available
        }
    }

    private fun fetchHealthData() {
        getActualPatientUID { patientUID ->
            if (patientUID == null) {
                Log.w("YearFragment", "❌ Patient UID not found")
                if (isAdded && activity != null) {
                    activity?.runOnUiThread {
                    healthDataAdapter.updateData(emptyList())
                    updateAverageUI(null, null, null)
                    updateChartData(emptyMap())
                }
                }
                return@getActualPatientUID
            }

            // 🔽 Tambahkan ini: ambil umur user
            getUserAge { age ->
                Log.d("YearFragment", "📌 User age: ${age ?: "Unknown"}")
                // Kalau mau, kamu bisa update UI: updateAgeUI(age)
            }

            // 🔽 Lanjutkan listener untuk data kesehatan
            firestore.collection("patient_heart_rate")
                .whereEqualTo("userId", patientUID)
                .addSnapshotListener { documents, error ->
                    if (error != null) {
                        Log.e("YearFragment", "❌ Error fetching health data", error)
                        return@addSnapshotListener
                    }

                    if (documents == null || documents.isEmpty) {
                        Log.w("YearFragment", "⚠ No health data found")
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

                        val timestampStr = doc.getString("timestamp")
                        val timestamp = if (timestampStr != null) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).parse(timestampStr)
                        } else {
                            null
                        }

                        val year = if (timestamp != null) {
                            SimpleDateFormat("yyyy", Locale.ENGLISH).format(timestamp)
                        } else {
                            getCurrentYear()
                        }

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
    }

    private fun updateAverageUI(heartRate: Int?, bloodPressure: String?, battery: Int?) {
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                avgHeartRateTextView.text = heartRate?.let { "$it" } ?: "N/A" // **Tambahkan "bpm"**
                avgBloodPressureTextView.text = bloodPressure ?: "N/A"
                avgBatteryTextView.text = battery?.let { "$it%" } ?: "N/A" // **Tambahkan "%"**
            }
        }
    }

    private fun updateChartData(yearAverages: Map<String, Int>) {
        if (isAdded && activity != null) {
            activity?.runOnUiThread {
                yearChartView.setData(yearAverages)
            }
        }
    }

    private fun getCurrentYear(): String {
        return SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
    }
}
