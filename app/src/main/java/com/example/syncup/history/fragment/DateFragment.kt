package com.example.syncup.history.fragment

import HealthData
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
import com.example.syncup.chart.HeartRateChartView
import com.example.syncup.home.HomeFragment
import com.example.syncup.model.HealthAdapter
import com.example.syncup.model.HealthItem
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

class DateFragment : Fragment() {

    private lateinit var avgHeartRateTextView: TextView
    private lateinit var avgBloodPressureTextView: TextView
    private lateinit var avgBatteryTextView: TextView
    private lateinit var btnScrollUp: ImageView

    private lateinit var recyclerView: RecyclerView
    private lateinit var healthDataAdapter: HealthAdapter
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private lateinit var heartRateChart: HeartRateChartView
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_date, container, false)

        btnScrollUp = view.findViewById(R.id.btnScrollUp)
        btnScrollUp.setOnClickListener {
            recyclerView.smoothScrollToPosition(0) // Scroll ke atas saat tombol diklik
        }


        avgHeartRateTextView = view.findViewById(R.id.avg_heartrate)
        avgBloodPressureTextView = view.findViewById(R.id.avg_bloodpressure)
        avgBatteryTextView = view.findViewById(R.id.textView13)

        recyclerView = view.findViewById(R.id.recycler_view_health)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        heartRateChart = view.findViewById(R.id.heartRateChart)
        healthDataAdapter = HealthAdapter(emptyList())
        recyclerView.adapter = healthDataAdapter

        // **Tambahkan Scroll Listener**
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
        getActualPatientUID { userId ->
            if (userId == null) {
                Toast.makeText(requireContext(), "User ID tidak ditemukan", Toast.LENGTH_SHORT).show()
                return@getActualPatientUID
            }

            Log.d("FirestoreDebug", "Fetching data for userId (Firestore): $userId")

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
                    .whereEqualTo("userId", userId)
                    .addSnapshotListener { documents, exception ->
                        if (exception != null) {
                            Log.e("FirestoreError", "Failed to fetch data: ${exception.message}")
                            return@addSnapshotListener
                        }
                        if (documents == null) return@addSnapshotListener

                        val groupedItems = mutableListOf<HealthItem>()
                        val groupedMap = LinkedHashMap<String, MutableList<HealthItem.DataItem>>()
                        val dataList = mutableListOf<HealthData>()

                        for (doc in documents) {
                            val heartRate = doc.getLong("heartRate")?.toInt() ?: 0
                            if (heartRate == 0) continue

                            val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                            val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                            val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                            val timestamp = doc.getString("timestamp") ?: continue

                            val formattedTime = extractTime(timestamp)
                            if (dataList.any { extractTime(it.fullTimestamp) == formattedTime }) continue

                            val formattedDate = extractDate(timestamp)

                            val healthData = HealthData(
                                heartRate = heartRate,
                                bloodPressure = "$systolicBP/$diastolicBP",
                                batteryLevel = batteryLevel,
                                timestamp = formattedTime,
                                fullTimestamp = timestamp,
                                userAge = age
                            )

                            dataList.add(healthData)

                            if (!groupedMap.containsKey(formattedDate)) {
                                groupedMap[formattedDate] = mutableListOf()
                            }
                            groupedMap[formattedDate]?.add(HealthItem.DataItem(healthData))
                        }

                        if (dataList.isEmpty()) {
                            view?.findViewById<View>(R.id.emptyStateView)?.visibility = View.VISIBLE
                            return@addSnapshotListener
                        }

                        val sortedData = dataList.sortedByDescending { it.fullTimestamp }
                        val latestDate = sortedData.firstOrNull()?.let { extractDate(it.fullTimestamp) }

                        if (latestDate != null) {
                            val latestData = groupedMap[latestDate] ?: emptyList()

                            if (latestData.isNotEmpty()) {
                                val avgHeartRate = latestData.map { it.healthData.heartRate }.average().toInt()
                                val avgSystolicBP = latestData.map { it.healthData.bloodPressure.split("/")[0].toInt() }.average().toInt()
                                val avgDiastolicBP = latestData.map { it.healthData.bloodPressure.split("/")[1].toInt() }.average().toInt()
                                val avgBatteryLevel = latestData.map { it.healthData.batteryLevel }.average().toInt()

                                if (avgHeartRate != 0 && isAdded) {
                                    activity?.runOnUiThread {
                                        avgHeartRateTextView.text = "$avgHeartRate"
                                        avgBloodPressureTextView.text = "$avgSystolicBP/$avgDiastolicBP"
                                        avgBatteryTextView.text = "$avgBatteryLevel%"

                                        heartRateChart.setData(latestData.map { it.healthData })
                                    }
                                }
                            }
                        }

                        val sortedGroupedMap = groupedMap.toSortedMap(compareByDescending { parseDateToSortableFormat(it) })

                        for ((date, items) in sortedGroupedMap) {
                            groupedItems.add(HealthItem.DateHeader(date))
                            groupedItems.addAll(items.sortedByDescending { it.healthData.fullTimestamp })
                        }

                        healthDataAdapter.updateData(groupedItems)
                        updateRecyclerViewHeight()
                        updateViewPagerHeight()
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
