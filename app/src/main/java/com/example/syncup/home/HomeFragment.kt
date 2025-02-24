package com.example.syncup.home

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
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
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.syncup.R
import com.example.syncup.ble.BluetoothLeService
import com.example.syncup.data.BloodPressureRepository
import com.example.syncup.data.HomeViewModel
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
    private var currentLon: Double? = null
    private var bpTextView: TextView? = null
    private lateinit var homeViewModel: HomeViewModel
    private var heartRateChartView: com.example.syncup.chart.HeartRateChartViewHome? = null



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

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        heartRateTextView = view.findViewById(R.id.heart_rate_value)

        progressBar = view.findViewById(R.id.progress_loading)

        searchDoctor = view.findViewById(R.id.search_doctor)

        searchDoctor.setOnEditorActionListener { _, _, _ ->
            val searchText = searchDoctor.text.toString().trim()
            if (searchText.isNotEmpty()) {
                navigateToSearchFragment(searchText)
            }
            true
        }


        // Panggil fungsi untuk menutup keyboard saat klik di luar EditText
        setupUI(view)
        database = FirebaseDatabase.getInstance().reference.child("connected_device")
        heartRateDatabase = FirebaseDatabase.getInstance().reference.child("heart_rate")

        // **Pencegahan Force Close: Tambahkan Listener dengan Cek isAdded**
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
                            requireContext().registerReceiver(heartRateReceiver, makeGattUpdateIntentFilter())
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
            interval = 5000           // Update setiap 5 detik
            fastestInterval = 3000    // Update tercepat 3 detik
            priority = com.google.android.gms.location.LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(locationResult: com.google.android.gms.location.LocationResult) {
                val location = locationResult.lastLocation
                if (location != null) {
                    currentLat = location.latitude
                    currentLon = location.longitude
                    mapsTextView?.text = "Lat: $currentLat, Long: $currentLon"
                } else {
                    mapsTextView?.text = "Location not available"
                }
            }
        }

        // 🔹 **Tambahkan pengecekan izin sebelum requestLocationUpdates()**
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
    private fun updateIndicator(heartRate: Int) {
        val indicatorTextView = view?.findViewById<TextView>(R.id.indicator_value)
        val indicatorBox = view?.findViewById<View>(R.id.indicator_box)

        if (heartRate == -1) {
            // Jika nilai heart rate tidak valid, kembalikan ke kondisi default
            indicatorTextView?.text = "null"
            indicatorBox?.setBackgroundResource(R.drawable.bg_purple_box)
        } else if (heartRate in 60..100) {
            // Heart rate sehat: set teks "health" dan background hijau
            indicatorTextView?.text = "health"
            indicatorBox?.setBackgroundResource(R.drawable.bg_green_box)
        } else {
            // Nilai di luar rentang sehat: set teks "anger" dan background merah
            indicatorTextView?.text = "anger"
            indicatorBox?.setBackgroundResource(R.drawable.bg_red_box)
        }
    }


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeViewModel = ViewModelProvider(requireActivity()).get(HomeViewModel::class.java)

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
            homeViewModel.setBloodPressure(bp)
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
                val gmmIntentUri = Uri.parse("geo:${currentLat},${currentLon}?q=${currentLat},${currentLon}")
                val mapIntent = Intent(Intent.ACTION_VIEW, gmmIntentUri)
                mapIntent.setPackage("com.google.android.apps.maps")

                if (mapIntent.resolveActivity(requireContext().packageManager) != null) {
                    startActivity(mapIntent)
                } else {
                    Log.e(TAG, "Google Maps app is not installed.")
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
        requireContext().registerReceiver(batteryReceiver, batteryFilter)
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

        requireContext().registerReceiver(locationModeReceiver, IntentFilter(android.location.LocationManager.PROVIDERS_CHANGED_ACTION))
        checkLocationPermissionAndUpdateMaps()

        val filter = IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_DEVICE_DISCONNECTED)
            addAction(BluetoothLeService.ACTION_GATT_CONNECTED)
        }
        requireContext().registerReceiver(deviceDisconnectReceiver, filter)
        requireContext().registerReceiver(deviceReconnectReceiver, filter)
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

                updateIndicator(heartRate)

                FirebaseDatabase.getInstance()
                    .reference
                    .child("heart_rate")
                    .child("latest")
                    .setValue(heartRate)
            }
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_HEART_RATE_MEASUREMENT)
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

    companion object {
        private const val TAG = "HomeFragment"
    }
}