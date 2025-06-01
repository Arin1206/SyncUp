package com.example.syncup.home

import HealthData
import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.location.Geocoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.example.syncup.adapter.DoctorAdapter
import com.example.syncup.adapter.DotIndicatorAdapter
import com.example.syncup.adapter.NewsAdapter
import com.example.syncup.ble.BluetoothLeService
import com.example.syncup.data.BloodPressureRepository
import com.example.syncup.data.HomeViewModel
import com.example.syncup.databinding.FragmentHomeBinding
import com.example.syncup.databinding.FragmentProfilePatientBinding
import com.example.syncup.model.Doctor
import com.example.syncup.model.News
import com.example.syncup.profile.ProfilePatientFragment
import com.example.syncup.search.SearchPatientFragment
import com.example.syncup.viewmodel.HeartRateViewModel
import com.example.syncup.welcome.WelcomeActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

class HomeFragment : Fragment() {

    private val LOCATION_PERMISSION_REQUEST_CODE = 1001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var deviceAddress: String? = null
    private var isDeviceDisconnected = false
    private var deviceName: String? = null
    private var bluetoothLeService: BluetoothLeService? = null
    private lateinit var heartRateViewModel: HeartRateViewModel
    private var isBound = false
    private var isReceiverRegistered = false
    private lateinit var database: DatabaseReference
    private lateinit var heartRateDatabase: DatabaseReference
    private var deviceEventListener: ValueEventListener? = null
    private var heartRateEventListener: ValueEventListener? = null
    private var progressBar: ProgressBar? = null
    private var heartRateTextView: TextView? = null
    private var currentLat: Double? = null
    private lateinit var searchDoctor: EditText
    private lateinit var recyclerViewDoctors: RecyclerView
    private lateinit var doctorAdapter: DoctorAdapter
    private val doctorList = mutableListOf<Doctor>()
    private var monthChartView: com.example.syncup.chart.MonthChartViewHome? = null
    private var currentLon: Double? = null
    private lateinit var recyclerViewDots: RecyclerView
    private lateinit var dotIndicatorAdapter: DotIndicatorAdapter
    private var bpTextView: TextView? = null
    private lateinit var recyclerViewNews: RecyclerView
    private lateinit var newsAdapter: NewsAdapter
    private val newsList = mutableListOf<News>()
    private lateinit var homeViewModel: HomeViewModel
    private var heartRateChartView: com.example.syncup.chart.HeartRateChartViewHome? = null

    private val firestore = FirebaseFirestore.getInstance()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as BluetoothLeService.LocalBinder
            bluetoothLeService = binder.getService()
            isBound = true
            Log.d(TAG, "BluetoothLeService connected")

