package com.example.syncup.ble

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.syncup.R

class DeviceControlActivity : AppCompatActivity() {
    private lateinit var deviceAddress: String
    private lateinit var deviceName: String
    private var bluetoothLeService: BluetoothLeService? = null
    private var isBound = false

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_control)

        deviceAddress = intent.getStringExtra("DEVICE_ADDRESS")!!
        deviceName = intent.getStringExtra("DEVICE_NAME")!!

        findViewById<TextView>(R.id.device_name).text = deviceName

        val intent = Intent(this, BluetoothLeService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)

        registerReceiver(heartRateReceiver, makeGattUpdateIntentFilter())
        Log.d(TAG, "Receiver registered")
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isBound) {
            unbindService(serviceConnection)
        } else {
            Log.d(TAG, "Perangkat terbind")
        }
        unregisterReceiver(heartRateReceiver)
        Log.d(TAG, "Receiver unregistered and service unbound")
    }

    private val heartRateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (BluetoothLeService.ACTION_HEART_RATE_MEASUREMENT == intent.action) {
                val heartRate = intent.getIntExtra(BluetoothLeService.EXTRA_HEART_RATE, -1)
                Log.d(TAG, "Heart rate received: $heartRate")
                findViewById<TextView>(R.id.heart_rate_value).text = "$heartRate bpm"
            }
        }
    }

    private fun makeGattUpdateIntentFilter(): IntentFilter {
        return IntentFilter().apply {
            addAction(BluetoothLeService.ACTION_HEART_RATE_MEASUREMENT)
        }
    }

    companion object {
        private const val TAG = "DeviceControlActivity"
    }
}
