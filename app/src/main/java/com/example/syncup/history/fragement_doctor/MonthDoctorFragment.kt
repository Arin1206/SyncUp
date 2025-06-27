package com.example.syncup.history.fragement_doctor

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
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
    private lateinit var patientSpinner: Spinner
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private val allMonthMap = mutableMapOf<String, MutableList<PatientData>>()

    private var healthDataListener: ListenerRegistration? = null
    private val patientList = mutableListOf<PatientData>()
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
        patientSpinner = view.findViewById(R.id.patientSpinner)
        fetchHealthData()
        fetchPatientsForSpinner()

        return view
    }

    private fun fetchPatientsForSpinner() {
        getActualDoctorUID { doctorId ->
            if (doctorId == null) {
                Toast.makeText(requireContext(), "Dokter tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@getActualDoctorUID
            }

            firestore.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorId)
                .get()
                .addOnSuccessListener { assignedSnapshot ->
                    Log.d("AssignedPatientsQuery", "Assigned Patients: ${assignedSnapshot.documents.size}")

                    if (assignedSnapshot.isEmpty) {
                        Toast.makeText(requireContext(), "Tidak ada pasien yang ditugaskan", Toast.LENGTH_SHORT).show()
                        return@addOnSuccessListener
                    }

                    val patientIds = assignedSnapshot.documents.mapNotNull { it.getString("patientId") }
                    Log.d("AssignedPatientIds", "Patient IDs: $patientIds")

                    patientList.clear()

                    // Add "All" option to the patient list first
                    patientList.add(PatientData(name = "All", id = "All", age = "", gender = "", heartRate = "", systolicBP = "", diastolicBP = "", email = "", phoneNumber = "", photoUrl = ""))

                    // Fetch patient details for the spinner
                    for (patientId in patientIds) {
                        firestore.collection("users_patient_email").document(patientId).get()
                            .addOnSuccessListener { userDoc ->
                                Log.d("UserDoc", "Fetched user data for patient: $patientId")

                                val name = userDoc.getString("fullName") ?: "N/A"
                                Log.d("PatientData", "Patient name: $name")

                                if (name != "N/A") {
                                    val age = userDoc.getString("age") ?: "N/A"
                                    val gender = userDoc.getString("gender") ?: "N/A"
                                    val heartRate = userDoc.getString("heartRate") ?: "N/A"
                                    val systolicBP = userDoc.getString("systolicBP") ?: "N/A"
                                    val diastolicBP = userDoc.getString("diastolicBP") ?: "N/A"
                                    val email = userDoc.getString("email") ?: "N/A"
                                    val phoneNumber = userDoc.getString("phoneNumber") ?: "N/A"
                                    val photoUrl = userDoc.getString("photoUrl") ?: "N/A"

                                    val patient = PatientData(
                                        id = patientId,
                                        name = name,
                                        age = age,
                                        gender = gender,
                                        heartRate = heartRate,
                                        systolicBP = systolicBP,
                                        diastolicBP = diastolicBP,
                                        email = email,
                                        phoneNumber = phoneNumber,
                                        photoUrl = photoUrl
                                    )
                                    patientList.add(patient)

                                    Log.d("PatientFetch", "Added patient: $name")
                                } else {
                                    Log.d("PatientFetch", "Skipped patient due to invalid name: $patientId")
                                }

                                // After fetching all patients, set the spinner listener
                                if (patientList.isNotEmpty()) {
                                    val patientNames = patientList.map { it.name }
                                    val adapter = ArrayAdapter(requireContext(), R.layout.spinner_item, patientNames)  // Use custom layout here
                                    patientSpinner.adapter = adapter
                                    Log.d("SpinnerAdapter", "Adapter set with names: $patientNames")

                                    // Apply the custom background to the spinner
                                    patientSpinner.setBackgroundResource(R.drawable.spinner_background)  // Set the background drawable

                                    // Set the "All" option as the default selected item
                                    patientSpinner.setSelection(0)

                                    // Set the spinner listener here after populating the data
                                    setSpinnerListener()
                                } else {
                                    Toast.makeText(requireContext(), "No patients available", Toast.LENGTH_SHORT).show()
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("PatientFetchError", "Failed to fetch data for patient: $patientId", exception)
                            }
                    }
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Gagal mengambil data pasien", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun setSpinnerListener() {
        patientSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                val selectedPatient = patientList[position]

                Log.d("SelectedPatient", "Selected Patient: ${selectedPatient.name}")

                // If "All" is selected, fetch all data without filters
                if (selectedPatient.name == "All") {
                    fetchHealthData() // Fetch all data without filters
                } else {
                    fetchHealthData(
                        selectedPatientName = selectedPatient.name,
                    )
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle when nothing is selected
                Toast.makeText(requireContext(), "No patient selected", Toast.LENGTH_SHORT).show()
            }
        }
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



    private fun fetchHealthData(selectedPatientName: String? = null) {
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
                        updateUI(emptyList(), emptyMap())
                        return@addOnSuccessListener
                    }

                    val allGroupedItems = mutableListOf<MonthHealthItemDoctor>()
                    var loadedCount = 0
                    val totalPatients = patientIds.size
                    val patientDataMap = mutableMapOf<String, MutableMap<String, PatientData>>() // Grouping by Patient ID and Month

                    // Loop through each patient ID
                    for (id in patientIds) {
                        firestore.collection("users_patient_email").document(id).get()
                            .addOnSuccessListener { userDoc ->
                                val name = userDoc["fullName"] as? String ?: "-"
                                val photoUrl = userDoc["photoUrl"] as? String ?: ""
                                val age = userDoc["age"] as? String ?: "-"
                                val gender = userDoc["gender"] as? String ?: "-"
                                val email = userDoc["email"] as? String ?: "-"
                                val phoneNumber = userDoc["phoneNumber"] as? String ?: "-"

                                // Fetch photo from "patient_photoprofile" collection
                                firestore.collection("patient_photoprofile").document(id).get()
                                    .addOnSuccessListener { photoDoc ->
                                        val profilePhotoUrl = photoDoc.getString("photoUrl") ?: ""

                                        // Skip this patient if selectedPatientName doesn't match or if it's null

                                        firestore.collection("patient_heart_rate")
                                            .whereEqualTo("userId", id)
                                            .get()
                                            .addOnSuccessListener { hrDocs ->
                                                val monthMap = mutableMapOf<String, MutableList<Triple<Int, Int?, Int?>>>()
                                                val batteryMap = mutableMapOf<String, MutableList<Int>>()  // Month -> battery list

                                                // Process each heart rate record
                                                hrDocs.forEach { doc ->
                                                    val heartRate = doc.getLong("heartRate")?.toInt()
                                                    val timestamp = doc.getString("timestamp")
                                                    val systolic = doc.getDouble("systolicBP")?.toInt()
                                                    val diastolic = doc.getDouble("diastolicBP")?.toInt()

                                                    // Only process the entry if all values are valid
                                                    if (heartRate == null || heartRate == 0 || timestamp == null || systolic == null || diastolic == null) {
                                                        // Skip this entry
                                                        return@forEach
                                                    }

                                                    val monthYear = extractMonthYearFromTimestamp(timestamp)

                                                    // Add heart rate, systolic, diastolic to monthMap for aggregation
                                                    monthMap.getOrPut(monthYear) { mutableListOf() }
                                                        .add(Triple(heartRate, systolic, diastolic))

                                                    // Collect battery level if available
                                                    val battery = doc.getDouble("batteryLevel")?.toInt()
                                                    if (battery != null) {
                                                        batteryMap.getOrPut(monthYear) { mutableListOf() }.add(battery)
                                                    }
                                                }

                                                // Aggregate data for the same patient per month
                                                monthMap.forEach { (month, entries) ->
                                                    val avgHR = entries.map { it.first }.average().toInt()
                                                    val avgSys = entries.mapNotNull { it.second }.average().toInt()
                                                    val avgDia = entries.mapNotNull { it.third }.average().toInt()

                                                    val avgBattery = batteryMap[month]?.average()?.toInt()?.toString()

                                                    // Check if the patient already exists in the same month
                                                    val existingPatientData = patientDataMap.getOrPut(id) { mutableMapOf() }
                                                    if (existingPatientData.containsKey(month)) {
                                                        // Update the existing patient data for this month
                                                        existingPatientData[month]?.apply {
                                                            heartRate += " / $avgHR" // Accumulate heart rate
                                                            systolicBP = "$systolicBP / $avgSys"
                                                            diastolicBP = "$diastolicBP / $avgDia"
                                                            batteryLevel = "$batteryLevel / $avgBattery"
                                                        }
                                                    } else {
                                                        // Create new entry for the patient and month
                                                        existingPatientData[month] = PatientData(
                                                            id = id,
                                                            name = name,
                                                            age = age,
                                                            gender = gender,
                                                            email = email,
                                                            phoneNumber = phoneNumber,
                                                            heartRate = avgHR.toString(),
                                                            systolicBP = avgSys?.toString() ?: "None",
                                                            diastolicBP = avgDia?.toString() ?: "None",
                                                            photoUrl = profilePhotoUrl,  // Use the photo URL from "patient_photoprofile"
                                                            isAssigned = true,
                                                            batteryLevel = avgBattery
                                                        )
                                                    }
                                                }

                                                loadedCount++
                                                if (loadedCount == totalPatients) {
                                                    val groupedByMonth = mutableMapOf<String, MutableList<PatientData>>()

                                                    patientDataMap.values.forEach { monthData ->
                                                        monthData.forEach { (month, pdata) ->
                                                            groupedByMonth.getOrPut(month) { mutableListOf() }.add(pdata)
                                                        }
                                                    }

                                                    val sortedMonths = groupedByMonth.keys.sortedByDescending { month ->
                                                        parseMonthYearToDate(month)
                                                    }

                                                    val filteredMonthMap = mutableMapOf<String, MutableList<PatientData>>()
                                                    sortedMonths.forEach { month ->
                                                        val patients = groupedByMonth[month]

                                                        // Filter berdasarkan nama pasien jika selectedPatientName != null
                                                        val filteredPatients = if (selectedPatientName != null) {
                                                            patients?.filter { it.name.equals(selectedPatientName, ignoreCase = true) }

                                                        } else {
                                                            patients
                                                        }

                                                        if (!filteredPatients.isNullOrEmpty()) {
                                                            allGroupedItems.add(MonthHealthItemDoctor.MonthHeader(month))
                                                            filteredPatients.forEach { pdata ->
                                                                allGroupedItems.add(MonthHealthItemDoctor.DataItem(pdata))
                                                            }
                                                            // Simpan untuk update chart juga
                                                            filteredMonthMap[month] = filteredPatients.toMutableList()
                                                        }
                                                    }

                                                    updateUI(allGroupedItems, filteredMonthMap)
                                                }


                                            }
                                    }
                            }
                    }
                }
        }
    }





    private fun parseMonthYearToDate(monthYear: String): Date {
        return try {
            val formatter = SimpleDateFormat("MMMM yyyy", Locale.ENGLISH)
            formatter.parse(monthYear) ?: Date(0)
        } catch (e: Exception) {
            Date(0) // Default ke waktu paling lama jika format gagal
        }
    }




    private fun updateUI(
        data: List<MonthHealthItemDoctor>,
        filteredMonthMap: Map<String, List<PatientData>>
    ) {
        if (!isAdded) return

        requireActivity().runOnUiThread {
            healthDataAdapter.updateData(data)

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

            // Chart pakai data dari filteredMonthMap
            val monthAverages = filteredMonthMap.mapValues { (_, list) ->
                list.mapNotNull { it.heartRate.toIntOrNull() }.average().toInt()
            }.mapKeys { (monthFull) -> convertToShortMonth(monthFull) }

            updateChartData(monthAverages)
            Log.d("ChartUpdate", "Filtered chart data: $monthAverages")
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