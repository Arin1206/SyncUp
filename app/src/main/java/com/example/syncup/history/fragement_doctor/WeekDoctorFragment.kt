package com.example.syncup.history.fragement_doctor

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
import com.example.syncup.adapter.WeekHealthDoctorAdapter
import com.example.syncup.chart.WeekChartView
import com.example.syncup.model.WeekHealthItem
import com.example.syncup.model.WeekHealthItemDoctor
import com.example.syncup.search.PatientData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WeekDoctorFragment : Fragment() {

    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView
    private lateinit var weekChartView: WeekChartView
    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: WeekHealthDoctorAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_week_doctor, container, false)

        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        healthDataAdapter = WeekHealthDoctorAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        weekChartView = view.findViewById(R.id.heartRateChart)

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
                        updateWeekUI(emptyMap(), emptyList())
                        return@addOnSuccessListener
                    }

                    val allWeekMap = mutableMapOf<String, MutableList<PatientData>>()
                    val allGroupedItems = mutableListOf<WeekHealthItemDoctor>()
                    var loadedCount = 0

                    for (id in patientIds) {
                        val processData: (Map<String, Any?>, String) -> Unit = { userDoc, photoUrl ->
                            val name = userDoc["fullName"] as? String ?: "-"
                            val age = userDoc["age"] as? String ?: "-"
                            val gender = userDoc["gender"] as? String ?: "-"
                            val email = userDoc["email"] as? String ?: "-"
                            val phoneNumber = userDoc["phoneNumber"] as? String ?: "-"

                            firestore.collection("patient_heart_rate")
                                .whereEqualTo("userId", id)
                                .get()
                                .addOnSuccessListener { hrDocs ->
                                    val currentMonthYear = getCurrentMonthYear()
                                    val firstWeekRange = getFirstWeekOfCurrentMonth()
                                    val tempWeekMap = mutableMapOf<String, MutableList<Triple<Int, Int?, Int?>>>()
                                    val batteryMap = mutableMapOf<String, MutableList<Int>>()  // Week -> battery list

                                    for (doc in hrDocs) {
                                        val timestamp = doc.getString("timestamp") ?: continue
                                        if (extractMonthYearFromTimestamp(timestamp) != currentMonthYear) continue

                                        val week = getWeekOfMonth(timestamp, firstWeekRange)
                                        val hr = doc.getLong("heartRate")?.toInt() ?: continue
                                        if (hr == 0) continue  // 🔴 Skip heart rate 0

                                        val sys = doc.getDouble("systolicBP")?.toInt()
                                        val dia = doc.getDouble("diastolicBP")?.toInt()

                                        tempWeekMap.getOrPut(week) { mutableListOf() }.add(Triple(hr, sys, dia))

                                        // 🔁 Ambil battery level kalau tersedia
                                        val battery = doc.getDouble("batteryLevel")?.toInt()
                                        if (battery != null) {
                                            batteryMap.getOrPut(week) { mutableListOf() }.add(battery)
                                        }
                                    }


                                    for ((week, values) in tempWeekMap) {
                                        val avgHR = values.map { it.first }.average().toInt()
                                        val avgSys = values.mapNotNull { it.second }.average().toInt().toString() ?: "None"
                                        val avgDia = values.mapNotNull { it.third }.average().toInt().toString() ?: "None"

                                        val avgBattery = batteryMap[week]?.average()?.toInt()?.toString()

                                        val pdata = PatientData(
                                            id = id,
                                            name = name,
                                            age = age,
                                            gender = gender,
                                            email = email,
                                            phoneNumber = phoneNumber,
                                            heartRate = avgHR.toString(),
                                            systolicBP = avgSys,
                                            diastolicBP = avgDia,
                                            photoUrl = photoUrl,
                                            isAssigned = true,
                                            batteryLevel = avgBattery  // Tambahkan ke model kalau belum
                                        )


                                        allWeekMap.getOrPut(week) { mutableListOf() }.add(pdata)
                                    }

                                    loadedCount++
                                    if (loadedCount == patientIds.size) {
                                        val sortedWeekMap = allWeekMap.toSortedMap(compareByDescending { it })
                                        for ((week, list) in sortedWeekMap) {
                                            allGroupedItems.add(WeekHealthItemDoctor.WeekHeader(week))
                                            list.forEach { allGroupedItems.add(WeekHealthItemDoctor.DataItem(it)) }
                                        }
                                        updateWeekUI(allWeekMap, allGroupedItems)
                                    }
                                }
                        }

                        // Cek data user berdasarkan email/phone
                        firestore.collection("users_patient_email").document(id).get()
                            .addOnSuccessListener { userDoc ->
                                if (userDoc.exists()) {
                                    firestore.collection("patient_photoprofile").document(id).get()
                                        .addOnSuccessListener { photoDoc ->
                                            val photoUrl = photoDoc.getString("photoUrl") ?: ""
                                            processData(userDoc.data ?: emptyMap(), photoUrl)
                                        }
                                } else {
                                    firestore.collection("users_patient_phonenumber").document(id).get()
                                        .addOnSuccessListener { phoneDoc ->
                                            if (phoneDoc.exists()) {
                                                firestore.collection("patient_photoprofile").document(id).get()
                                                    .addOnSuccessListener { photoDoc ->
                                                        val photoUrl = photoDoc.getString("photoUrl") ?: ""
                                                        processData(phoneDoc.data ?: emptyMap(), photoUrl)
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


    private fun updateWeekUI(
        weekMap: Map<String, List<PatientData>>,
        groupedItems: List<WeekHealthItemDoctor>
    ) {
        val latestWeek = weekMap.keys.maxByOrNull { extractStartEndDateFromWeek(it).first }
        val currentWeekData = latestWeek?.let { weekMap[it] }

        activity?.runOnUiThread {
            if (!currentWeekData.isNullOrEmpty()) {
                val avgHR = currentWeekData.mapNotNull { it.heartRate.toIntOrNull() }.average().toInt()
                val avgSys = currentWeekData.mapNotNull { it.systolicBP.toIntOrNull() }.average().toInt()
                val avgDia = currentWeekData.mapNotNull { it.diastolicBP.toIntOrNull() }.average().toInt()
                val avgbatteries = currentWeekData
                    .mapNotNull { it.batteryLevel?.toIntOrNull() }
                    .takeIf { it.isNotEmpty() }
                    ?.average()?.toInt()

                avgBatteryTextView.text = avgbatteries?.let { "$it%" } ?: "--%"

                avgHeartRateTextView.text = "$avgHR"
                avgBloodPressureTextView.text = "$avgSys/$avgDia"
                            } else {
                avgHeartRateTextView.text = "-- BPM"
                avgBloodPressureTextView.text = "--/-- mmHg"
                avgBatteryTextView.text = "--%"
            }

            val chartData = weekMap.mapValues { (_, list) ->
                list.mapNotNull { it.heartRate.toIntOrNull() }.average().toInt()
            }

            weekChartView.setData(chartData)
            weekChartView.invalidate()
            healthDataAdapter.updateData(groupedItems)
        }
    }


    private fun extractMonthYearFromTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestamp) ?: return "Unknown Month"

            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            Log.e("WeekFragment", "❌ Error extracting month-year from timestamp: ${e.message}")
            "Unknown Month"
        }
    }


    private fun getWeekOfMonth(timestamp: String, firstWeekRange: Pair<String, String>): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val compareFormat = SimpleDateFormat("dd MMM yyyy HH:mm:ss", Locale.getDefault())

        val date = inputFormat.parse(timestamp) ?: return "Unknown Week"

        val firstWeekStart = compareFormat.parse("${firstWeekRange.first} 00:00:00")
        val firstWeekEnd = compareFormat.parse("${firstWeekRange.second} 23:59:59")

        val calendar = Calendar.getInstance().apply { time = date }
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)

        val startDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        val currentMonth = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)

        // **Cari jumlah minggu sejak minggu pertama**
        val weekNumber = ((calendar.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1

        Log.d("WeekFragment", "📅 Tanggal: $timestamp -> Week: $weekNumber ($startDate - $endDate) di $currentMonth")

        return when {
            date.after(firstWeekStart) && date.before(firstWeekEnd) ->
                "Week 1 (${firstWeekRange.first} - ${firstWeekRange.second})"

            else ->
                "Week $weekNumber ($startDate - $endDate)"
        }
    }


    private fun getFirstWeekOfCurrentMonth(): Pair<String, String> {
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.DAY_OF_MONTH, 1)

        while (calendar.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            calendar.add(Calendar.DAY_OF_MONTH, -1)
        }

        val startOfWeek = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)
        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endOfWeek = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        Log.d("WeekCalculation", "Corrected First Week of Current Month: $startOfWeek - $endOfWeek")

        return Pair(startOfWeek, endOfWeek)
    }

    private fun extractStartEndDateFromWeek(weekText: String): Pair<String, String> {
        return try {
            val regex = """\((\d{2} \w{3} \d{4}) - (\d{2} \w{3} \d{4})\)""".toRegex()
            val matchResult = regex.find(weekText)

            if (matchResult != null) {
                val startDate = matchResult.groupValues[1]
                val endDate = matchResult.groupValues[2]
                return Pair(startDate, endDate)
            }

            Log.e("WeekParsing", "Failed to extract start/end date from: $weekText")
            Pair("01 Jan 1900", "01 Jan 1900") // Return nilai default aman
        } catch (e: Exception) {
            Log.e("WeekParsing", "Error extracting start/end date from week: ${e.message}")
            Pair("01 Jan 1900", "01 Jan 1900") // Return nilai default aman
        }
    }




    private fun getCurrentMonthYear(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
    }

    private fun extractMonthYearFromWeek(weekText: String): String {
        return try {
            val regex = """\((\d{2} \w{3} \d{4}) - (\d{2} \w{3} \d{4})\)""".toRegex()
            val matchResult = regex.find(weekText)

            if (matchResult != null) {
                val startMonthYear = matchResult.groupValues[1].split(" ").takeLast(2).joinToString(" ")
                val endMonthYear = matchResult.groupValues[2].split(" ").takeLast(2).joinToString(" ")

                val currentMonthYear = getCurrentMonthYear()

                // Jika minggu ini berakhir di bulan yang sedang berjalan, anggap sebagai bulan berjalan
                return if (endMonthYear == currentMonthYear) endMonthYear else startMonthYear
            }

            Log.e("WeekParsing", "Failed to extract month from: $weekText")
            "Unknown Month"
        } catch (e: Exception) {
            Log.e("WeekParsing", "Error extracting month from week: ${e.message}")
            "Unknown Month"
        }
    }

}