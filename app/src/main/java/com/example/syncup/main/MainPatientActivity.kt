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
import android.widget.*
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.ble.BluetoothLeService
import com.example.syncup.ble.DeviceControlActivity
import com.example.syncup.databinding.ActivityMainPatientBinding
import com.example.syncup.faq.FaqFragment
import com.example.syncup.history.HistoryPatientFragment
import com.example.syncup.home.HomeFragment
import com.example.syncup.welcome.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class MainPatientActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityMainPatientBinding
    private var backPressedTime: Long = 0
    private lateinit var auth: FirebaseAuth
    private lateinit var bluetoothAdapter: BluetoothAdapter
    private val handler = Handler(Looper.getMainLooper())
    private val scanInterval: Long = 15000 // Interval scanning 15 detik
    private val scanResults = mutableListOf<Map<String, String>>()
    private lateinit var listAdapter: SimpleAdapter
    private val deviceAddresses = mutableSetOf<String>()
    private lateinit var database: DatabaseReference

    // BroadcastReceiver untuk menangkap perangkat Bluetooth
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

        val serviceIntent = Intent(this, BluetoothLeService::class.java)
        startService(serviceIntent)

        database = FirebaseDatabase.getInstance().reference.child("connected_device")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = getColor(android.R.color.transparent)  // Status bar transparan
 // Status bar putih, teks hitam

        auth = FirebaseAuth.getInstance()
        window.navigationBarColor = getColor(R.color.black)

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
        registerReceiver(receiver, filter)

        binding.scanButtonContainer.setOnClickListener {
            scanBt(it)
        }

        replaceFragment(HomeFragment())

        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.homepage -> replaceFragment(HomeFragment())
                R.id.history-> replaceFragment(HistoryPatientFragment())
                R.id.faq->replaceFragment(FaqFragment())
                else -> {}
            }
            true
        }
    }
    private fun saveToFirebase(deviceName: String, deviceAddress: String) {
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid  // **Gunakan UID pengguna saat ini**
            val deviceInfo = mapOf(
                "deviceName" to deviceName,
                "deviceAddress" to deviceAddress
            )
            database.child(userId).setValue(deviceInfo) // **Simpan berdasarkan UID**
        }
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



    // Periksa izin Bluetooth
    private fun checkPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.BLUETOOTH_SCAN
        ) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    // Minta izin Bluetooth
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION),
            1
        )
    }

    // Fungsi untuk scanning Bluetooth
    fun scanBt(view: View) {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Device doesn't support Bluetooth", Toast.LENGTH_SHORT).show()
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blueToothPermissionLauncher.launch(Manifest.permission.BLUETOOTH_SCAN)
            } else {
                blueToothPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }

    private val blueToothPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            if (!bluetoothAdapter.isEnabled) {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                btActivityResultLauncher.launch(enableBtIntent)
            } else {
                scanBT()
            }
        } else {
            Toast.makeText(this, "Bluetooth permission denied", Toast.LENGTH_SHORT).show()
        }
    }

    private val btActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK) {
            scanBT()
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
        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            btActivityResultLauncher.launch(enableBtIntent)
        } else {
            if (!bluetoothAdapter.isDiscovering) {
                bluetoothAdapter.startDiscovery()
            }
            scanResults.clear()
            deviceAddresses.clear()
            handler.post(scanRunnable)
            showScanDialog()
        }
    }

    private fun showScanDialog() {
        val builder = AlertDialog.Builder(this@MainPatientActivity)
        val inflater = layoutInflater
        val dialogView: View = inflater.inflate(R.layout.scan_bt, null)
        builder.setView(dialogView)

        val deviceListView = dialogView.findViewById<ListView>(R.id.bt_list)
        val dialog = builder.create()

        // **Pastikan listAdapter telah diinisialisasi sebelum digunakan**
        val from = arrayOf("A")
        val to = intArrayOf(R.id.item_name)
        listAdapter = SimpleAdapter(this, scanResults, R.layout.item_list, from, to)
        deviceListView.adapter = listAdapter

        deviceListView.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val deviceInfo = scanResults[position]
            val deviceName = deviceInfo["A"] ?: "Unknown Device"
            val deviceAddress = deviceInfo["B"] ?: "No Address"

            if (deviceAddress.isNotEmpty() && deviceAddress != "No Address") {
                saveToFirebase(deviceName, deviceAddress) // Simpan ke Firebase

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

    private fun updateDeviceList(deviceName: String, deviceAddress: String) {
        val deviceData = mapOf("A" to deviceName, "B" to deviceAddress)
        scanResults.add(deviceData)

        // **Cek apakah listAdapter sudah diinisialisasi sebelum update**
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
