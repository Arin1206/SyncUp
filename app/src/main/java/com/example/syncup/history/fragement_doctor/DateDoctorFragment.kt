package com.example.syncup.history.fragement_doctor

import HealthData
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.example.syncup.R
import com.example.syncup.chart.DateChartViewDoctor

import com.example.syncup.search.PatientAdapter
import com.example.syncup.search.PatientData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale


class DateDoctorFragment : Fragment() {

    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView


    private lateinit var emptyImage: ImageView
    private lateinit var emptyText: TextView
    private lateinit var patientSpinner: Spinner

    private lateinit var patientAdapter: PatientAdapter
    private var assignedPatientsListener: ListenerRegistration? = null
    private val patientDetailsListeners = mutableMapOf<String, ListenerRegistration>()
    private val patientPhotoListeners = mutableMapOf<String, ListenerRegistration>()
    private val patientHeartRateListeners = mutableMapOf<String, ListenerRegistration>()
    private lateinit var recyclerView: RecyclerView
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var heartRateChart: DateChartViewDoctor
    private val patientList = mutableListOf<PatientData>()
    private var selectedDate: String? = null
    private var selectedPatient: String? = null
    private var selectedStatus: String? = null


    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_date_doctor, container, false)


        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)
        patientSpinner = view.findViewById(R.id.patientSpinner)
        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        heartRateChart = view.findViewById(R.id.heartRateChart)

        patientAdapter = PatientAdapter(mutableListOf(), requireContext())

        recyclerView.adapter = patientAdapter

        val dangerButton = view.findViewById<TextView>(R.id.statusDanger)
        val warningButton = view.findViewById<TextView>(R.id.statusWarning)
        val healthyButton = view.findViewById<TextView>(R.id.statusHealth)
        val resetButton = view.findViewById<TextView>(R.id.statusReset)
        resetButton.setOnClickListener {
            if (allPatients.isEmpty()) {
                emptyImage.visibility = View.VISIBLE
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                patientAdapter.updateList(allPatients)
                emptyImage.visibility = View.GONE
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }


        dangerButton.setOnClickListener {
            selectedStatus = "Danger"  // Set the global selectedStatus
            Log.d("SelectedStatus", "Selected Status: $selectedStatus")
            filterPatientsByStatus()  // No need to pass selectedStatus, it's already global
        }

        warningButton.setOnClickListener {
            selectedStatus = "Warning"  // Set the global selectedStatus
            Log.d("SelectedStatus", "Selected Status: $selectedStatus")
            filterPatientsByStatus()  // No need to pass selectedStatus, it's already global
        }

        healthyButton.setOnClickListener {
            selectedStatus = "Healthy"  // Set the global selectedStatus
            Log.d("SelectedStatus", "Selected Status: $selectedStatus")
            filterPatientsByStatus()  // No need to pass selectedStatus, it's already global
        }


        resetButton.setOnClickListener {
            // Clear all the selected filters
            selectedStatus = null
            selectedPatient = null
            selectedDate = null

            // Log to check if the status is reset
            Log.d("ResetFilters", "Filters reset: Status: $selectedStatus, Patient: $selectedPatient, Date: $selectedDate")

            // After resetting, filter again to show all patients without filters
            filterPatientsByStatus() // This will fetch all data without any filters

            // Handle visibility if no patients are available after reset
            if (allPatients.isEmpty()) {
                emptyImage.visibility = View.VISIBLE
                emptyText.visibility = View.VISIBLE
                recyclerView.visibility = View.GONE
            } else {
                emptyImage.visibility = View.GONE
                emptyText.visibility = View.GONE
                recyclerView.visibility = View.VISIBLE
            }
        }

        val dateDisplay = view.findViewById<TextView>(R.id.dateDisplay)
        val calendar = Calendar.getInstance()

//        val todayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)
//        dateDisplay.text = todayDate
//
//        val todayDisplay = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)
//        dateDisplay.text = todayDisplay

        fetchHealthData()

        emptyImage = view.findViewById(R.id.emptyImage)
        emptyText = view.findViewById(R.id.emptyText)


        dateDisplay.setOnClickListener {
            val datePicker = DatePickerDialog(requireContext(), { _, year, month, dayOfMonth ->
                selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)

                // Display the selected date in the UI
                val displayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)!!)
                dateDisplay.text = displayDate

                // Get the selected patient from the spinner (if any)
                selectedPatient = patientSpinner.selectedItem?.toString()  // This is assuming patientSpinner holds patient names

                // Pass the selected parameters to fetchHealthData
                fetchHealthData(
                    selectedPatientName = selectedPatient,  // Patient name (can be null)
                    selectedDateFilter = selectedDate,      // Pass selected date
                    selectedStatus = selectedStatus                   // No status selected
                )

            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }




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


    private val allPatients = mutableListOf<PatientData>()

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
                        selectedDateFilter = selectedDate,
                        selectedStatus = selectedStatus
                    )
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>) {
                // Handle when nothing is selected
                Toast.makeText(requireContext(), "No patient selected", Toast.LENGTH_SHORT).show()
            }
        }
    }




    private fun fetchHealthData(
        selectedPatientName: String? = null,
        selectedDateFilter: String? = null,
        selectedStatus: String? = null
    ) {
        // Log the selected filters for debugging
        Log.d("HealthDataFetch", "Selected Patient: $selectedPatientName")
        Log.d("HealthDataFetch", "Selected Date: $selectedDateFilter") // Make sure selectedDateFilter is logged
        Log.d("HealthDataFetch", "Selected Status: $selectedStatus")

        getActualDoctorUID { doctorId ->
            if (doctorId == null) {
                Toast.makeText(requireContext(), "Dokter tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@getActualDoctorUID
            }

            assignedPatientsListener?.remove()
            patientDetailsListeners.values.forEach { it.remove() }
            patientDetailsListeners.clear()
            patientPhotoListeners.values.forEach { it.remove() }
            patientPhotoListeners.clear()
            patientHeartRateListeners.values.forEach { it.remove() }
            patientHeartRateListeners.clear()

            assignedPatientsListener = firestore.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorId)
                .addSnapshotListener { assignedSnapshot, error ->
                    if (error != null) {
                        Toast.makeText(requireContext(), "Gagal mengambil data assigned_patient", Toast.LENGTH_SHORT).show()
                        return@addSnapshotListener
                    }

                    if (assignedSnapshot == null || assignedSnapshot.isEmpty) {
                        Toast.makeText(requireContext(), "Tidak ada pasien yang ditugaskan", Toast.LENGTH_SHORT).show()
                        patientAdapter.updateList(emptyList())
                        return@addSnapshotListener
                    }

                    val patientIds = assignedSnapshot.documents.mapNotNull { it.getString("patientId") }
                    val patientDataList = mutableListOf<PatientData>()
                    val allHeartRates = mutableListOf<Int>()
                    val allSystolics = mutableListOf<Int>()
                    val allDiastolics = mutableListOf<Int>()
                    val allBatteryLevels = mutableListOf<Int>()
                    var completedCount = 0
                    val totalPatients = patientIds.size

                    for (patientId in patientIds) {
                        firestore.collection("users_patient_email")
                            .document(patientId)
                            .addSnapshotListener { userDoc, errorUser ->
                                if (errorUser != null || userDoc == null || !userDoc.exists()) {
                                    completedCount++
                                    if (completedCount == totalPatients) updateUI(patientDataList, allBatteryLevels)
                                    return@addSnapshotListener
                                }

                                val name = userDoc.getString("fullName") ?: "N/A"
                                // Filter by patient name if provided
                                if (selectedPatientName != null && name != selectedPatientName) {
                                    completedCount++
                                    if (completedCount == totalPatients) updateUI(patientDataList, allBatteryLevels)
                                    return@addSnapshotListener // Skip this patient if name does not match
                                }

                                val age = userDoc.getString("age") ?: "-"
                                val gender = userDoc.getString("gender") ?: "-"
                                val email = userDoc.getString("email") ?: "-"
                                val phoneNumber = userDoc.getString("phoneNumber") ?: "-"
                                val photoUrl = userDoc.getString("photoUrl") ?: ""

                                patientPhotoListeners[patientId] = firestore.collection("patient_photoprofile")
                                    .document(patientId)
                                    .addSnapshotListener { photoDoc, errorPhoto ->
                                        val photoUrl = if (errorPhoto != null || photoDoc == null || !photoDoc.exists()) {
                                            Log.e("PhotoUrlError", "Gagal ambil photo profile untuk $patientId: ${errorPhoto?.message}")
                                            ""
                                        } else {
                                            photoDoc.getString("photoUrl") ?: ""
                                        }

                                        patientHeartRateListeners[patientId] = firestore.collection("patient_heart_rate")
                                            .whereEqualTo("userId", patientId)
                                            .addSnapshotListener { hrDocs, errorHr ->
                                                if (errorHr != null || hrDocs == null) {
                                                    Log.e("HeartRateError", "Gagal ambil heart rate untuk $patientId: ${errorHr?.message}")
                                                    completedCount++
                                                    if (completedCount == totalPatients) updateUI(patientDataList, allBatteryLevels)
                                                    return@addSnapshotListener
                                                }

                                                val dataList = hrDocs.documents.mapNotNull { doc ->
                                                    val heartRate = doc.getLong("heartRate")?.toInt() ?: return@mapNotNull null
                                                    if (heartRate == 0) return@mapNotNull null
                                                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                                                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                                                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                                                    val fullTimestamp = doc.getString("timestamp") ?: ""

                                                    // Filter by date if provided
                                                    if (selectedDateFilter != null) {
                                                        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                                        val targetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                        try {
                                                            val date = inputFormat.parse(fullTimestamp)
                                                            val formatted = targetFormat.format(date!!)
                                                            // Log filtered date comparison
                                                            Log.d("HealthDataFetch", "Comparing date: $formatted with selected date: $selectedDateFilter")
                                                            if (formatted != selectedDateFilter) return@mapNotNull null
                                                        } catch (e: Exception) {
                                                            Log.e("TimestampParse", "Error parsing timestamp: ${e.message}")
                                                            return@mapNotNull null
                                                        }
                                                    }

                                                    // Filter by status if provided
                                                    if (selectedStatus != null) {
                                                        val heartRateStatus = when (selectedStatus) {
                                                            "Danger" -> heartRate >= (220 - age.toInt())
                                                            "Warning" -> heartRate in ((220 - age.toInt()) * 0.8).toInt() until (220 - age.toInt())
                                                            "Healthy" -> heartRate < ((220 - age.toInt()) * 0.8).toInt()
                                                            else -> false
                                                        }
                                                        // Log filtering status
                                                        Log.d("HealthDataFetch", "Filtering by status: $selectedStatus with heartRateStatus: $heartRateStatus")
                                                        if (!heartRateStatus) return@mapNotNull null
                                                    }

                                                    HealthData(
                                                        heartRate = heartRate,
                                                        bloodPressure = "$systolicBP/$diastolicBP",
                                                        batteryLevel = batteryLevel,
                                                        timestamp = extractDate(fullTimestamp),
                                                        fullTimestamp = fullTimestamp,
                                                        userAge = null
                                                    )
                                                }

                                                if (dataList.isNotEmpty()) {
                                                    val avgHeartRate = dataList.map { it.heartRate }.average().toInt()
                                                    val avgSystolic = dataList.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
                                                    val avgDiastolic = dataList.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
                                                    val avgBattery = dataList.map { it.batteryLevel }.average().toInt()

                                                    val existingIndex = patientDataList.indexOfFirst { it.id == patientId }
                                                    val patientData = PatientData(
                                                        id = patientId,
                                                        name = name,
                                                        age = age,
                                                        gender = gender,
                                                        heartRate = avgHeartRate.toString(),
                                                        systolicBP = avgSystolic.toString(),
                                                        diastolicBP = avgDiastolic.toString(),
                                                        photoUrl = photoUrl,
                                                        email = email,
                                                        phoneNumber = phoneNumber,
                                                        isAssigned = true
                                                    )

                                                    allBatteryLevels.add(avgBattery)

                                                    if (existingIndex != -1) {
                                                        patientDataList[existingIndex] = patientData
                                                    } else {
                                                        patientDataList.add(patientData)
                                                    }
                                                    completedCount++
                                                    if (completedCount == totalPatients) updateUI(patientDataList, allBatteryLevels)
                                                } else {
                                                    completedCount++
                                                    if (completedCount == totalPatients) updateUI(patientDataList, allBatteryLevels)
                                                }
                                            }
                                    }
                            }
                    }
                }
        }
    }






