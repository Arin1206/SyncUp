package com.example.syncup.home

import HealthData
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.adapter.PatientPagerAdapter
import com.example.syncup.databinding.FragmentHomeBinding
import com.example.syncup.databinding.FragmentHomeDoctorBinding
import com.example.syncup.profile.ProfileDoctorFragment
import com.example.syncup.profile.ProfilePatientFragment
import com.example.syncup.search.SearchDoctorFragment
import com.example.syncup.search.SearchPatientFragment
import com.example.syncup.welcome.WelcomeActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class HomeDoctorFragment : Fragment() {

    private var _binding: FragmentHomeDoctorBinding? = null
    private val binding get() = _binding!!
    private var globalLayoutListener: ViewTreeObserver.OnGlobalLayoutListener? = null
    private var pageChangeCallback: ViewPager2.OnPageChangeCallback? = null
    private var monthChartView: com.example.syncup.chart.MonthChartViewHome? = null
    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var currentLat: Double? = null
    private var currentLon: Double? = null
    private val firestore = FirebaseFirestore.getInstance()
    private lateinit var searchDoctor: EditText
    private lateinit var parentLayout: ConstraintLayout


    private fun setupUI(view: View) {
        // Set listener pada parent layout untuk menangkap klik di luar input
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // Clear focus dari EditText
                searchDoctor.clearFocus()
                hideKeyboard()
            }
            false
        }


        // Tambahkan listener agar keyboard turun saat EditText kehilangan fokus
        searchDoctor.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event?.action == KeyEvent.ACTION_DOWN && event.keyCode == KeyEvent.KEYCODE_ENTER)) {

                val searchText = searchDoctor.text.toString().trim()
                hideKeyboard()

                // Jika input kosong, tampilkan semua dokter
                navigateToSearchFragment(searchText)
                true
            } else {
                false
            }
        }


    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?  {
        _binding = FragmentHomeDoctorBinding.inflate(inflater, container, false)

        val view = binding.root

        searchDoctor = binding.searchDoctor

        setupUI(view)

        parentLayout = view.findViewById(R.id.main)
        parentLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }

        return view



    }

    private fun startLiveLocationUpdates() {
        val mapsTextView = view?.findViewById<TextView>(R.id.maps)

        val locationRequest = com.google.android.gms.location.LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                if (!isAdded) return
                val location = locationResult.lastLocation
                if (location != null) {
                    currentLat = location.latitude
                    currentLon = location.longitude

                    // Reverse geocode untuk mendapatkan alamat lengkap
                    val geocoder = Geocoder(requireContext(), Locale.getDefault())
                    try {
                        val addresses = geocoder.getFromLocation(currentLat!!, currentLon!!, 1)
                        if (!addresses.isNullOrEmpty()) {
                            val address = addresses[0]
                            val fullAddress = buildString {
                                append(address.thoroughfare ?: "")              // Nama jalan
                                if (!address.subThoroughfare.isNullOrEmpty()) {
                                    append(" ${address.subThoroughfare}")       // Nomor rumah
                                }
                                if (!address.locality.isNullOrEmpty()) {
                                    append(", ${address.locality}")             // Kota
                                }
                                // Jangan tambahkan postalCode dan countryName
                            }

                            mapsTextView?.text = fullAddress
                        } else {
                            mapsTextView?.text = "Address not found"
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        mapsTextView?.text = "Failed to get address"
                    }
                } else {
                    mapsTextView?.text = "Location not available"
                }
            }
        }

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, requireActivity().mainLooper)
        } else {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun fetchAveragePatientData() {
        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Log.e("DoctorData", "Doctor UID is null")
                return@getActualDoctorUID
            }

            firestore.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUID)
                .addSnapshotListener { assignedSnapshot, assignedError ->
                    if (assignedError != null) {
                        Log.e("DoctorData", "Error listening to assigned_patient", assignedError)
                        return@addSnapshotListener
                    }

                    val patientIds = assignedSnapshot?.documents?.mapNotNull { it.getString("patientId") } ?: emptyList()
                    if (patientIds.isEmpty()) {
                        Log.d("DoctorData", "No assigned patients found")
                        return@addSnapshotListener
                    }

                    // Ambil data usia dari koleksi users
                    val emailQuery = firestore.collection("users_patient_email")
                        .whereIn("userId", patientIds)
                        .get()

                    val phoneQuery = firestore.collection("users_patient_phonenumber")
                        .whereIn("userId", patientIds)
                        .get()

                    Tasks.whenAllSuccess<QuerySnapshot>(emailQuery, phoneQuery)
                        .addOnSuccessListener { results ->
                            val emailSnapshot = results[0] as QuerySnapshot
                            val phoneSnapshot = results[1] as QuerySnapshot

                            val emailAges = emailSnapshot.documents.mapNotNull {
                                it.getString("age")?.toIntOrNull()
                            }
                            val phoneAges = phoneSnapshot.documents.mapNotNull {
                                it.getString("age")?.toIntOrNull()
                            }


                            val allAges = emailAges + phoneAges
                            val avgAge = if (allAges.isNotEmpty()) allAges.sum() / allAges.size else null

                            firestore.collection("patient_heart_rate")
                                .whereIn("userId", patientIds)
                                .addSnapshotListener { heartSnapshot, heartError ->
                                    if (heartError != null) {
                                        Log.e("DoctorData", "Error listening to patient_heart_rate", heartError)
                                        return@addSnapshotListener
                                    }

                                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                                    var totalHeartRate = 0.0
                                    var totalSystolic = 0.0
                                    var totalDiastolic = 0.0
                                    var totalBattery = 0.0
                                    var count = 0

                                    for (doc in heartSnapshot?.documents ?: emptyList()) {
                                        val timestampStr = doc.getString("timestamp") ?: continue
                                        if (!timestampStr.startsWith(today)) continue

                                        val heartRate = doc.getDouble("heartRate") ?: continue
                                        if (heartRate == 0.0) continue
                                        val systolic = doc.getDouble("systolicBP") ?: continue
                                        val diastolic = doc.getDouble("diastolicBP") ?: continue
                                        val battery = doc.getDouble("batteryLevel") ?: 0.0

                                        totalHeartRate += heartRate
                                        totalSystolic += systolic
                                        totalDiastolic += diastolic
                                        totalBattery += battery
                                        count++
                                    }

                                    _binding?.let { binding ->
                                        if (count > 0) {
                                            val avgHeartRate = (totalHeartRate / count).roundToInt()
                                            val avgSystolic = (totalSystolic / count).roundToInt()
                                            val avgDiastolic = (totalDiastolic / count).roundToInt()
                                            val avgBattery = (totalBattery / count).roundToInt()

                                            binding.heartRateValue.text = "$avgHeartRate"
                                            binding.bpValue.text = "$avgSystolic/$avgDiastolic"
                                            binding.batteryValue.text = "$avgBattery%"

                                            // Update indikator dan warna latar belakang
                                            updateIndicator(avgHeartRate, avgAge)
                                            val chartView = binding.heartRateChart
                                            val heartRates = heartSnapshot?.documents
                                                ?.filter {
                                                    val t = it.getString("timestamp") ?: return@filter false
                                                    t.startsWith(today)
                                                }
                                                ?.mapNotNull { it.getDouble("heartRate")?.toFloat() }
                                                ?: emptyList()

                                            chartView.setData(heartRates)



                                        } else {
                                            binding.heartRateValue.text = "null"
                                            binding.bpValue.text = "null"
                                            binding.batteryValue.text = "null"
                                            binding.indicatorValue.text = "null"
                                            Log.d("DoctorData", "No valid patient data for today")
                                        }
                                    }
                                }
                        }
                }
        }
    }
    private fun stopLiveLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
    }


    private fun updateIndicator(avgHeartRate: Int, avgAge: Int?) {
        avgAge?.let { age ->
            val maxWarning = 220 - age
            val minWarning = (maxWarning * 0.8).toInt()

            val (statusText, colorRes) = when {
                avgHeartRate >= maxWarning -> "Danger" to R.color.red
                avgHeartRate < minWarning -> "Health" to R.color.green
                else -> "Warning" to R.color.yellow
            }

            _binding?.let { binding ->
                binding.indicatorValue.text = statusText
                binding.indicatorValue.setTextColor(ContextCompat.getColor(binding.root.context, android.R.color.black))

                val background = ContextCompat.getDrawable(binding.root.context, R.drawable.rounded_status_bg)?.mutate()
                val wrappedDrawable = background?.let { DrawableCompat.wrap(it) }
                wrappedDrawable?.let {
                    DrawableCompat.setTint(it, ContextCompat.getColor(binding.root.context, colorRes))
                    binding.indicatorBox.background = it
                }
            }
        } ?: run {
            _binding?.indicatorValue?.text = "Unknown"
        }
    }
    private fun checkLocationPermissionAndUpdateMaps() {
        val mapsTextView = view?.findViewById<TextView>(R.id.maps)

        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        } else {
            // Cek apakah layanan lokasi aktif (GPS atau Network)
            val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

            if (!isGpsEnabled && !isNetworkEnabled) {
                // Jika lokasi tidak aktif, tampilkan pesan dan arahkan ke setting
                mapsTextView?.text = "Location not available, enable location services"
                openLocationSettings()
            } else {
                // Jika layanan lokasi aktif, mulai live updates
                startLiveLocationUpdates()
            }
        }
    }
    private fun openLocationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }



    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = PatientPagerAdapter(this)
        binding.viewPager.adapter = adapter

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (!userId.isNullOrEmpty()) {
            loadActualDoctorProfilePicture()
        }
        fetchAveragePatientData()
        fetchWeeklyAverages()
        monthChartView = view.findViewById(R.id.monthHeartRateChart)
        fetchMonthlyAverages()

        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, position ->
            tab.text = if (position == 0) "Online" else "Offline"
        }.attach()

        val profileImage = view.findViewById<ImageView>(R.id.profile)
        profileImage.setOnClickListener {
            navigateToPatientFragment()
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(requireContext())

        val mapsTextView = view.findViewById<TextView>(R.id.maps)
        mapsTextView.setOnClickListener {
            if (currentLat != null && currentLon != null) {
                try {
                    // Coba buka dengan Google Maps
                    val gmmIntentUri = Uri.parse("geo:${currentLat},${currentLon}?q=${currentLat},${currentLon}")
                    val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                    mapIntent.setPackage("com.google.android.apps.maps")
                    startActivity(mapIntent)
                } catch (e: Exception) {
                    Log.e(HomeFragment.TAG, "Google Maps failed, trying web URL.")

                    // Jika gagal, buka dengan URL Maps
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$currentLat,$currentLon")
                    )
                    startActivity(webIntent)
                }
            } else {
                Log.e(HomeFragment.TAG, "Coordinates are null. Cannot open maps.")
            }
        }


        val dayTextView = binding.day
        val currentDate = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(currentDate)

        dayTextView.text = "This Day, $formattedDate"
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {

            private val globalLayoutListener = ViewTreeObserver.OnGlobalLayoutListener {
                val currentView = getChildAtCurrentPosition() ?: return@OnGlobalLayoutListener
                updateViewPagerHeightForChild(currentView)
            }

            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                val currentView = getChildAtCurrentPosition()
                currentView?.viewTreeObserver?.addOnGlobalLayoutListener(globalLayoutListener)
            }

            private fun getChildAtCurrentPosition(): View? {
                val binding = _binding ?: return null  // Cegah null pointer
                val recyclerView = binding.viewPager.getChildAt(0) as? ViewGroup ?: return null
                val position = binding.viewPager.currentItem
                if (position < recyclerView.childCount) {
                    return recyclerView.getChildAt(position)
                }
                return null
            }


            private fun updateViewPagerHeightForChild(view: View) {
                view.post {
                    val widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(view.width, View.MeasureSpec.EXACTLY)
                    val heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                    view.measure(widthMeasureSpec, heightMeasureSpec)

                    val newHeight = view.measuredHeight
                    val layoutParams = binding.viewPager.layoutParams
                    if (layoutParams.height != newHeight) {
                        layoutParams.height = newHeight
                        binding.viewPager.layoutParams = layoutParams
                    }
                }
            }
        })


        checkLocationPermissionAndUpdateMaps()

        // Trigger resize untuk halaman pertama
        binding.tabLayout.post {
            val tabStrip = binding.tabLayout.getChildAt(0) as ViewGroup
            for (i in 0 until tabStrip.childCount) {
                val tabView = tabStrip.getChildAt(i)

                // Set margin kanan antar tab
                val params = tabView.layoutParams as ViewGroup.MarginLayoutParams
                params.marginEnd = 12
                params.width = (80 * resources.displayMetrics.density).toInt()  // Lebar 80dp
                params.height = (30 * resources.displayMetrics.density).toInt() // Tinggi 22dp
                tabView.layoutParams = params

            }
        }



    }


    private fun navigateToSearchFragment(query: String) {
        val bundle = Bundle().apply {
            putString("searchQuery", query) // Kirim keyword ke fragment tujuan
        }
        val fragment = SearchDoctorFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, fragment)
            .addToBackStack(null)
            .commit()
    }
    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
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


    private fun loadActualDoctorProfilePicture() {
        getActualDoctorUID { patientUserId ->
            if (patientUserId != null) {
                loadProfilePicture(patientUserId)
            } else {
                Log.e("ProfilePatient", "Failed to get actual patient UID")
                // Bisa juga kasih placeholder/default image kalau perlu
            }
        }
    }

    private fun loadProfilePicture(userId: String) {
        firestore.collection("doctor_photoprofile").document(userId).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    val photoUrl = document.getString("photoUrl")
                    Log.d("HomeFragment", "Photo URL from Firestore: $photoUrl")
                    if (!photoUrl.isNullOrEmpty()) {
                        Glide.with(this)
                            .load(photoUrl)
                            .circleCrop()
                            .placeholder(R.drawable.account_circle) // gambar default sementara
                            .error(R.drawable.account_circle)        // gambar error jika gagal load
                            .into(binding.profile)
                    } else {
                        Log.w("HomeFragment", "photoUrl field is empty or null")
                    }
                } else {
                    Log.w("HomeFragment", "No document found for userId: $userId")
                }
            }
            .addOnFailureListener { e ->
                Log.e("HomeFragment", "Failed to get photo profile", e)
            }

    }

    private fun navigateToPatientFragment() {
        val fragment = ProfileDoctorFragment()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, fragment) // Sesuaikan dengan container di layout
            .addToBackStack(null) // Tambahkan ke backstack agar bisa kembali ke Home
            .commit()
    }


    private fun getPatientAgeByUID(patientUID: String, onResult: (Int?) -> Unit) {
        val db = FirebaseFirestore.getInstance()

        db.collection("users_patient_email")
            .whereEqualTo("userId", patientUID)
            .get()
            .addOnSuccessListener { docs ->
                if (!docs.isEmpty) {
                    val age = docs.firstOrNull()?.getString("age")?.toInt()
                    return@addOnSuccessListener onResult(age)
                }

                db.collection("users_patient_phonenumber")
                    .whereEqualTo("userId", patientUID)
                    .get()
                    .addOnSuccessListener { phoneDocs ->
                        val age = phoneDocs.firstOrNull()?.getString("age")?.toInt()
                        onResult(age)
                    }
                    .addOnFailureListener { onResult(null) }
            }
            .addOnFailureListener { onResult(null) }
    }



    private fun fetchWeeklyAverages() {
        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Log.e(HomeFragment.TAG, "❌ UID dokter tidak ditemukan.")
                updateUI(0, 0.0, 0.0, 0)
                return@getActualDoctorUID
            }

            val db = FirebaseFirestore.getInstance()

            // Ambil pasien yang di-assign ke dokter ini
            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUID)
                .get()
                .addOnSuccessListener { assignedDocs ->
                    val assignedPatientUIDs = assignedDocs.mapNotNull { it.getString("patientId") }

                    if (assignedPatientUIDs.isEmpty()) {
                        Log.d(HomeFragment.TAG, "❌ Tidak ada pasien yang di-assign ke dokter ini.")
                        updateUI(0, 0.0, 0.0, 0)
                        return@addOnSuccessListener
                    }

                    db.collection("patient_heart_rate")
                        .whereIn("userId", assignedPatientUIDs)
                        .addSnapshotListener { documents, error ->
                            if (error != null) {
                                Log.e(HomeFragment.TAG, "❌ Error fetching health data", error)
                                return@addSnapshotListener
                            }

                            if (documents == null || documents.isEmpty) {
                                Log.d(HomeFragment.TAG, "📭 Tidak ada data heart rate minggu ini.")
                                updateUI(0, 0.0, 0.0, 0)
                                return@addSnapshotListener
                            }

                            val weekMap = LinkedHashMap<String, MutableList<HealthData>>()
                            val firstWeekRange = getFirstWeekOfCurrentMonth()
                            val currentMonthYear = getCurrentMonthYear()

                            val docsList = documents.toList()
                            val patientAgeMap = mutableMapOf<String, Int?>()

                            // Langkah 1: Ambil umur semua pasien terkait
                            var ageFetchCount = 0
                            for (doc in docsList) {
                                val userId = doc.getString("userId") ?: continue
                                if (userId !in patientAgeMap) {
                                    getPatientAgeByUID(userId) { age ->
                                        patientAgeMap[userId] = age
                                        ageFetchCount++

                                        // Lanjut hanya kalau semua umur sudah didapat
                                        if (ageFetchCount == docsList.size) {
                                            processWeeklyData(docsList, assignedPatientUIDs, patientAgeMap, weekMap, firstWeekRange, currentMonthYear)
                                        }
                                    }
                                } else {
                                    ageFetchCount++
                                    if (ageFetchCount == docsList.size) {
                                        processWeeklyData(docsList, assignedPatientUIDs, patientAgeMap, weekMap, firstWeekRange, currentMonthYear)
                                    }
                                }
                            }
                        }
                }
                .addOnFailureListener {
                    Log.e(HomeFragment.TAG, "❌ Gagal mengambil assigned_patient", it)
                    updateUI(0, 0.0, 0.0, 0)
                }
        }
    }

    // Fungsi untuk memproses dan mengelompokkan data mingguan
    private fun processWeeklyData(
        docsList: List<DocumentSnapshot>,
        assignedPatientUIDs: List<String>,
        patientAgeMap: Map<String, Int?>,
        weekMap: LinkedHashMap<String, MutableList<HealthData>>,
        firstWeekRange: Pair<String, String>,
        currentMonthYear: String
    ) {
        for (doc in docsList) {
            val userId = doc.getString("userId") ?: continue
            if (userId !in assignedPatientUIDs) continue

            val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
            if (heartRate == 0) continue

            val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
            val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
            val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
            val timestamp = doc.getString("timestamp") ?: continue

            val dataMonthYear = extractMonthYearFromTimestamp(timestamp)
            if (dataMonthYear != currentMonthYear) {
                Log.d(HomeFragment.TAG, "📌 Data diabaikan (bukan bulan berjalan): $timestamp")
                continue
            }

            val weekNumber = getWeekOfMonth(timestamp, firstWeekRange)
            val userAge = patientAgeMap[userId]

            val healthData = HealthData(
                heartRate = heartRate,
                bloodPressure = "$systolicBP/$diastolicBP",
                batteryLevel = batteryLevel,
                timestamp = timestamp,
                fullTimestamp = timestamp,
                userAge = userAge
            )

            weekMap.getOrPut(weekNumber) { mutableListOf() }.add(healthData)
        }

        val latestWeek = weekMap.keys.maxByOrNull { week ->
            extractStartEndDateFromWeek(week).first
        }

        val currentWeekData = latestWeek?.let { weekMap[it] }

        if (!currentWeekData.isNullOrEmpty()) {
            val avgHeartRate = currentWeekData.map { it.heartRate }.average().toInt()
            val avgSystolicBP = currentWeekData.map { it.bloodPressure.split("/")[0].toInt() }.average().toInt()
            val avgDiastolicBP = currentWeekData.map { it.bloodPressure.split("/")[1].toInt() }.average().toInt()
            val avgBatteryLevel = currentWeekData.map { it.batteryLevel }.average().toInt()

            Log.d(HomeFragment.TAG, "✅ Averages -> HR: $avgHeartRate, BP: $avgSystolicBP/$avgDiastolicBP, Battery: $avgBatteryLevel")
            updateUI(avgHeartRate, avgSystolicBP.toDouble(), avgDiastolicBP.toDouble(), avgBatteryLevel)
        } else {
            Log.d(HomeFragment.TAG, "⚠ Tidak ada data valid untuk minggu terbaru.")
            updateUI(0, 0.0, 0.0, 0)
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

    private fun getCurrentMonthYear(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
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

        val weekNumber = ((calendar.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1

        Log.d(HomeFragment.TAG, "📅 Tanggal: $timestamp -> Week: $weekNumber ($startDate - $endDate) di $currentMonth")

        return when {
            date.after(firstWeekStart) && date.before(firstWeekEnd) ->
                "Week 1 (${firstWeekRange.first} - ${firstWeekRange.second})"
            else ->
                "Week $weekNumber ($startDate - $endDate)"
        }
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
            Pair("01 Jan 1900", "01 Jan 1900")
        } catch (e: Exception) {
            Log.e("WeekParsing", "Error extracting start/end date from week: ${e.message}")
            Pair("01 Jan 1900", "01 Jan 1900")
        }
    }

    private fun updateUI(heartRate: Int, systolic: Double, diastolic: Double, battery: Int) {
        Log.d(HomeFragment.TAG, "Updating UI with -> HeartRate: $heartRate, BP: ${systolic.roundToInt()} / ${diastolic.roundToInt()}, Battery: $battery%")

        view?.findViewById<TextView>(R.id.avg_week_heartrate)?.text =
            if (heartRate > 0) "$heartRate" else " "

        view?.findViewById<TextView>(R.id.avg_week_bloodpressure)?.text =
            if (systolic > 0 && diastolic > 0) "${systolic.roundToInt()} / ${diastolic.roundToInt()}" else " "

        view?.findViewById<TextView>(R.id.avg_week_battery)?.text =
            if (battery > 0) "$battery %" else " "
    }
    private fun extractMonthYearFromTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestamp) ?: return "Unknown Month"

            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            Log.e(HomeFragment.TAG, "❌ Error extracting month-year from timestamp: ${e.message}")
            "Unknown Month"
        }
    }

    private fun fetchMonthlyAverages() {
        getActualDoctorUID { doctorUID ->
            if (doctorUID == null) {
                Log.e(HomeFragment.TAG, "❌ UID dokter tidak ditemukan.")
                return@getActualDoctorUID
            }

            val db = FirebaseFirestore.getInstance()

            // Step 1: Ambil pasien yang di-assign ke dokter ini
            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUID)
                .get()
                .addOnSuccessListener { assignedDocs ->
                    val assignedPatientUIDs = assignedDocs.mapNotNull { it.getString("patientId") }

                    if (assignedPatientUIDs.isEmpty()) {
                        Log.d(HomeFragment.TAG, "❌ Tidak ada pasien yang di-assign ke dokter ini.")
                        return@addOnSuccessListener
                    }

                    // Step 2: Ambil data heart rate pasien-pasien tersebut
                    db.collection("patient_heart_rate")
                        .whereIn("userId", assignedPatientUIDs)
                        .addSnapshotListener { documents, error ->
                            if (error != null) {
                                Log.e(HomeFragment.TAG, "❌ Error fetching monthly data: ", error)
                                return@addSnapshotListener
                            }

                            val monthMap = mutableMapOf<String, MutableList<Int>>()

                            val now = Calendar.getInstance()
                            val fourMonthsAgo = Calendar.getInstance().apply {
                                add(Calendar.MONTH, -3)
                                set(Calendar.DAY_OF_MONTH, 1)
                                set(Calendar.HOUR_OF_DAY, 0)
                                set(Calendar.MINUTE, 0)
                                set(Calendar.SECOND, 0)
                                set(Calendar.MILLISECOND, 0)
                            }

                            if (documents != null) {
                                for (doc in documents) {
                                    val userId = doc.getString("userId") ?: continue
                                    if (userId !in assignedPatientUIDs) continue

                                    val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                                    if (heartRate == 0) continue

                                    val timestamp = doc.getString("timestamp") ?: continue
                                    val timestampMillis = timestampToMillis(timestamp) ?: continue

                                    val dataCalendar = Calendar.getInstance().apply {
                                        timeInMillis = timestampMillis
                                    }

                                    if (dataCalendar.timeInMillis in fourMonthsAgo.timeInMillis..now.timeInMillis) {
                                        val monthName = getMonthNameByIndex(dataCalendar.get(Calendar.MONTH))
                                        monthMap.getOrPut(monthName) { mutableListOf() }.add(heartRate)
                                    }
                                }
                            }

                            // Hitung rata-rata heart rate tiap bulan
                            val monthAverages = monthMap.mapValues { (_, values) -> values.average().toInt() }

                            // Dapatkan urutan 4 bulan terakhir
                            val currentMonthIndex = now.get(Calendar.MONTH)
                            val last4Months = (0..3).map {
                                val monthIndex = (currentMonthIndex - it + 12) % 12
                                getMonthNameByIndex(monthIndex)
                            }.reversed()

                            // Pastikan urutan lengkap
                            val completeData = last4Months.associateWith { monthAverages[it] }

                            Log.d(HomeFragment.TAG, "✅ Filtered Monthly Averages (Last 4 months): $completeData")
                            monthChartView?.setData(completeData)
                        }
                }
                .addOnFailureListener {
                    Log.e(HomeFragment.TAG, "❌ Gagal mengambil assigned_patient", it)
                }
        }
    }


    private fun getMonthNameByIndex(index: Int): String {
        return listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[index]
    }

    private fun timestampToMillis(timestamp: String): Long? {
        return try {
            val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            formatter.parse(timestamp)?.time
        } catch (e: Exception) {
            null
        }
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding?.let { binding ->
            val recyclerView = binding.viewPager.getChildAt(0) as? ViewGroup
            val currentView = recyclerView?.getChildAt(binding.viewPager.currentItem)
            currentView?.viewTreeObserver?.removeOnGlobalLayoutListener(globalLayoutListener)
            pageChangeCallback?.let { binding.viewPager.unregisterOnPageChangeCallback(it) }
        }

        globalLayoutListener = null
        pageChangeCallback = null
        _binding = null
    }

}
