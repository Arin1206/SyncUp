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
import com.example.syncup.chart.WeekChartView
import com.example.syncup.home.HomeFragment
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
    private lateinit var weekChartView: WeekChartView
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

        weekChartView = view.findViewById(R.id.heartRateChart)

        val patientId = arguments?.getString("patientId")
        if (patientId != null) {
            // Jika patientId ada, lanjutkan untuk fetch data kesehatan
            fetchHealthDataUsingPatientId(patientId)
            Log.d("Date", "$patientId")
        } else {
            Toast.makeText(requireContext(), "Patient ID tidak ditemukan", Toast.LENGTH_SHORT).show()
            Log.d("Date", "tidak ditemukan patientid")
        }

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
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        getActualPatientUID { actualPatientUID ->
            if (actualPatientUID == null) {
                Toast.makeText(requireContext(), "Failed to retrieve patient UID", Toast.LENGTH_SHORT).show()
                return@getActualPatientUID
            }

            getUserAge { age ->
                if (age == null) {
                    Toast.makeText(
                        requireContext(),
                        "Usia pengguna tidak ditemukan",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@getUserAge
                }
                firestore.collection("patient_heart_rate")
                    .whereEqualTo("userId", actualPatientUID)
                    .addSnapshotListener { documents, error ->
                        if (error != null) {
                            Log.e("WeekFragment", "Error fetching health data", error)
                            return@addSnapshotListener
                        }

                        if (documents == null || documents.isEmpty) {
                            Log.w("WeekFragment", "No health data found in Firestore")
                            if (isAdded && activity != null) {
                                activity?.runOnUiThread {
                                avgHeartRateTextView.text = "-- BPM"
                                avgBloodPressureTextView.text = "--/-- mmHg"
                                avgBatteryTextView.text = "--%"
                                healthDataAdapter.updateData(emptyList())
                                weekChartView.setData(emptyMap())
                                weekChartView.invalidate()
                            }
                                }
                            return@addSnapshotListener
                        }

                        Log.d("WeekFragment", "Total documents retrieved: ${documents.size()}")

                        val weekMap = LinkedHashMap<String, MutableList<HealthData>>()
                        val firstWeekRange = getFirstWeekOfCurrentMonth()
                        val currentMonthYear = getCurrentMonthYear()

                        for (doc in documents) {
                            val heartRate = doc.getLong("heartRate")?.toInt() ?: 0
                            if (heartRate == 0) continue

                            val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                            val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                            val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                            val timestamp = doc.getString("timestamp") ?: continue
                            val dataMonthYear = extractMonthYearFromTimestamp(timestamp)

                            if (dataMonthYear != currentMonthYear) {
                                Log.d("WeekFragment", "📌 Data diabaikan (bukan bulan berjalan): $timestamp")
                                continue
                            }

                            val weekNumber = getWeekOfMonth(timestamp, firstWeekRange)

                            Log.d("WeekFragment", "✔ Data Masuk: HR=$heartRate, BP=$systolicBP/$diastolicBP, Battery=$batteryLevel, Week=$weekNumber")

                            val healthData = HealthData(
                                heartRate = heartRate,
                                bloodPressure = "$systolicBP/$diastolicBP",
                                batteryLevel = batteryLevel,
                                timestamp = timestamp,
                                fullTimestamp = timestamp,
                                userAge = age
                            )

                            weekMap.getOrPut(weekNumber) { mutableListOf() }.add(healthData)
                        }

                        val filteredWeekMap = weekMap.toSortedMap(compareByDescending { it })
                        val latestWeek = filteredWeekMap.keys.maxByOrNull { week ->
                            extractStartEndDateFromWeek(week).first
                        }
                        val currentWeekData = latestWeek?.let { filteredWeekMap[it] }

                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                if (!currentWeekData.isNullOrEmpty()) {
                                    val avgHeartRate =
                                        currentWeekData.map { it.heartRate }.average().toInt()
                                    val avgSystolicBP =
                                        currentWeekData.map { it.bloodPressure.split("/")[0].toInt() }
                                            .average().toInt()
                                    val avgDiastolicBP =
                                        currentWeekData.map { it.bloodPressure.split("/")[1].toInt() }
                                            .average().toInt()
                                    val avgBatteryLevel =
                                        currentWeekData.map { it.batteryLevel }.average().toInt()

                                    avgHeartRateTextView.text = "$avgHeartRate"
                                    avgBloodPressureTextView.text = "$avgSystolicBP/$avgDiastolicBP"
                                    avgBatteryTextView.text = "$avgBatteryLevel%"

                                    Log.d(
                                        "WeekFragment",
                                        "✅ Updated Avg: HR=$avgHeartRate, BP=$avgSystolicBP/$avgDiastolicBP, Battery=$avgBatteryLevel, Week=$latestWeek"
                                    )
                                } else {
                                    avgHeartRateTextView.text = "-- BPM"
                                    avgBloodPressureTextView.text = "--/-- mmHg"
                                    avgBatteryTextView.text = "--%"
                                    Log.w(
                                        "WeekFragment",
                                        "⚠ Tidak ada data minggu terbaru, avg di-reset."
                                    )
                                }

                                val chartData = filteredWeekMap.mapValues { (_, dataList) ->
                                    dataList.map { it.heartRate }.average().toInt()
                                }

                                weekChartView.setData(chartData)
                                weekChartView.invalidate()
                                Log.d("WeekFragment", "Chart data set: $chartData")

                                val groupedItems = mutableListOf<WeekHealthItem>()

                                for ((week, dataList) in filteredWeekMap) {
                                    val avgHeartRate =
                                        dataList.map { it.heartRate }.average().toInt()
                                    val avgSystolicBP =
                                        dataList.map { it.bloodPressure.split("/")[0].toInt() }
                                            .average().toInt()
                                    val avgDiastolicBP =
                                        dataList.map { it.bloodPressure.split("/")[1].toInt() }
                                            .average().toInt()
                                    val avgBatteryLevel =
                                        dataList.map { it.batteryLevel }.average().toInt()

                                    val avgHealthData = HealthData(
                                        heartRate = avgHeartRate,
                                        bloodPressure = "$avgSystolicBP/$avgDiastolicBP",
                                        batteryLevel = avgBatteryLevel,
                                        timestamp = week,
                                        fullTimestamp = week,
                                        userAge = age
                                    )

                                    groupedItems.add(WeekHealthItem.WeekHeader(week))
                                    groupedItems.add(WeekHealthItem.DataItem(avgHealthData))
                                }

                                healthDataAdapter.updateData(groupedItems)
                            }
                        }
                    }
            }

        }
    }

    private fun fetchHealthDataUsingPatientId(patientId : String) {
        getAgeFromPatientId(patientId) { age ->
                if (age == null) {
                    Toast.makeText(
                        requireContext(),
                        "Usia pengguna tidak ditemukan",
                        Toast.LENGTH_SHORT
                    ).show()
                    return@getAgeFromPatientId
                }
                firestore.collection("patient_heart_rate")
                    .whereEqualTo("userId", patientId)
                    .addSnapshotListener { documents, error ->
                        if (error != null) {
                            Log.e("WeekFragment", "Error fetching health data", error)
                            return@addSnapshotListener
                        }

                        if (documents == null || documents.isEmpty) {
                            Log.w("WeekFragment", "No health data found in Firestore")
                            if (isAdded && activity != null) {
                                activity?.runOnUiThread {
                                    avgHeartRateTextView.text = "-- BPM"
                                    avgBloodPressureTextView.text = "--/-- mmHg"
                                    avgBatteryTextView.text = "--%"
                                    healthDataAdapter.updateData(emptyList())
                                    weekChartView.setData(emptyMap())
                                    weekChartView.invalidate()
                                }
                            }
                            return@addSnapshotListener
                        }

                        Log.d("WeekFragment", "Total documents retrieved: ${documents.size()}")

                        val weekMap = LinkedHashMap<String, MutableList<HealthData>>()
                        val firstWeekRange = getFirstWeekOfCurrentMonth()
                        val currentMonthYear = getCurrentMonthYear()

                        for (doc in documents) {
                            val heartRate = doc.getLong("heartRate")?.toInt() ?: 0
                            if (heartRate == 0) continue

                            val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                            val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                            val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                            val timestamp = doc.getString("timestamp") ?: continue
                            val dataMonthYear = extractMonthYearFromTimestamp(timestamp)

                            if (dataMonthYear != currentMonthYear) {
                                Log.d("WeekFragment", "📌 Data diabaikan (bukan bulan berjalan): $timestamp")
                                continue
                            }

                            val weekNumber = getWeekOfMonth(timestamp, firstWeekRange)

                            Log.d("WeekFragment", "✔ Data Masuk: HR=$heartRate, BP=$systolicBP/$diastolicBP, Battery=$batteryLevel, Week=$weekNumber")

                            val healthData = HealthData(
                                heartRate = heartRate,
                                bloodPressure = "$systolicBP/$diastolicBP",
                                batteryLevel = batteryLevel,
                                timestamp = timestamp,
                                fullTimestamp = timestamp,
                                userAge = age
                            )

                            weekMap.getOrPut(weekNumber) { mutableListOf() }.add(healthData)
                        }

                        val filteredWeekMap = weekMap.toSortedMap(compareByDescending { it })
                        val latestWeek = filteredWeekMap.keys.maxByOrNull { week ->
                            extractStartEndDateFromWeek(week).first
                        }
                        val currentWeekData = latestWeek?.let { filteredWeekMap[it] }

                        if (isAdded && activity != null) {
                            activity?.runOnUiThread {
                                if (!currentWeekData.isNullOrEmpty()) {
                                    val avgHeartRate =
                                        currentWeekData.map { it.heartRate }.average().toInt()
                                    val avgSystolicBP =
                                        currentWeekData.map { it.bloodPressure.split("/")[0].toInt() }
                                            .average().toInt()
                                    val avgDiastolicBP =
                                        currentWeekData.map { it.bloodPressure.split("/")[1].toInt() }
                                            .average().toInt()
                                    val avgBatteryLevel =
                                        currentWeekData.map { it.batteryLevel }.average().toInt()

                                    avgHeartRateTextView.text = "$avgHeartRate"
                                    avgBloodPressureTextView.text = "$avgSystolicBP/$avgDiastolicBP"
                                    avgBatteryTextView.text = "$avgBatteryLevel%"

                                    Log.d(
                                        "WeekFragment",
                                        "✅ Updated Avg: HR=$avgHeartRate, BP=$avgSystolicBP/$avgDiastolicBP, Battery=$avgBatteryLevel, Week=$latestWeek"
                                    )
                                } else {
                                    avgHeartRateTextView.text = "-- BPM"
                                    avgBloodPressureTextView.text = "--/-- mmHg"
                                    avgBatteryTextView.text = "--%"
                                    Log.w(
                                        "WeekFragment",
                                        "⚠ Tidak ada data minggu terbaru, avg di-reset."
                                    )
                                }

                                val chartData = filteredWeekMap.mapValues { (_, dataList) ->
                                    dataList.map { it.heartRate }.average().toInt()
                                }

                                weekChartView.setData(chartData)
                                weekChartView.invalidate()
                                Log.d("WeekFragment", "Chart data set: $chartData")

                                val groupedItems = mutableListOf<WeekHealthItem>()

                                for ((week, dataList) in filteredWeekMap) {
                                    val avgHeartRate =
                                        dataList.map { it.heartRate }.average().toInt()
                                    val avgSystolicBP =
                                        dataList.map { it.bloodPressure.split("/")[0].toInt() }
                                            .average().toInt()
                                    val avgDiastolicBP =
                                        dataList.map { it.bloodPressure.split("/")[1].toInt() }
                                            .average().toInt()
                                    val avgBatteryLevel =
                                        dataList.map { it.batteryLevel }.average().toInt()

                                    val avgHealthData = HealthData(
                                        heartRate = avgHeartRate,
                                        bloodPressure = "$avgSystolicBP/$avgDiastolicBP",
                                        batteryLevel = avgBatteryLevel,
                                        timestamp = week,
                                        fullTimestamp = week,
                                        userAge = age
                                    )

                                    groupedItems.add(WeekHealthItem.WeekHeader(week))
                                    groupedItems.add(WeekHealthItem.DataItem(avgHealthData))
                                }

                                healthDataAdapter.updateData(groupedItems)
                            }
                        }
                    }
            }
    }

    private fun getAgeFromPatientId(patientId: String, onResult: (Int?) -> Unit) {
        firestore.collection("users_patient_email")
            .whereEqualTo("userId", patientId)
            .get()
            .addOnSuccessListener { documents ->
                if (documents.isEmpty) {
                    // Jika tidak ditemukan di email, coba di phone number
                    firestore.collection("users_patient_phonenumber")
                        .whereEqualTo("userId", patientId)
                        .get()
                        .addOnSuccessListener { docs ->
                            if (docs.isEmpty) {
                                onResult(null)  // Tidak ditemukan di kedua tempat
                            } else {
                                val age = docs.firstOrNull()?.getString("age")?.toInt()
                                onResult(age)
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirestoreError", "Failed to fetch age from phone number: ${e.message}")
                            onResult(null)
                        }
                } else {
                    val age = documents.firstOrNull()?.getString("age")?.toInt()
                    onResult(age)
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreError", "Failed to fetch age from email: ${e.message}")
                onResult(null)
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