private fun updateUI(patientDataList: List<PatientData>, allBatteryLevels: MutableList<Int>) {
    if (patientDataList.isEmpty()) {
        patientAdapter.updateList(emptyList())
        avgHeartRateTextView.text = "-"
        avgBloodPressureTextView.text = "-"
        avgBatteryTextView.text = "-"
        emptyImage.visibility = View.VISIBLE
        emptyText.visibility = View.VISIBLE
        recyclerView.visibility = View.GONE
        updateRecyclerViewHeight()
        return
    }

    val avgHeartRates = patientDataList.mapNotNull { it.heartRate.toIntOrNull() }
    val avgSystolics = patientDataList.mapNotNull { it.systolicBP.toIntOrNull() }
    val avgDiastolics = patientDataList.mapNotNull { it.diastolicBP.toIntOrNull() }
    val avgBatteries = allBatteryLevels

    emptyImage.visibility = View.GONE
    emptyText.visibility = View.GONE
    recyclerView.visibility = View.VISIBLE
    patientAdapter.updateList(patientDataList)

    if (avgHeartRates.isNotEmpty()) {
        avgHeartRateTextView.text = avgHeartRates.average().toInt().toString()
    } else {
        avgHeartRateTextView.text = "-"
    }
    if (avgSystolics.isNotEmpty() && avgDiastolics.isNotEmpty()) {
        avgBloodPressureTextView.text = "${avgSystolics.average().toInt()}/${avgDiastolics.average().toInt()}"
    } else {
        avgBloodPressureTextView.text = "-"
    }
    if (avgBatteries.isNotEmpty()) {
        avgBatteryTextView.text = "${avgBatteries.average().toInt()}%"
    } else {
        avgBatteryTextView.text = "-"
    }
    heartRateChart.setData(patientDataList)
}












