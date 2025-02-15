package com.example.syncup.home

import android.content.*
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.ble.BluetoothLeService
import com.example.syncup.welcome.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth

class HomeFragment : Fragment() {

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var bluetoothLeService: BluetoothLeService? = null
    private var isBound = false
    private var isReceiverRegistered = false  // FLAG untuk mengecek apakah receiver sudah terdaftar

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

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        // Ambil data perangkat jika ada
        arguments?.let {
            deviceAddress = it.getString("DEVICE_ADDRESS")
            deviceName = it.getString("DEVICE_NAME")
        }

        // Menampilkan informasi perangkat atau pesan default jika belum memilih
        val deviceInfoTextView = view.findViewById<TextView>(R.id.device_name)
        deviceInfoTextView.text = if (deviceName != null) {
            "Connected to: $deviceName ($deviceAddress)"
        } else {
            "No Device Selected"
        }

        // Tombol Logout
        val logoutButton = view.findViewById<TextView>(R.id.logoutbutton)
        logoutButton.setOnClickListener {
            logoutUser()
        }

        // Mulai layanan Bluetooth jika ada perangkat yang dipilih
        if (deviceAddress != null) {
            val intent = Intent(requireContext(), BluetoothLeService::class.java)
            requireContext().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

            if (!isReceiverRegistered) {
                requireContext().registerReceiver(heartRateReceiver, makeGattUpdateIntentFilter())
                isReceiverRegistered = true  // Tandai receiver sebagai terdaftar
                Log.d(TAG, "Receiver registered")
            }
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (isBound) {
            requireContext().unbindService(serviceConnection)
            isBound = false
        }

        if (isReceiverRegistered) {  // Hanya unregister jika sudah terdaftar
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
                view?.findViewById<TextView>(R.id.heart_rate_value)?.text = "$heartRate bpm"
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

        // Logout dari Firebase
        auth.signOut()

        // Kembali ke WelcomeActivity
        val intent = Intent(requireContext(), WelcomeActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
    }

    companion object {
        private const val TAG = "HomeFragment"
    }
}