            if (bluetoothLeService?.initialize() == true) {
                bluetoothLeService?.connect(deviceAddress)
            } else {
                Log.e(TAG, "BluetoothLeService initialization failed")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bluetoothLeService = null
            isBound = false
            Log.d(TAG, "BluetoothLeService disconnected")
        }
    }

    private fun setupUI(view: View) {
        // Set listener pada parent layout untuk menangkap klik di luar input
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
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

    private fun setupNewsRecyclerView(view: View) {
        val recyclerViewNews = view.findViewById<RecyclerView>(R.id.recycler_view_news)
        recyclerViewDots = view.findViewById(R.id.recycler_view_dots)

        // Atur RecyclerView horizontal untuk berita
        val layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerViewNews.layoutManager = layoutManager

        // Tambahkan contoh berita
        val newsList = mutableListOf(
            News("Studi Temukan Kerusakan Jantung pada Penyintas Covid-19", R.drawable.sample_news_image,0),
            News("Gaya Hidup Sehat untuk Mencegah Penyakit Jantung", R.drawable.sample_news_image,1),
            News("Inovasi Teknologi dalam Deteksi Penyakit Jantung", R.drawable.sample_news_image,2)
        )

        val newsAdapter = NewsAdapter(newsList, requireActivity())
        recyclerViewNews.adapter = newsAdapter

        // Inisialisasi RecyclerView untuk dot indicator
        dotIndicatorAdapter = DotIndicatorAdapter(newsList.size, 0)
        recyclerViewDots.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recyclerViewDots.adapter = dotIndicatorAdapter

        // Tambahkan listener untuk mengganti dot indikator saat user scroll
        recyclerViewNews.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val currentPosition = layoutManager.findFirstVisibleItemPosition()
                dotIndicatorAdapter.updateSelectedIndex(currentPosition)
            }
        })
    }


    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun fetchDoctorsFromFirestore() {
        val db = FirebaseFirestore.getInstance()
        val doctorList = mutableListOf<Doctor>()

        // Ambil data dari users_doctor_email
        db.collection("users_doctor_email")
            .get()
            .addOnSuccessListener { emailDocuments ->
                for (document in emailDocuments) {
                    val fullName = document.getString("fullName") ?: "Unknown"
                    doctorList.add(Doctor(fullName, "")) // Gambar kosong karena tidak ada di koleksi ini
                }

                // Ambil data dari users_doctor_phonenumber
                db.collection("users_doctor_phonenumber")
                    .get()
                    .addOnSuccessListener { phoneDocuments ->
                        for (document in phoneDocuments) {
                            val fullName = document.getString("fullName") ?: "Unknown"
                            doctorList.add(Doctor(fullName, "")) // Gambar kosong karena tidak ada di koleksi ini
                        }

                        // Perbarui adapter setelah kedua koleksi diambil
                        doctorAdapter = DoctorAdapter(doctorList)
                        recyclerViewDoctors.adapter = doctorAdapter
                        doctorAdapter.notifyDataSetChanged()
                    }
                    .addOnFailureListener { exception ->
                        Log.e("Firestore", "Error fetching phone doctors: ", exception)
                    }
            }
            .addOnFailureListener { exception ->
                Log.e("Firestore", "Error fetching email doctors: ", exception)
            }
    }


    @SuppressLint("MissingInflatedId")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {


        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val view = binding.root
            progressBar = view.findViewById(R.id.progress_loading)

        searchDoctor = view.findViewById(R.id.search_doctor)
        heartRateTextView = view.findViewById(R.id.heart_rate_value)


        recyclerViewDoctors = view.findViewById(R.id.recycler_view_doctors)

        // Gunakan GridLayoutManager dengan spanCount 3
        val layoutManager = GridLayoutManager(requireContext(), 3)
        recyclerViewDoctors.layoutManager = layoutManager


        doctorAdapter = DoctorAdapter(doctorList)
        recyclerViewDoctors.adapter = doctorAdapter

        fetchDoctorsFromFirestore()

        searchDoctor.setOnEditorActionListener { _, _, _ ->
            val searchText = searchDoctor.text.toString().trim()
            if (searchText.isNotEmpty()) {
                navigateToSearchFragment(searchText)
            }
            true
        }



        // Panggil fungsi untuk menutup keyboard saat klik di luar EditText
        setupUI(view)
        getActualPatientUid { patientUid ->
            if (patientUid == null) {
                Log.e(TAG, "Gagal mendapatkan patient UID")
                return@getActualPatientUid
            }

            database = FirebaseDatabase.getInstance().reference
                .child("connected_device")
                .child(patientUid)

            heartRateDatabase = FirebaseDatabase.getInstance().reference
                .child("heart_rate")
                .child(patientUid)
                .child("latest")

            deviceEventListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists() && isAdded) {
                        // Ambil nilai dari Firebase
                        deviceName = snapshot.child("deviceName").getValue(String::class.java)
                        deviceAddress = snapshot.child("deviceAddress").getValue(String::class.java)

                        activity?.runOnUiThread {
                            // Update device name: jika deviceName tersedia, tampilkan; jika tidak, tampilkan default
                            view?.findViewById<TextView>(R.id.device_name)?.text =
                                if (!deviceName.isNullOrEmpty()) deviceName else "Start Connected"

                            // Jika deviceAddress tidak valid, set nilai-nilai lainnya ke "null"
                            if (deviceAddress.isNullOrEmpty() || deviceAddress == "null") {
                                view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "null"
                                view?.findViewById<TextView>(R.id.bp_value)?.text = "null"
                                view?.findViewById<TextView>(R.id.indicator_value)?.text = "null"
                                view?.findViewById<TextView>(R.id.battery_value)?.text = "null"
                            }
                        }

                        // Jika deviceAddress valid, bind service dan register receiver
                        if (!deviceAddress.isNullOrEmpty() && deviceAddress != "null") {
                            val intent = Intent(requireContext(), BluetoothLeService::class.java)
                            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

                            if (!isReceiverRegistered) {
                                safeRegisterReceiver(heartRateReceiver, makeGattUpdateIntentFilter())
                                isReceiverRegistered = true
                                Log.d(TAG, "Receiver registered")
                            }
                        }
                    } else {
                        activity?.runOnUiThread {
                            view?.findViewById<TextView>(R.id.device_name)?.text = "Start Connected"
                            view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "null"
                            view?.findViewById<TextView>(R.id.bp_value)?.text = "null"
                            view?.findViewById<TextView>(R.id.indicator_value)?.text = "null"
                            view?.findViewById<TextView>(R.id.battery_value)?.text = "null"
                        }
                    }


                }



                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Failed to read device data from Firebase: ${error.message}")
                }
            }



            database.addValueEventListener(deviceEventListener!!)

            // **Listen for Heart Rate Updates from Firebase**
            // Tombol Logout
