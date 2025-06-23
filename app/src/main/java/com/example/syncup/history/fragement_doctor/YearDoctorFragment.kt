package com.example.syncup.history.fragement_doctor

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
import com.example.syncup.adapter.YearHealthAdapter
import com.example.syncup.adapter.YearHealthDoctorAdapter
import com.example.syncup.chart.YearChartView
import com.example.syncup.model.YearHealthItem
import com.example.syncup.model.YearHealthItemDoctor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class YearDoctorFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: YearHealthDoctorAdapter
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
        val view = inflater.inflate(R.layout.fragment_year_doctor, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = YearHealthDoctorAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)
        yearChartView = view.findViewById(R.id.heartRateChart)

        fetchHealthData()

        return view
    }

    private fun getActualDoctorUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        var phoneNumber = currentUser.phoneNumber

        // Format the phone number if it starts with "+62"
        phoneNumber = formatPhoneNumber(phoneNumber)

        val firestore = FirebaseFirestore.getInstance()

        Log.d("ProfileDoctor", "Current User Email: $email")
        Log.d("ProfileDoctor", "Formatted Phone: $phoneNumber")

        if (!email.isNullOrEmpty()) {
            firestore.collection("users_doctor_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfileDoctor", "Email query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfileDoctor", "No user document found for email")
                        onResult(null)  // No user document found for email
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfileDoctor", "Found userId for email: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileDoctor", "Error querying email", e)
                    onResult(null)
                }
        } else if (!phoneNumber.isNullOrEmpty()) {
            firestore.collection("users_doctor_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfileDoctor", "Phone number query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfileDoctor", "No user document found for phone number")
                        onResult(null)  // No user document found for phone number
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfileDoctor", "Found userId for phone number: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfileDoctor", "Error querying phone number", e)
                    onResult(null)
                }
        } else {
            Log.e("ProfileDoctor", "No email or phone number found for the current user")
            onResult(null)  // If neither email nor phone is available
        }
    }

    // Helper function to format phone number
    private fun formatPhoneNumber(phoneNumber: String?): String? {
        return phoneNumber?.let {
            if (it.startsWith("+62")) {
                "0" + it.substring(3)  // Replace +62 with 0
            } else {
                it  // If it doesn't start with +62, return the number as is
            }
        }
    }

    private fun fetchHealthData() {
        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Toast.makeText(requireContext(), "Gagal mendapatkan UID dokter", Toast.LENGTH_SHORT).show()
                return@getActualDoctorUID
            }

            firestore.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUID)
                .get()
                .addOnSuccessListener { assignedSnapshot ->
                    val patientIds = assignedSnapshot.documents.mapNotNull { it.getString("patientId") }
                    if (patientIds.isEmpty()) {
                        updateAverageUI(null, null, null)
                        healthDataAdapter.updateData(emptyList())
                        updateChartData(emptyMap())
                        return@addOnSuccessListener
                    }

                    val yearMap = mutableMapOf<String, MutableList<com.example.syncup.search.PatientData>>()
                    val yearChartMap = mutableMapOf<String, MutableList<Int>>()
                    var loadedCount = 0

                    for (id in patientIds) {
                        val process: (Map<String, Any?>, String) -> Unit = { userDoc, photoUrl ->
                            val name = userDoc["fullName"] as? String ?: "-"
                            val age = userDoc["age"] as? String ?: "-"
                            val gender = userDoc["gender"] as? String ?: "-"
                            val email = userDoc["email"] as? String ?: "-"
                            val phoneNumber = userDoc["phoneNumber"] as? String ?: "-"

                            firestore.collection("patient_heart_rate")
                                .whereEqualTo("userId", id)
                                .get()
                                .addOnSuccessListener { healthDocs ->
                                    val tempMap = mutableMapOf<String, MutableList<Int>>()
                                    val bpMap = mutableMapOf<String, MutableList<String>>()
                                    val batteryMap = mutableMapOf<String, MutableList<Int>>()

                                    for (doc in healthDocs) {
                                        val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                                        if (heartRate == 0) continue
                                        val systolic = doc.getDouble("systolicBP")?.toInt() ?: 0
                                        val diastolic = doc.getDouble("diastolicBP")?.toInt() ?: 0
                                        val battery = doc.getDouble("batteryLevel")?.toInt() ?: 0
                                        val timestamp = doc.getString("timestamp") ?: continue

                                        val year = extractYearFromTimestamp(timestamp)
                                        tempMap.getOrPut(year) { mutableListOf() }.add(heartRate)
                                        bpMap.getOrPut(year) { mutableListOf() }.add("$systolic/$diastolic")
                                        batteryMap.getOrPut(year) { mutableListOf() }.add(battery)
                                    }

                                    for ((year, hrList) in tempMap) {
                                        val avgHR = hrList.average().toInt()
                                        val avgBP = bpMap[year]?.firstOrNull() ?: "N/A"
                                        val avgBattery = batteryMap[year]?.average()?.toInt()?.toString()

                                        val pdata = com.example.syncup.search.PatientData(
                                            id = id,
                                            name = name,
                                            age = age,
                                            gender = gender,
                                            email = email,
                                            phoneNumber = phoneNumber,
                                            heartRate = avgHR.toString(),
                                            systolicBP = avgBP.split("/").firstOrNull() ?: "None",
                                            diastolicBP = avgBP.split("/").getOrNull(1) ?: "None",
                                            photoUrl = photoUrl,
                                            isAssigned = true,
                                            batteryLevel = avgBattery
                                        )

                                        yearMap.getOrPut(year) { mutableListOf() }.add(pdata)
                                        yearChartMap.getOrPut(year) { mutableListOf() }.add(avgHR)
                                    }

                                    loadedCount++
                                    if (loadedCount == patientIds.size) {
                                        val groupedItems = mutableListOf<YearHealthItemDoctor>()
                                        val chartMap = mutableMapOf<String, Int>()

                                        val sorted = yearMap.toSortedMap()
                                        for ((year, list) in sorted) {
                                            groupedItems.add(YearHealthItemDoctor.YearHeader(year))
                                            list.forEach { groupedItems.add(YearHealthItemDoctor.YearData(it)) }

                                            chartMap[year] = yearChartMap[year]?.average()?.toInt() ?: 0
                                        }

                                        val currentYear = getCurrentYear()
                                        val latest = yearMap[currentYear]?.firstOrNull()
                                        if (latest != null) {
                                            val avgHR = latest.heartRate.toIntOrNull()
                                            val avgBP = "${latest.systolicBP}/${latest.diastolicBP}"
                                            val avgBat = latest.batteryLevel?.toIntOrNull()
                                            updateAverageUI(avgHR, avgBP, avgBat)
                                        } else {
                                            updateAverageUI(null, null, null)
                                        }

                                        healthDataAdapter.updateData(groupedItems)
                                        updateChartData(chartMap)
                                    }
                                }
                        }

                        // Ambil profil & foto
                        firestore.collection("users_patient_email").document(id).get()
                            .addOnSuccessListener { doc ->
                                if (doc.exists()) {
                                    firestore.collection("patient_photoprofile").document(id).get()
                                        .addOnSuccessListener { photo ->
                                            val photoUrl = photo.getString("photoUrl") ?: ""
                                            process(doc.data ?: emptyMap(), photoUrl)
                                        }
                                } else {
                                    firestore.collection("users_patient_phonenumber").document(id).get()
                                        .addOnSuccessListener { docPhone ->
                                            if (docPhone.exists()) {
                                                firestore.collection("patient_photoprofile").document(id).get()
                                                    .addOnSuccessListener { photo ->
                                                        val photoUrl = photo.getString("photoUrl") ?: ""
                                                        process(docPhone.data ?: emptyMap(), photoUrl)
                                                    }
                                            } else {
                                                loadedCount++
                                            }
                                        }
                                }
                            }
                    }
                }
        }
    }

    private fun extractYearFromTimestamp(timestamp: String): String {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
            val date = format.parse(timestamp)
            SimpleDateFormat("yyyy", Locale.ENGLISH).format(date!!)
        } catch (e: Exception) {
            getCurrentYear()
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