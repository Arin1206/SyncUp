package com.example.syncup.history.fragement_doctor

import HealthData
import android.annotation.SuppressLint
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
import java.util.Locale


class DateDoctorFragment : Fragment() {

    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView
    private lateinit var btnScrollUp: ImageView

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

        btnScrollUp = view.findViewById(R.id.btnScrollUp)
        btnScrollUp.setOnClickListener {
            recyclerView.smoothScrollToPosition(0)
        }

        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        heartRateChart = view.findViewById(R.id.heartRateChart)

        patientAdapter = PatientAdapter(mutableListOf())

        recyclerView.adapter = patientAdapter

        setupScrollListener()
        fetchHealthData()

        return view
    }


    private fun setupScrollListener() {
        recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val totalItems = layoutManager.itemCount
                val visibleItems = layoutManager.findLastVisibleItemPosition()

                // **Jika sudah mencapai setengah daftar, tampilkan tombol**
                if (visibleItems >= totalItems / 2) {
                    if (btnScrollUp.visibility == View.GONE) {
                        btnScrollUp.visibility = View.VISIBLE
                        btnScrollUp.animate().alpha(1f).setDuration(300)
                    }
                } else {
                    if (btnScrollUp.visibility == View.VISIBLE) {
                        btnScrollUp.animate().alpha(0f).setDuration(300).withEndAction {
                            btnScrollUp.visibility = View.GONE
                        }
                    }
                }
            }
        })
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
        getActualDoctorUID { doctorId ->
            if (doctorId == null) {
                Toast.makeText(requireContext(), "Dokter tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@getActualDoctorUID
            }

            // Bersihkan listener sebelumnya supaya gak dobel
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

                    // Untuk kalkulasi rerata keseluruhan
                    val allHeartRates = mutableListOf<Int>()
                    val allSystolics = mutableListOf<Int>()
                    val allDiastolics = mutableListOf<Int>()
                    val allBatteryLevels = mutableListOf<Int>()

                    var completedCount = 0
                    val totalPatients = patientIds.size

                    fun updateUI() {
                        // Hitung rerata keseluruhan berdasarkan patientDataList terbaru
                        if (patientDataList.isEmpty()) {
                            patientAdapter.updateList(emptyList())
                            avgHeartRateTextView.text = "-"
                            avgBloodPressureTextView.text = "-"
                            avgBatteryTextView.text = "-"
                            updateRecyclerViewHeight()
                            updateViewPagerHeight()
                            return
                        }

                        val avgHeartRates = patientDataList.mapNotNull { it.heartRate.toIntOrNull() }
                        val avgSystolics = patientDataList.mapNotNull { it.systolicBP.toIntOrNull() }
                        val avgDiastolics = patientDataList.mapNotNull { it.diastolicBP.toIntOrNull() }
                        val avgBatteries = allBatteryLevels
                        // Kalau kamu punya data battery per pasien, bisa tambahkan

                        // Update adapter
                        patientAdapter.updateList(patientDataList)
                        updateRecyclerViewHeight()
                        updateViewPagerHeight()

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
                    for (patientId in patientIds) {
                        // Hapus listener lama kalau ada
                        patientDetailsListeners[patientId]?.remove()
                        patientPhotoListeners[patientId]?.remove()
                        patientHeartRateListeners[patientId]?.remove()

                        // Listen data detail pasien
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

                                // Listen photo profile
                                patientPhotoListeners[patientId] = firestore.collection("patient_photoprofile")
                                    .document(patientId)
                                    .addSnapshotListener { photoDoc, errorPhoto ->
                                        val photoUrl = if (errorPhoto != null || photoDoc == null || !photoDoc.exists()) {
                                            Log.e("PhotoUrlError", "Gagal ambil photo profile untuk $patientId: ${errorPhoto?.message}")
                                            ""
                                        } else {
                                            photoDoc.getString("photoUrl") ?: ""
                                        }

                                        // Listen heart rate data
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
                                                    HealthData(
                                                        heartRate = heartRate,
                                                        bloodPressure = "$systolicBP/$diastolicBP",
                                                        batteryLevel = batteryLevel,
                                                        timestamp = "", fullTimestamp = "", userAge = null
                                                    )
                                                }

                                                if (dataList.isNotEmpty()) {
                                                    val avgHeartRate = dataList.map { it.heartRate }.average().toInt()
                                                    val avgSystolic = dataList.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
                                                    val avgDiastolic = dataList.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
                                                    val avgBattery = dataList.map { it.batteryLevel }.average().toInt()

                                                    // Tambah ke list global
                                                    // Pastikan data lama pasien ini dihapus dulu jika sudah ada, untuk menghindari data duplikat
                                                    // Simpannya dulu sementara, nanti akan kita proses di updateUI()

                                                    // Update atau tambah data pasien di patientDataList
                                                    // Cari index pasien ini di list
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

                                                    // Jangan langsung update UI di sini, tunggu semua pasien selesai
                                                    completedCount = patientDataList.size
                                                    if (completedCount == totalPatients) {
                                                        updateUI()
                                                    }
                                                } else {
                                                    // Kalau pasien tidak ada data heart rate
                                                    completedCount = patientDataList.size
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

    private fun updateViewPagerHeight() {
        view?.post {
            val parentViewPager = requireActivity().findViewById<ViewPager2>(R.id.viewPager)
            val bottomNav = requireActivity().findViewById<View>(R.id.bottom_navigation)
            val scanButtonContainer = requireActivity().findViewById<FrameLayout>(R.id.scanButtonContainer)

            parentViewPager?.let {
                val layoutParams = it.layoutParams
                val bottomNavHeight = bottomNav?.height ?: 0
                val fabScanHeight = scanButtonContainer?.height ?: 0

                // **Hanya sesuaikan tinggi tanpa menambah container utama**
                layoutParams.height = recyclerView.measuredHeight + bottomNavHeight + (fabScanHeight / 4)
                it.layoutParams = layoutParams
            }
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




}