//        view.findViewById<TextView>(R.id.logoutbutton)?.setOnClickListener {
//            logoutUser()
//        }
        }


        return view
    }

    private fun navigateToSearchFragment(query: String) {
        val bundle = Bundle().apply {
            putString("searchQuery", query) // Kirim keyword ke fragment tujuan
        }
        val fragment = SearchPatientFragment()
        fragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, fragment) // Pastikan ID sesuai dengan container yang digunakan
            .addToBackStack(null) // Tambahkan ke backstack agar bisa kembali ke Home
            .commit()
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

    private fun stopLiveLocationUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
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

    private val locationModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == android.location.LocationManager.PROVIDERS_CHANGED_ACTION) {
                val locationManager = requireContext().getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
                val isGpsEnabled = locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)
                val isNetworkEnabled = locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)

                if (!isGpsEnabled && !isNetworkEnabled) {
                    val mapsTextView = view?.findViewById<TextView>(R.id.maps)
                    mapsTextView?.text = "Location not available"
                    openLocationSettings()
                }
            }
        }
    }



    private fun openLocationSettings() {
        val intent = Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        startActivity(intent)
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                checkLocationPermissionAndUpdateMaps() // Izin sudah diberikan, update lokasi
            } else {
                Log.e(TAG, "Location permission denied.")
                view?.findViewById<TextView>(R.id.maps)?.text = "Permission denied"
            }
        }
    }
    private fun updateIndicator(heartRate: Int, userAge: Int?) {
        val indicatorTextView = view?.findViewById<TextView>(R.id.indicator_value)
        val indicatorBox = view?.findViewById<View>(R.id.indicator_box)

        if (heartRate == -1 || userAge == null) {
            // Jika nilai heart rate tidak valid atau usia belum tersedia, kondisi default
            indicatorTextView?.text = "null"
            indicatorBox?.setBackgroundResource(R.drawable.bg_purple_box)
            return
        }

        val maxWarning = 220 - userAge
        val minWarning = (maxWarning * 0.8).toInt()

        when {
            heartRate >= maxWarning -> {
                // Danger
                indicatorTextView?.text = "anger"
                indicatorBox?.setBackgroundResource(R.drawable.bg_red_box)
            }
            heartRate < minWarning -> {
                // Healthy
                indicatorTextView?.text = "health"
                indicatorBox?.setBackgroundResource(R.drawable.bg_green_box)
            }
            else -> {
                // Warning
                indicatorTextView?.text = "warning"
                indicatorBox?.setBackgroundResource(R.drawable.bg_yellow_box) // Buat warna kuning kalau ada
            }
        }
    }


    private fun getCurrentMonthYear(): String {
        val calendar = Calendar.getInstance()
        return SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(calendar.time)
    }

    private fun getUserAge(onResult: (Int?) -> Unit) {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            onResult(null)
            return
        }

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        if (email != null) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    val age = documents.firstOrNull()?.getString("age")?.toInt()
                    onResult(age)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else if (phoneNumber != null) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    val age = documents.firstOrNull()?.getLong("age")?.toInt()
                    onResult(age)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else {
            onResult(null)
        }
    }

    private fun fetchWeeklyAverages() {
        getUserAge { age ->
            if (age == null) {
                Log.e(TAG, "❌ Umur tidak ditemukan.")
            } else {
                Log.d(TAG, "📌 Umur pengguna: $age")
            }

            getActualPatientUid { userId ->
                if (userId == null) {
                    Log.e(TAG, "❌ UID pasien tidak ditemukan.")
                    updateUI(0, 0.0, 0.0, 0)
                    return@getActualPatientUid
                }

                val db = FirebaseFirestore.getInstance()

                db.collection("patient_heart_rate")
                    .whereEqualTo("userId", userId)
                    .addSnapshotListener { documents, error ->
                        if (error != null) {
                            Log.e(TAG, "Error fetching health data", error)
                            return@addSnapshotListener
                        }

                        if (documents == null || documents.isEmpty) {
                            Log.d(TAG, "No data found for this week.")
                            updateUI(0, 0.0, 0.0, 0)
                            return@addSnapshotListener
                        }

                        val weekMap = LinkedHashMap<String, MutableList<HealthData>>()
                        val firstWeekRange = getFirstWeekOfCurrentMonth()
                        val currentMonthYear = getCurrentMonthYear()

                        for (doc in documents) {
                            val heartRate = doc.getLong("heartRate")?.toInt() ?: continue
                            if (heartRate == 0) continue

                            val systolicBP = doc.getDouble("systolicBP")?.toInt() ?: 0
                            val diastolicBP = doc.getDouble("diastolicBP")?.toInt() ?: 0
                            val batteryLevel = doc.getLong("batteryLevel")?.toInt() ?: 0
                            val timestamp = doc.getString("timestamp") ?: continue

                            val dataMonthYear = extractMonthYearFromTimestamp(timestamp)
                            if (dataMonthYear != currentMonthYear) {
                                Log.d(TAG, "📌 Data diabaikan (bukan bulan berjalan): $timestamp")
                                continue
                            }

                            val weekNumber = getWeekOfMonth(timestamp, firstWeekRange)

                            Log.d(TAG, "✔ Data Masuk: HR=$heartRate, BP=$systolicBP/$diastolicBP, Battery=$batteryLevel, Week=$weekNumber")

                            val healthData = HealthData(
                                heartRate = heartRate,
                                bloodPressure = "$systolicBP/$diastolicBP",
                                batteryLevel = batteryLevel,
                                timestamp = timestamp,
                                fullTimestamp = timestamp,
                                userAge = age// ← umur user dimasukkan di sini
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

                            Log.d(TAG, "✅ Latest Week Averages -> HeartRate: $avgHeartRate, BP: $avgSystolicBP/$avgDiastolicBP, Battery: $avgBatteryLevel")
                            updateUI(avgHeartRate, avgSystolicBP.toDouble(), avgDiastolicBP.toDouble(), avgBatteryLevel)
                        } else {
                            Log.d(TAG, "No valid data found for the latest week.")
                            updateUI(0, 0.0, 0.0, 0)
                        }
                    }
            }
        }
    }

    private fun extractMonthYearFromTimestamp(timestamp: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val date = inputFormat.parse(timestamp) ?: return "Unknown Month"

            SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error extracting month-year from timestamp: ${e.message}")
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

        val weekNumber = ((calendar.get(Calendar.DAY_OF_MONTH) - 1) / 7) + 1

        Log.d(TAG, "📅 Tanggal: $timestamp -> Week: $weekNumber ($startDate - $endDate) di $currentMonth")

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
            Pair("01 Jan 1900", "01 Jan 1900")
        } catch (e: Exception) {
            Log.e("WeekParsing", "Error extracting start/end date from week: ${e.message}")
            Pair("01 Jan 1900", "01 Jan 1900")
        }
    }

    private fun getWeekOfMonth(timestamp: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp) ?: return "Unknown Week"
        val calendar = Calendar.getInstance().apply { time = date }

        val weekOfMonth = calendar.get(Calendar.WEEK_OF_MONTH)
        val monthYear = SimpleDateFormat("MMMM yyyy", Locale.getDefault()).format(date)

        // Tentukan awal dan akhir minggu
        calendar.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        val startDate = SimpleDateFormat("dd MMM", Locale.getDefault()).format(calendar.time)

        calendar.add(Calendar.DAY_OF_WEEK, 6)
        val endDate = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(calendar.time)

        return "Week $weekOfMonth ($startDate - $endDate)"
    }


    private fun updateUI(heartRate: Int, systolic: Double, diastolic: Double, battery: Int) {
        Log.d(TAG, "Updating UI with -> HeartRate: $heartRate, BP: ${systolic.roundToInt()} / ${diastolic.roundToInt()}, Battery: $battery%")

        view?.findViewById<TextView>(R.id.avg_week_heartrate)?.text =
            if (heartRate > 0) "$heartRate" else " "

        view?.findViewById<TextView>(R.id.avg_week_bloodpressure)?.text =
            if (systolic > 0 && diastolic > 0) "${systolic.roundToInt()} / ${diastolic.roundToInt()}" else " "

        view?.findViewById<TextView>(R.id.avg_week_battery)?.text =
            if (battery > 0) "$battery %" else " "
    }

    private fun getMonthName(timestamp: String): String {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp) ?: return "Unknown"
        return SimpleDateFormat("MMM", Locale.getDefault()).format(date)
    }


    private fun getActualPatientUid(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_patient_email")
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
            firestore.collection("users_patient_phonenumber")
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

    private fun fetchMonthlyAverages() {
        getActualPatientUid { userId ->
            if (userId == null) {
                Log.e(TAG, "❌ UID pasien tidak ditemukan.")
                return@getActualPatientUid
            }

            val db = FirebaseFirestore.getInstance()

            db.collection("patient_heart_rate")
                .whereEqualTo("userId", userId)
                .addSnapshotListener { documents, error ->
                    if (error != null) {
                        Log.e(TAG, "Error fetching monthly data: ", error)
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

                    val monthAverages = monthMap.mapValues { (_, values) -> values.average().toInt() }

                    val currentMonthIndex = now.get(Calendar.MONTH)
                    val last4Months = (0..3).map {
                        val monthIndex = (currentMonthIndex - it + 12) % 12
                        getMonthNameByIndex(monthIndex)
                    }.reversed()

                    val completeData = last4Months.associateWith { monthAverages[it] }

                    Log.d(TAG, "✅ Filtered Monthly Averages (Last 4 months): $completeData")
                    monthChartView?.setData(completeData)
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


    private fun getMonthIndex(timestamp: String): Int {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(timestamp) ?: return -1
        val calendar = Calendar.getInstance()
        calendar.time = date
        return calendar.get(Calendar.MONTH) // 0 = Januari, 11 = Desember
    }

    private fun navigateToPatientFragment() {
        val fragment = ProfilePatientFragment()
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, fragment) // Sesuaikan dengan container di layout
            .addToBackStack(null) // Tambahkan ke backstack agar bisa kembali ke Home
            .commit()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel = ViewModelProvider(requireActivity()).get(HomeViewModel::class.java)

        monthChartView = view.findViewById(R.id.monthHeartRateChart)
        fetchMonthlyAverages()

        val profileImage = view.findViewById<ImageView>(R.id.profile)
        profileImage.setOnClickListener {
            navigateToPatientFragment()
        }
        setupNewsRecyclerView(view)

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (!userId.isNullOrEmpty()) {
            loadActualPatientProfilePicture()
        }
        fetchWeeklyAverages()
        bpTextView = view.findViewById(R.id.bp_value)

        // **Gunakan ViewModel untuk menyimpan BP saat pindah fragment**
        homeViewModel.bloodPressure.observe(viewLifecycleOwner) { bp ->
            bpTextView?.text = when {
                bp.systolic == -1 && bp.diastolic == -1 -> "Process"
                bp.systolic > 0 && bp.diastolic > 0 -> "${bp.systolic} / ${bp.diastolic}"
                else -> "No Data"
            }
        }

        // **Pastikan LiveData dari Repository diobservasi**
        BloodPressureRepository.bloodPressureLiveData.observe(viewLifecycleOwner) { bp ->
            if (bp != null) {
                homeViewModel.setBloodPressure(bp)
            }
        }


        heartRateViewModel = ViewModelProvider(requireActivity()).get(HeartRateViewModel::class.java)
        heartRateViewModel.heartRate.observe(viewLifecycleOwner) { rate ->
            heartRateTextView?.text = "$rate bpm"
        }

        heartRateChartView = view.findViewById(R.id.heartRateChart)
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
                    Log.e(TAG, "Google Maps failed, trying web URL.")

                    // Jika gagal, buka dengan URL Maps
                    val webIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://www.google.com/maps/search/?api=1&query=$currentLat,$currentLon")
                    )
                    startActivity(webIntent)
                }
            } else {
                Log.e(TAG, "Coordinates are null. Cannot open maps.")
            }
        }


        val dayTextView = view.findViewById<TextView>(R.id.day)
        val currentDate = Calendar.getInstance().time
        val dateFormat = SimpleDateFormat("dd-MMM-yyyy", Locale.getDefault())
        val formattedDate = dateFormat.format(currentDate)

        dayTextView.text = "This Day, $formattedDate"

        checkLocationPermissionAndUpdateMaps()
    }



    private var hasHandledDisconnect = false // 🔹 Tambahkan flag untuk menangani disconnect sekali saja

    private val deviceDisconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothLeService.ACTION_DEVICE_DISCONNECTED) {
                if (!isDeviceDisconnected) {
                    isDeviceDisconnected = true
                    hasHandledDisconnect = true
                    Log.i(TAG, "Device disconnected, stopping BP polling...")

                    // **Stop polling BP saat device disconnected**
                    BloodPressureRepository.stopPolling()

                    activity?.runOnUiThread {
                        progressBar?.visibility = View.VISIBLE

                        view?.findViewById<TextView>(R.id.bp_value)?.apply {
                            text = "null"
                            visibility = View.VISIBLE
                        }

                        view?.findViewById<TextView>(R.id.device_name)?.text = "Start Connected"
                        view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "null"
                        view?.findViewById<TextView>(R.id.indicator_value)?.text = "null"
                        view?.findViewById<TextView>(R.id.battery_value)?.text = "null"

                        // **Hapus semua data di grafik saat perangkat terputus**
                        heartRateChartView?.clearChart()

                        Handler().postDelayed({
                            progressBar?.visibility = View.GONE
                        }, 3000)
                    }
                }
            }
        }
    }



    private val deviceReconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothLeService.ACTION_GATT_CONNECTED) {
                isDeviceDisconnected = false
                hasHandledDisconnect = false
                Log.i(TAG, "Device reconnected, waiting 5 minutes before showing BP...")

                activity?.runOnUiThread {
                    view?.findViewById<TextView>(R.id.device_name)?.apply {
                        text = if (!deviceName.isNullOrEmpty()) deviceName else "Connected"
                        visibility = View.VISIBLE
                    }

                    view?.findViewById<TextView>(R.id.bp_value)?.apply {
                        text = "Waiting..."
                        visibility = View.VISIBLE
                    }

                    // **Gunakan delayedStartPolling() untuk menunda polling BP**
                    BloodPressureRepository.delayedStartPolling()

                    Log.d(TAG, "Device reconnected. BP polling starts after delay.")
                }
            }
        }
    }





    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothLeService.ACTION_BATTERY_LEVEL_MEASUREMENT) {
                val battery = intent.getIntExtra(BluetoothLeService.EXTRA_BATTERY_LEVEL, -1)
                Log.d(TAG, "🔋 Battery level received in HomeFragment: $battery%")

                activity?.runOnUiThread {
                    view?.findViewById<TextView>(R.id.battery_value)?.text = "$battery%"
                }
            }
        }
    }




    override fun onResume() {
        super.onResume()

        val batteryFilter = IntentFilter(BluetoothLeService.ACTION_BATTERY_LEVEL_MEASUREMENT)
        safeRegisterReceiver(batteryReceiver, batteryFilter)
        Log.d(TAG, "🔄 Battery receiver registered")

        if (!hasHandledDisconnect) {
            progressBar?.visibility = View.VISIBLE
            Log.d(TAG, "ProgressBar set to VISIBLE in onResume")

            Handler().postDelayed({
                if (bluetoothLeService != null && bluetoothLeService!!.isConnected()) {
                    Log.d(TAG, "Device is connected, showing UI elements.")
                    progressBar?.visibility = View.GONE
                    view?.findViewById<TextView>(R.id.device_name)?.text = deviceName ?: "Start Connected"
                    view?.findViewById<TextView>(R.id.heart_rate_value)?.visibility = View.VISIBLE
                    view?.findViewById<TextView>(R.id.bp_value)?.visibility = View.VISIBLE
                    view?.findViewById<TextView>(R.id.indicator_value)?.visibility = View.VISIBLE
                    view?.findViewById<TextView>(R.id.battery_value)?.visibility = View.VISIBLE

                    // **Pastikan polling BP dimulai ulang jika perlu**
                    if (!BloodPressureRepository.bloodPressureLiveData.hasActiveObservers()) {
                        Log.d(TAG, "🔄 Restarting BP polling...")
                        BloodPressureRepository.startPolling()
                    }

                } else {
                    Log.d(TAG, "Device is disconnected, setting values to 'null'.")
                    progressBar?.visibility = View.GONE
                    view?.findViewById<TextView>(R.id.device_name)?.text = "Start Connected"
                    view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "null"
                    view?.findViewById<TextView>(R.id.bp_value)?.text = "null"
                    view?.findViewById<TextView>(R.id.indicator_value)?.text = "null"
                    view?.findViewById<TextView>(R.id.battery_value)?.text = "null"
                }
            }, 3000)
        }

        safeRegisterReceiver(locationModeReceiver, IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION))
        checkLocationPermissionAndUpdateMaps()

        val filter = IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_DEVICE_DISCONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        }
        safeRegisterReceiver(deviceDisconnectReceiver, filter)
        safeRegisterReceiver(deviceReconnectReceiver, filter)
    }

    override fun onPause() {
        super.onPause()

        try {
            requireContext().unregisterReceiver(batteryReceiver)
            Log.d(TAG, "🔄 Battery receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "⚠ Battery receiver was not registered, skipping...")
        }

        try {
            requireContext().unregisterReceiver(deviceDisconnectReceiver)
            Log.d(TAG, "🔄 Device Disconnect receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "⚠ Device Disconnect receiver was not registered, skipping...")
        }

        try {
            requireContext().unregisterReceiver(deviceReconnectReceiver)
            Log.d(TAG, "🔄 Device Reconnect receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "⚠ Device Reconnect receiver was not registered, skipping...")
        }

        try {
            requireContext().unregisterReceiver(locationModeReceiver)
            Log.d(TAG, "🔄 Location Mode receiver unregistered")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "⚠ Location Mode receiver was not registered, skipping...")
        }

        stopLiveLocationUpdates()
    }



    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }

        if (isReceiverRegistered) {
            requireContext().unregisterReceiver(heartRateReceiver)
            isReceiverRegistered = false
            Log.d(TAG, "Receiver unregistered")
        }


    }

    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothLeService.ACTION_HEART_RATE_MEASUREMENT == intent.action) {
                val heartRate = intent.getIntExtra(BluetoothLeService.EXTRA_HEART_RATE, -1)
                Log.d(TAG, "Heart rate received: $heartRate")
                heartRateTextView?.text = "$heartRate bpm"

                heartRateChartView?.addHeartRate(heartRate)
                getUserAge { age ->
                    // Jalankan updateIndicator saat userAge sudah didapat
                    updateIndicator(heartRate, age)
                }

                getActualPatientUid { patientUid ->
                    if (patientUid != null) {
                        FirebaseDatabase.getInstance()
                            .reference
                            .child("heart_rate")
                            .child(patientUid)
                            .child("latest")
                            .setValue(heartRate)
                            .addOnSuccessListener {
                                Log.d(TAG, "Heart rate saved to patient UID: $patientUid")
                            }
                            .addOnFailureListener { e ->
                                Log.e(TAG, "Failed to save heart rate: ${e.message}")
                            }
                    } else {
                        Log.e(TAG, "Patient UID not found, cannot save heart rate.")
                    }
                }

            }
        }
    }


    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_HEART_RATE_MEASUREMENT)
        }
    }

    private fun safeRegisterReceiver(receiver: BroadcastReceiver, filter: IntentFilter) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requireContext().registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            requireContext().registerReceiver(receiver, filter)
        }
    }


    private fun logoutUser() {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user != null) {
            user.delete()
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Log.d("Logout", "User successfully deleted from Firebase Auth")
                    } else {
                        Log.e("Logout", "Failed to delete user: ${task.exception?.message}")
                    }
                }
        }

        auth.signOut()
        val intent = Intent(requireContext(), WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    private fun loadActualPatientProfilePicture() {
        getActualPatientUid { patientUserId ->
            if (patientUserId != null) {
                loadProfilePicture(patientUserId)
            } else {
                Log.e("ProfilePatient", "Failed to get actual patient UID")
                // Bisa juga kasih placeholder/default image kalau perlu
            }
        }
    }

    private fun loadProfilePicture(userId: String) {
        firestore.collection("patient_photoprofile").document(userId).get()
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


    companion object {
        const val TAG = "HomeFragment"
    }
}