private fun filterBySelectedDate(date: String) {
        val filtered = allPatients.filter { patient ->
            val ts = patient.fullTimestamp ?: return@filter false
            ts.startsWith(date) // Format timestamp di Firestore: yyyy-MM-dd HH:mm:ss
        }

        if (filtered.isEmpty()) {
            Toast.makeText(requireContext(), "Tidak ada data pada tanggal ini", Toast.LENGTH_SHORT).show()
            avgHeartRateTextView.text = "-"
            avgBloodPressureTextView.text = "-"
            avgBatteryTextView.text = "-"
        } else {
            val avgHeartRates = filtered.mapNotNull { it.heartRate.toIntOrNull() }
            val avgSystolics = filtered.mapNotNull { it.systolicBP.toIntOrNull() }
            val avgDiastolics = filtered.mapNotNull { it.diastolicBP.toIntOrNull() }
            val avgBatteries = filtered.mapNotNull { it.batteryLevel?.toIntOrNull() }

            avgHeartRateTextView.text = if (avgHeartRates.isNotEmpty()) avgHeartRates.average().toInt().toString() else "-"
            avgBloodPressureTextView.text = if (avgSystolics.isNotEmpty() && avgDiastolics.isNotEmpty()) {
                "${avgSystolics.average().toInt()}/${avgDiastolics.average().toInt()}"
            } else "-"
            avgBatteryTextView.text = if (avgBatteries.isNotEmpty()) "${avgBatteries.average().toInt()}%" else "-"
        }

        patientAdapter.updateList(filtered.sortedByDescending { it.fullTimestamp })
    }


    private fun parseDateToSortableFormat(date: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())  // 🔹 Format "2025-03-01"
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: "")
        } catch (e: Exception) {
            Log.e("DateFormatError", "Error parsing date: ${e.message}")
            date  // Jika gagal parsing, tetap gunakan tanggal asli agar tidak hilang
        }
    }


    private fun extractMonthYear(date: String): String {
        return try {
            val inputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy-MM", Locale.getDefault())  // 🔹 Format: "2025-01"
            val parsedDate = inputFormat.parse(date)
            outputFormat.format(parsedDate ?: "")
        } catch (e: Exception) {
            Log.e("DateFormatError", "Error parsing date: ${e.message}")
            date  // Jika gagal, gunakan tanggal asli
        }
    }








    // **Fungsi untuk mengambil hanya tanggal (misalnya "24 Jan 2025")**
    private fun extractDate(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())  // 🔹 Format Tanggal
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: "")
        } catch (e: Exception) {
            Log.e("DateFormatError", "Error parsing timestamp: ${e.message}")
            timestamp  // Jika gagal, gunakan timestamp asli
        }
    }

    // **Fungsi untuk mengambil jam & menit saja dari timestamp**
    private fun extractTime(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())  // 🔹 Format Jam:Menit
            val date = inputFormat.parse(timestamp)
            outputFormat.format(date ?: "")
        } catch (e: Exception) {
            Log.e("TimeFormatError", "Error parsing timestamp: ${e.message}")
            timestamp
        }
    }

    private fun updateRecyclerViewHeight() {
        recyclerView.post {
            val layoutParams = recyclerView.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            recyclerView.layoutParams = layoutParams

            // **Kurangi padding bawah agar tidak terlalu jauh dari Bottom Navigation**
            val bottomNavHeight = requireActivity().findViewById<View>(R.id.bottom_navigation)?.height ?: 0
            val scanButtonHeight = requireActivity().findViewById<View>(R.id.scanButtonContainer)?.height ?: 0

            // **Tambahkan sedikit padding agar item terakhir tidak tertutup, tapi tidak terlalu jauh**
            recyclerView.setPadding(0, 0, 0, bottomNavHeight + (scanButtonHeight / 3)) // Kurangi jarak tambahan
        }
    }

    private fun filterPatientsByStatus() {
        // Get the selected patient from the spinner
        val selectedPatientName = patientSpinner.selectedItem?.toString()

        // Log the selected patient for debugging purposes
        Log.d("SelectedPatient", "Selected Patient: $selectedPatientName")

        // If "All" is selected, return all patients without any filters
        if (selectedPatientName == "All") {
            patientAdapter.updateList(allPatients) // Show all patients
            emptyImage.visibility = View.GONE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            fetchHealthData(
                selectedPatientName = null,
                selectedDateFilter = selectedDate,
                selectedStatus = selectedStatus
            )
            return
        }

        // Filter the list based on selected status, patient, and date
        val filteredList = allPatients.filter { patient ->
            val heartRate = patient.heartRate.toIntOrNull()
            val age = patient.age.toIntOrNull()

            // Check if the patient matches the selected name
            if (selectedPatientName != null && patient.name != selectedPatientName) {
                return@filter false
            }

            // Check if the patient matches the selected date filter, if provided
            if (selectedDate != null && patient.fullTimestamp != null) {
                val patientDate = extractDate(patient.fullTimestamp)
                if (patientDate != selectedDate) {
                    return@filter false
                }
            }

            // Check if the patient's heart rate and age are valid for filtering by status
            if (heartRate != null && age != null) {
                val max = 220 - age
                val min = (max * 0.8).toInt()

                when (selectedStatus) {
                    "Danger" -> heartRate >= max
                    "Warning" -> heartRate in min until max
                    "Healthy" -> heartRate < min
                    else -> true  // No status filter, include all
                }
            } else {
                false
            }
        }

        // Update the RecyclerView based on filtered data
        patientAdapter.updateList(filteredList)

        // Call fetchHealthData to update health data display
        fetchHealthData(
            selectedPatientName = selectedPatientName,
            selectedDateFilter = selectedDate,
            selectedStatus = selectedStatus
        )

        // Handle visibility if no patients found
        if (filteredList.isEmpty()) {
            emptyImage.visibility = View.VISIBLE
            emptyText.visibility = View.VISIBLE
            recyclerView.visibility = View.GONE
        } else {
            emptyImage.visibility = View.GONE
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
        }
    }








}
