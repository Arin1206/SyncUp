package com.example.syncup.history.fragement_doctor

import HealthData
import android.annotation.SuppressLint
import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
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

    private lateinit var patientAdapter: PatientAdapter
    private var assignedPatientsListener: ListenerRegistration? = null
    private val patientDetailsListeners = mutableMapOf<String, ListenerRegistration>()
    private val patientPhotoListeners = mutableMapOf<String, ListenerRegistration>()
    private val patientHeartRateListeners = mutableMapOf<String, ListenerRegistration>()
    private lateinit var recyclerView: RecyclerView
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var heartRateChart: DateChartViewDoctor
    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_date_doctor, container, false)


        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)

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
            filterPatientsByStatus("Danger")
        }
        warningButton.setOnClickListener {
            filterPatientsByStatus("Warning")
        }
        healthyButton.setOnClickListener {
            filterPatientsByStatus("Healthy")
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
                val selectedDate = String.format("%04d-%02d-%02d", year, month + 1, dayOfMonth)

                // Tampilkan di UI
                val displayDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                    .format(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(selectedDate)!!)
                dateDisplay.text = displayDate

                // Panggil fungsi ini dengan tanggal yang dipilih
                fetchHealthData(selectedDate)

            }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH))
            datePicker.show()
        }


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


    private val allPatients = mutableListOf<PatientData>()

    private fun fetchHealthData(selectedDateFilter: String? = null) {
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
                    fun updateUI() {
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
                        allPatients.clear()
                        allPatients.addAll(patientDataList)
                    }
                    for (patientId in patientIds) {
                        patientDetailsListeners[patientId]?.remove()
                        patientPhotoListeners[patientId]?.remove()
                        patientHeartRateListeners[patientId]?.remove()
                        patientDetailsListeners[patientId] = firestore.collection("users_patient_email")
                            .document(patientId)
                            .addSnapshotListener { userDoc, errorUser ->
                                if (errorUser != null || userDoc == null || !userDoc.exists()) {
                                    Log.e("FetchUserError", "Gagal ambil user detail untuk $patientId: ${errorUser?.message}")
                                    completedCount++
                                    if (completedCount == totalPatients) updateUI()
                                    return@addSnapshotListener
                                }
                                val name = userDoc.getString("fullName") ?: "N/A"
                                val age = userDoc.getString("age") ?: "-"
                                val gender = userDoc.getString("gender") ?: "-"
                                val email = userDoc.getString("email") ?: "-"
                                val phoneNumber = userDoc.getString("phoneNumber") ?: "-"
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
                                                    if (completedCount == totalPatients) updateUI()
                                                    return@addSnapshotListener
                                                }
                                                val dataList = hrDocs.documents.mapNotNull { doc ->
                                                    val heartRate = doc.getLong("heartRate")?.toInt() ?: return@mapNotNull null
                                                    if (heartRate == 0) return@mapNotNull null
                                                    val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                                                    val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                                                    val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                                                    val fullTimestamp = doc.getString("timestamp") ?: ""
                                                    Log.d("DEBUG_FILTER", "Full timestamp: $fullTimestamp | Filter: $selectedDateFilter")
                                                    if (selectedDateFilter != null) {
                                                        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                                                        val targetFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                                                        try {
                                                            val date = inputFormat.parse(fullTimestamp)
                                                            val formatted = targetFormat.format(date!!)
                                                            if (formatted != selectedDateFilter) return@mapNotNull null
                                                            Log.d("DEBUG_COMPARE", "Comparing: ${formatted} == $selectedDateFilter")

                                                        } catch (e: Exception) {
                                                            Log.e("TimestampParse", "Error parsing timestamp: ${e.message}")
                                                            return@mapNotNull null
                                                        }
                                                    }
                                                    HealthData(
                                                        heartRate = heartRate,
                                                        bloodPressure = "$systolicBP/$diastolicBP",
                                                        batteryLevel = batteryLevel,
                                                        timestamp = extractDate(fullTimestamp), // jika kamu ingin pakai
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
                                                    if (completedCount == totalPatients) {
                                                        updateUI()
                                                    }
                                                } else {
                                                    completedCount++
                                                    if (completedCount == totalPatients) {
                                                        updateUI()
                                                    }
                                                }
                                            }
                                    }
                            }
                    }
                }
        }
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

    private fun filterPatientsByStatus(status: String) {
        val filteredList = allPatients.filter { patient ->
            val heartRate = patient.heartRate.toIntOrNull()
            val age = patient.age.toIntOrNull()
            if (heartRate != null && age != null) {
                val max = 220 - age
                val min = (max * 0.8).toInt()
                when (status) {
                    "Danger" -> heartRate >= max
                    "Warning" -> heartRate in min until max
                    "Healthy" -> heartRate < min
                    else -> false
                }
            } else {
                false
            }
        }

        patientAdapter.updateList(filteredList)



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
