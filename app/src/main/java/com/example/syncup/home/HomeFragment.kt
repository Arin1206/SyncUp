package com.example.syncup.home

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.syncup.R
import com.example.syncup.ble.BluetoothLeService
import com.example.syncup.viewmodel.HeartRateViewModel
import com.example.syncup.welcome.WelcomeActivity
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

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
    private var currentLon: Double? = null


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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)
        heartRateTextView = view.findViewById(R.id.heart_rate_value)

        progressBar = view.findViewById(R.id.progress_loading)

        database = FirebaseDatabase.getInstance().reference.child("connected_device")
        heartRateDatabase = FirebaseDatabase.getInstance().reference.child("heart_rate")

        // **Pencegahan Force Close: Tambahkan Listener dengan Cek isAdded**
        deviceEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && isAdded) {  // Cek apakah fragment masih aktif
                    deviceName = snapshot.child("deviceName").getValue(String::class.java) ?: "No Device"
                    deviceAddress = snapshot.child("deviceAddress").getValue(String::class.java) ?: "Unknown Address"

                    activity?.runOnUiThread {
                        view?.findViewById<TextView>(R.id.device_name)?.text =
                            "$deviceName ($deviceAddress)"
                    }

                    if (!deviceAddress.isNullOrEmpty() && deviceAddress != "Unknown Address") {
                        val intent = Intent(requireContext(), BluetoothLeService::class.java)
                        requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

                        if (!isReceiverRegistered) {
                            requireContext().registerReceiver(heartRateReceiver, makeGattUpdateIntentFilter())
                            isReceiverRegistered = true
                            Log.d(TAG, "Receiver registered")
                        }
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
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        heartRateViewModel = ViewModelProvider(requireActivity()).get(HeartRateViewModel::class.java)
        heartRateViewModel.heartRate.observe(viewLifecycleOwner) { rate ->
            heartRateTextView?.text = "$rate bpm"
        }

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

        checkLocationPermissionAndUpdateMaps()
    }



    private val deviceDisconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothLeService.ACTION_DEVICE_DISCONNECTED) {
                if (!isDeviceDisconnected) {
                    isDeviceDisconnected = true
                    Log.i(TAG, "Device disconnected, hiding UI elements...")
                    activity?.runOnUiThread {
                        progressBar?.visibility = View.VISIBLE // Tampilkan progress bar
                        Log.d(TAG, "ProgressBar set to VISIBLE")
                        Handler().postDelayed({
                            progressBar?.visibility = View.GONE // Sembunyikan progress bar setelah 2 detik
                            view?.findViewById<TextView>(R.id.device_name)?.visibility = View.GONE
                            view?.findViewById<TextView>(R.id.heart_rate_value)?.visibility = View.GONE
                        }, 2000)
                    }
                } else {
                    Log.d(TAG, "Disconnect already handled.")
                }
            }
        }
    }

    private val deviceReconnectReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothLeService.ACTION_GATT_CONNECTED) {
                isDeviceDisconnected = false
                Log.i(TAG, "Device reconnected, showing UI elements...")
                activity?.runOnUiThread {
                    view?.findViewById<TextView>(R.id.device_name)?.visibility = View.VISIBLE
                    view?.findViewById<TextView>(R.id.heart_rate_value)?.visibility = View.VISIBLE
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()

        progressBar?.visibility = View.VISIBLE // 🔹 Tampilkan ProgressBar saat mulai pengecekan
        Log.d(TAG, "ProgressBar set to VISIBLE in onResume")

        // 🔹 Beri waktu 2 detik untuk BLE menentukan status koneksi sebelum menghilangkan ProgressBar
        Handler().postDelayed({
            if (bluetoothLeService != null && bluetoothLeService!!.isConnected()) {
                Log.d(TAG, "Device is connected, showing UI elements.")
                progressBar?.visibility = View.GONE
                view?.findViewById<TextView>(R.id.device_name)?.visibility = View.VISIBLE
                view?.findViewById<TextView>(R.id.heart_rate_value)?.visibility = View.VISIBLE
            } else {
                Log.d(TAG, "Device is disconnected, hiding UI elements.")
                if (progressBar?.visibility != View.VISIBLE) {
                    progressBar?.visibility = View.VISIBLE
                    Log.d(TAG, "ProgressBar set to VISIBLE")
                    // Lanjutkan dengan Handler.postDelayed untuk menyembunyikannya
                    Handler().postDelayed({
                        progressBar?.visibility = View.GONE
                        view?.findViewById<TextView>(R.id.device_name)?.visibility = View.GONE
                        view?.findViewById<TextView>(R.id.heart_rate_value)?.visibility = View.GONE
                    }, 2000)
                }

            }
        }, 2000) // Delay 2 detik untuk memberi waktu BLE menentukan status

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

        requireContext().unregisterReceiver(deviceDisconnectReceiver)
        requireContext().unregisterReceiver(deviceReconnectReceiver)
        requireContext().unregisterReceiver(locationModeReceiver)
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
