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
import com.google.firebase.database.*

class HomeFragment : Fragment() {

    private var deviceAddress: String? = null
    private var deviceName: String? = null
    private var bluetoothLeService: BluetoothLeService? = null
    private var isBound = false
    private var isReceiverRegistered = false
    private lateinit var database: DatabaseReference
    private lateinit var heartRateDatabase: DatabaseReference
    private var deviceEventListener: ValueEventListener? = null
    private var heartRateEventListener: ValueEventListener? = null
    private var heartRateTextView: TextView? = null

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
                            "Connected to: $deviceName ($deviceAddress)"
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
        heartRateEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists() && isAdded) {
                    val heartRate = snapshot.getValue(Int::class.java) ?: -1
                    heartRateTextView?.text = "$heartRate bpm"
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "Failed to read heart rate from Firebase: ${error.message}")
            }
        }

        heartRateDatabase.addValueEventListener(heartRateEventListener!!)

        // Tombol Logout
        view.findViewById<TextView>(R.id.logoutbutton)?.setOnClickListener {
            logoutUser()
        }

        return view
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

        // **Hapus Listener Firebase untuk Menghindari Crash**
        deviceEventListener?.let { database.removeEventListener(it) }
        heartRateEventListener?.let { heartRateDatabase.removeEventListener(it) }
    }

    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothLeService.ACTION_HEART_RATE_MEASUREMENT == intent.action) {
                val heartRate = intent.getIntExtra(BluetoothLeService.EXTRA_HEART_RATE, -1)
                Log.d(TAG, "Heart rate received: $heartRate")
                heartRateTextView?.text = "$heartRate bpm"

                // Simpan heart rate ke Firebase
                heartRateDatabase.setValue(heartRate)
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
