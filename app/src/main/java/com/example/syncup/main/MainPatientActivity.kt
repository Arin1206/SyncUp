package com.example.syncup.main

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.ble.BluetoothLeService
import com.example.syncup.chat.ChatFragment
import com.example.syncup.databinding.ActivityMainPatientBinding
import com.example.syncup.faq.FaqFragment
import com.example.syncup.history.HistoryPatientFragment
import com.example.syncup.home.HomeFragment
import com.example.syncup.profile.ProfilePatientFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore

class MainPatientActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityMainPatientBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val scanInterval: Long = 15000 // Interval scanning 15 detik
    val scanResults = mutableListOf<Map<String, String>>()
    private lateinit var listAdapter: SimpleAdapter
    private val deviceAddresses = mutableSetOf<String>()
    private lateinit var database: DatabaseReference

    private val receiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (BluetoothDevice.ACTION_FOUND == action) {
                val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                val deviceName = device?.name ?: "Unknown Device"
                val deviceAddress = device?.address

                if (deviceAddress != null && !deviceAddresses.contains(deviceAddress)) {
                    deviceAddresses.add(deviceAddress)
                    updateDeviceList(deviceName, deviceAddress)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainPatientBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val rootView = window.decorView.findViewById<ViewGroup>(android.R.id.content)
        rootView.setBackgroundColor(ContextCompat.getColor(this, R.color.purple_dark))


        val serviceIntent = Intent(this, BluetoothLeService::class.java)
        startService(serviceIntent)
        // Initialize Firebase and other services
        database = FirebaseDatabase.getInstance().reference.child("connected_device")
        auth = FirebaseAuth.getInstance()
        window.navigationBarColor = getColor(R.color.black)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = getColor(android.R.color.transparent)  // Status bar transparan

        // Check for Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and above - Request Bluetooth permissions first
            if (checkBluetoothPermissions()) {
                // If permissions already granted, initialize HomeFragment
                initHomeFragment()
            } else {
                requestBluetoothPermissions()
            }
        } else {
            // For Android 10 (API 30) and below, go directly to HomeFragment
            initHomeFragment()
        }

        binding.scanButtonContainer.setOnClickListener {
            checkUserAgeBeforeScan { isAgeValid ->
                if (isAgeValid) {
                    scanBt()
                } else {
                    showAgeAlertDialog()
                }
            }
        }


        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.homepage -> replaceFragment(HomeFragment())
                R.id.history -> replaceFragment(HistoryPatientFragment())
                R.id.faq -> replaceFragment(FaqFragment())
                R.id.chat -> replaceFragment(ChatFragment())
                else -> {}
            }
            true
        }
    }

    private fun checkUserAgeBeforeScan(onResult: (Boolean) -> Unit) {
        getActualPatientUid { userId ->
            if (userId == null) {
                onResult(false)  // If no userId, return false
                return@getActualPatientUid
            }

            val firestore = FirebaseFirestore.getInstance()

            // Querying the users_patient_email or users_patient_phonenumber collection by userId
            firestore.collection("users_patient_email")
                .whereEqualTo("userId", userId)
                .get()
                .addOnSuccessListener { documents ->
                    // Check if the user is found in users_patient_email
                    if (documents.isEmpty) {
                        // If not found, check users_patient_phonenumber
                        firestore.collection("users_patient_phonenumber")
                            .whereEqualTo("userId", userId)
                            .get()
                            .addOnSuccessListener { phoneDocuments ->
                                if (phoneDocuments.isEmpty) {
                                    onResult(false)  // If no user document found in both collections
                                } else {
                                    val age = phoneDocuments.firstOrNull()?.get("age") as? String
                                    validateAge(age, onResult)
                                }
                            }
                            .addOnFailureListener {
                                onResult(false)
                            }
                    } else {
                        val age = documents.firstOrNull()?.get("age") as? String
                        validateAge(age, onResult)
                    }
                }
                .addOnFailureListener {
                    onResult(false)  // Handle failure cases
                }
        }
    }


    private fun validateAge(age: String?, onResult: (Boolean) -> Unit) {
        // Check if the age is a valid, non-empty string
        val isValid = !age.isNullOrBlank() && age != "0" && age.lowercase() != "n/a"
        onResult(isValid)  // Return the result
    }


    private fun showAgeAlertDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Lengkapi Profil")
            .setMessage("Silakan lengkapi umur Anda di halaman profil sebelum melanjutkan.")
            .setCancelable(false)
            .setPositiveButton("Isi Sekarang") { _, _ ->
                val profileFragment = ProfilePatientFragment()
                replaceFragment(profileFragment)
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun initHomeFragment() {
        replaceFragment(HomeFragment())
        // Remove the dark overlay
        window.decorView.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
    }


    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun checkBluetoothPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
    }

    // Request Location permissions from the user (Android 10 and below)
    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // Request Bluetooth permissions (Android 11 and above)
    private fun requestBluetoothPermissions() {
        blueToothPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN
            )
        )
    }

    // Location permission result handler
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Once location permission is granted, proceed to Bluetooth permissions
            requestBluetoothPermissions()
        } else {
            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Bluetooth permission result handler
    private val blueToothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val bluetoothConnectGranted = permissions[Manifest.permission.BLUETOOTH_CONNECT] == true
        val bluetoothScanGranted = permissions[Manifest.permission.BLUETOOTH_SCAN] == true

        if (bluetoothConnectGranted && bluetoothScanGranted) {
            // Proceed with scanning and initialize HomeFragment
            scanBT()
            initHomeFragment() // Initialize HomeFragment after permission granted
        } else {
            // Continuously show the permission request prompt until user clicks "Allow"
            Toast.makeText(this, "Bluetooth permissions denied. Functionality limited.", Toast.LENGTH_SHORT).show()
            requestBluetoothPermissions()  // Re-prompt if denied
        }
    }


    // Handle Bluetooth permission request and scanning
    private fun scanBt() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            btActivityResultLauncher.launch(enableBtIntent)
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (checkBluetoothPermissions()) {
                    scanBT()
                } else {
                    blueToothPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.BLUETOOTH_CONNECT,
                            Manifest.permission.BLUETOOTH_SCAN
                        )
                    )
                }
            } else {

                if (checkLocationPermission()) {
                    scanBT()
                } else {
                    locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            }
        }
    }

    private val btActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            scanBT() // Bluetooth is enabled, start scanning
        }
    }

    @SuppressLint("MissingPermission")
    private val scanRunnable = object : Runnable {
        override fun run() {
            if (!bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.startDiscovery()
            }
            handler.postDelayed(this, scanInterval)
        }
    }

    @SuppressLint("MissingPermission")
    private fun scanBT() {
        try {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                btActivityResultLauncher.launch(enableBtIntent)
            } else {
                if (!bluetoothAdapter.isDiscovering) {
                    bluetoothAdapter.startDiscovery() // Start Bluetooth discovery
                }
                scanResults.clear()
                deviceAddresses.clear()
                handler.post(scanRunnable)
                showScanDialog()  // Show the scanning dialog
            }
        } catch (e: SecurityException) {
            // Catch the SecurityException if BLUETOOTH_CONNECT permission is not granted
            Log.e("Bluetooth", "Bluetooth permission not granted: ${e.message}")
            // Ignore the error and allow the app to continue (maybe show a message to the user if needed)
        }
    }

    private fun showScanDialog() {
        val builder = AlertDialog.Builder(this@MainPatientActivity)
        val inflater = layoutInflater
        val dialogView: View = inflater.inflate(R.layout.scan_bt, null)
        builder.setView(dialogView)
        val deviceListView = dialogView.findViewById<ListView>(R.id.bt_list)
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val from = arrayOf("A")
        val to = intArrayOf(R.id.item_name)
        listAdapter = SimpleAdapter(this, scanResults, R.layout.item_list2, from, to)
        deviceListView.adapter = listAdapter
        deviceListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val deviceInfo = scanResults[position]
            val deviceName = deviceInfo["A"] ?: "Unknown Device"
            val deviceAddress = deviceInfo["B"] ?: "No Address"
            if (deviceAddress.isNotEmpty() && deviceAddress != "No Address") {
                saveToFirebase(deviceName, deviceAddress)
                try {
                    val homeFragment = HomeFragment().apply {
                        arguments = Bundle().apply {
                            putString("DEVICE_ADDRESS", deviceAddress)
                            putString("DEVICE_NAME", deviceName)
                        }
                    }
                    replaceFragment(homeFragment)
                    Toast.makeText(this@MainPatientActivity, "Connected to $deviceName", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Log.e("Bluetooth", "Error replacing fragment: ${e.message}")
                    Toast.makeText(this@MainPatientActivity, "Error connecting to device", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this@MainPatientActivity, "Invalid device selected", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }
        dialog.show()
    }

    fun replaceFragment(fragment: Fragment, hideBottomNavigation: Boolean = false) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frame, fragment)

        if (hideBottomNavigation) {
            binding.bottomNav.visibility = View.GONE
        } else {
            binding.bottomNav.visibility = View.VISIBLE
        }

        fragmentTransaction.commit()
    }

    private fun saveToFirebase(deviceName: String, deviceAddress: String) {
        getActualPatientUid { patientUid ->
            if (patientUid != null) {
                val deviceInfo = mapOf(
                    "deviceName" to deviceName,
                    "deviceAddress" to deviceAddress
                )
                database.child(patientUid).setValue(deviceInfo)
                    .addOnSuccessListener {
                        Log.d("saveToFirebase", "Device info saved for patient UID: $patientUid")
                    }
                    .addOnFailureListener { e ->
                        Log.e("saveToFirebase", "Failed to save device info: ${e.message}")
                    }
            } else {
                Log.e("saveToFirebase", "Failed to retrieve actual patient UID")
            }
        }
    }
    private fun getActualPatientUid(onResult: (String?) -> Unit) {
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



    private fun updateDeviceList(deviceName: String, deviceAddress: String) {
        val deviceData = mapOf("A" to deviceName, "B" to deviceAddress)
        scanResults.add(deviceData)

        if (::listAdapter.isInitialized) {
            listAdapter.notifyDataSetChanged()
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }
        handler.removeCallbacks(scanRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopScan()
        unregisterReceiver(receiver)
    }
}
