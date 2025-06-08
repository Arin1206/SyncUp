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
import com.example.syncup.adapter.MonthHealthAdapter
import com.example.syncup.adapter.MonthHealthDoctorAdapter
import com.example.syncup.chart.MonthChartView
import com.example.syncup.model.MonthHealthItem
import com.example.syncup.model.MonthHealthItemDoctor
import com.example.syncup.search.PatientData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MonthDoctorFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: MonthHealthDoctorAdapter
    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView
    private lateinit var monthChartView: MonthChartView

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val allMonthMap = mutableMapOf<String, MutableList<PatientData>>()

    private var healthDataListener: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_month_doctor, container, false)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = MonthHealthDoctorAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)
        monthChartView = view.findViewById(R.id.heartRateChart)

        fetchHealthData()

        return view
    }


    private fun getActualDoctorUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_doctor_email")
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
            firestore.collection("users_doctor_phonenumber")
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
                        updateUI(emptyList())
                        return@addOnSuccessListener
                    }


                    val allGroupedItems = mutableListOf<MonthHealthItemDoctor>()
                    var loadedCount = 0

                    for (id in patientIds) {
                        val processPatient: (Map<String, Any?>, String) -> Unit = { userDoc, photoUrl ->
                            val name = userDoc["fullName"] as? String ?: "-"
                            val age = userDoc["age"] as? String ?: "-"
                            val gender = userDoc["gender"] as? String ?: "-"
                            val email = userDoc["email"] as? String ?: "-"
                            val phoneNumber = userDoc["phoneNumber"] as? String ?: "-"

                            firestore.collection("patient_heart_rate")
                                .whereEqualTo("userId", id)
                                .get()
                                .addOnSuccessListener { hrDocs ->
                                    val monthMap = mutableMapOf<String, MutableList<Triple<Int, Int?, Int?>>>()
                                    val batteryMap = mutableMapOf<String, MutableList<Int>>()  // Week -> battery list

                                    for (doc in hrDocs) {
                                        val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                                        if (heartRate == 0) continue
                                        val timestamp = doc.getString("timestamp") ?: continue

                                        val systolic = doc.getDouble("systolicBP")?.toInt()
                                        val diastolic = doc.getDouble("diastolicBP")?.toInt()
                                        val monthYear = extractMonthYearFromTimestamp(timestamp)

                                        monthMap.getOrPut(monthYear) { mutableListOf() }.add(Triple(heartRate, systolic, diastolic))

                                        // 🔁 Ambil battery level kalau tersedia
                                        val battery = doc.getDouble("batteryLevel")?.toInt()
                                        if (battery != null) {
                                            batteryMap.getOrPut(monthYear) { mutableListOf() }.add(battery)
                                        }
                                    }

                                    for ((month, entries) in monthMap) {
                                        val avgHR = entries.map { it.first }.average().toInt()
                                        val avgSys = entries.mapNotNull { it.second }.average().toInt()
                                        val avgDia = entries.mapNotNull { it.third }.average().toInt()

                                        val avgBattery = batteryMap[month]?.average()?.toInt()?.toString()
                                        val pdata = PatientData(
                                            id = id,
                                            name = name,
                                            age = age,
                                            gender = gender,
                                            email = email,
                                            phoneNumber = phoneNumber,
                                            heartRate = avgHR.toString(),
                                            systolicBP = avgSys?.toString() ?: "None",
                                            diastolicBP = avgDia?.toString() ?: "None",
                                            photoUrl = photoUrl,
                                            isAssigned = true,
                                            batteryLevel = avgBattery
                                        )

                                        allMonthMap.getOrPut(month) { mutableListOf() }.add(pdata)
                                    }

                                    loadedCount++
                                    if (loadedCount == patientIds.size) {
                                        val sortedMonthMap = allMonthMap.toSortedMap(compareByDescending { parseMonth(it) })
                                        for ((month, list) in sortedMonthMap) {
                                            allGroupedItems.add(MonthHealthItemDoctor.MonthHeader(month))
                                            list.forEach { allGroupedItems.add(MonthHealthItemDoctor.DataItem(it)) }
                                        }
                                        updateUI(allGroupedItems)
                                    }
                                }
                        }

                        // Cek data user dari email atau phone
                        firestore.collection("users_patient_email").document(id).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    firestore.collection("patient_photoprofile").document(id).get()
                                        .addOnSuccessListener { photoDoc ->
                                            val photoUrl = photoDoc.getString("photoUrl") ?: ""
                                            processPatient(userDoc.data ?: emptyMap(), photoUrl)
                                        }
                                } else {
                                    firestore.collection("users_patient_phonenumber").document(id).get()
                                        .addOnSuccessListener { phoneDoc ->
                                            if (phoneDoc.exists()) {
                                                firestore.collection("patient_photoprofile").document(id).get()
                                                    .addOnSuccessListener { photoDoc ->
                                                        val photoUrl = photoDoc.getString("photoUrl") ?: ""
                                                        processPatient(phoneDoc.data ?: emptyMap(), photoUrl)
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

    private fun updateUI(data: List<MonthHealthItemDoctor>) {
        if (!isAdded) return



        requireActivity().runOnUiThread {
            // 1. Update adapter
            healthDataAdapter.updateData(data)

            // 2. Ambil data bulan saat ini saja untuk update summary UI
            val currentMonth = getCurrentMonth()
            val currentMonthPatients = data.filterIsInstance<MonthHealthItemDoctor.DataItem>()
                .filter { extractMonthYearFromPatient(it.patientData) == currentMonth }

            if (currentMonthPatients.isNotEmpty()) {
                val avgHeartRate = currentMonthPatients.mapNotNull { it.patientData.heartRate.toIntOrNull() }.average().toInt()
                val avgSys = currentMonthPatients.mapNotNull { it.patientData.systolicBP.toIntOrNull() }.average().toInt()
                val avgDia = currentMonthPatients.mapNotNull { it.patientData.diastolicBP.toIntOrNull() }.average().toInt()
                val avgBattery = currentMonthPatients.mapNotNull { it.patientData.batteryLevel?.toIntOrNull() }
                    .takeIf { it.isNotEmpty() }
                    ?.average()?.toInt()

                updateAverageUI(avgHeartRate, "$avgSys/$avgDia", avgBattery)
            } else {
                updateAverageUI(null, null, null)
            }

            // 3. Untuk chart data (opsional jika kamu pakai chart bulanan)
            val monthAverages = allMonthMap.mapValues { (_, list) ->
                list.mapNotNull { it.heartRate.toIntOrNull() }.average().toInt()
            }.mapKeys { (monthFull) -> convertToShortMonth(monthFull) }


            updateChartData(monthAverages)
            Log.d("ChartUpdate", "Data chart: $monthAverages")

        }
    }

    private fun convertToShortMonth(monthFull: String): String {
        return try {
            val sdfInput = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            val date = sdfInput.parse(monthFull)
            val sdfOutput = SimpleDateFormat("MMM", Locale.ENGLISH)
            sdfOutput.format(date ?: Date())
        } catch (e: Exception) {
            monthFull
        }
    }

    private fun extractMonthYearFromPatient(patient: PatientData): String {
        // Asumsikan data disimpan bulan saat ini
        // Kamu bisa update jika ingin lebih presisi berdasarkan timestamp
        return getCurrentMonth()
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