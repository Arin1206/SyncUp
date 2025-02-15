package com.example.syncup.ble

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import java.util.UUID

class BluetoothLeService : Service() {
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED
    private var isManualDisconnect = false
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
                Log.i(TAG, "Connected to GATT server.")
                bluetoothGatt?.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (!isManualDisconnect) {
                    connectionState = STATE_DISCONNECTED
                    broadcastUpdate(ACTION_GATT_DISCONNECTED)
                    Log.w(TAG, "Disconnected from GATT server. Status: $status")
                    handleDisconnect(status)
                } else {
                    Log.i(TAG, "Manually disconnected from GATT server.")
                    isManualDisconnect = false // Reset the flag after manual disconnection
                }
            }
        }


        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered.")
                for (service in gatt?.services ?: emptyList()) {
                    val serviceUuid = service.uuid
                    Log.i(TAG, "Service UUID: $serviceUuid")


                    if (serviceUuid == UUID.fromString("0000CC00-0000-1000-8000-00805F9B34FB")) {
                        for (characteristic in service.characteristics) {
                            val characteristicUuid = characteristic.uuid
                            Log.i(TAG, "Characteristic UUID: $characteristicUuid")


                            if (characteristicUuid == UUID.fromString("0000CC03-0000-1000-8000-00805F9B34FB")) {
                                readCharacteristic(characteristic)
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }


        @SuppressLint("MissingPermission")
        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                characteristic?.let {
                    val data = it.value
                    Log.i(TAG, "Characteristic read UUID: ${it.uuid}, Raw Data: ${data.joinToString(", ")}")


                    if (it.uuid == UUID.fromString("0000CC03-0000-1000-8000-00805F9B34FB")) {
                        // Misalnya, BPM berada di byte pertama
                        val heartRate = if (data.isNotEmpty()) data[2].toInt() and 0xFF else -1
                        Log.i(TAG, "Heart rate characteristic read: $heartRate")
                        broadcastUpdate(ACTION_HEART_RATE_MEASUREMENT, heartRate)
                    }
                }
            } else {
                Log.w(TAG, "onCharacteristicRead received: $status")
            }
        }


        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic != null) {
                Log.i(TAG, "Characteristic Changed UUID: ${characteristic.uuid}")


                if (characteristic.uuid == UUID.fromString("0000CC03-0000-1000-8000-00805F9B34FB")) {
                    // Misalnya, BPM berada di byte pertama
                    val heartRate = if (characteristic.value.isNotEmpty()) characteristic.value[0].toInt() and 0xFF else -1
                    Log.i(TAG, "Heart rate characteristic changed: $heartRate")
                    broadcastUpdate(ACTION_HEART_RATE_MEASUREMENT, heartRate)
                }
            }
        }


    }


    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }


    override fun onBind(intent: Intent): IBinder {
        return binder
    }


    override fun onUnbind(intent: Intent?): Boolean {
        close()
        return super.onUnbind(intent)
    }


    fun initialize(): Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter != null
    }


    @SuppressLint("MissingPermission")
    fun connect(address: String?): Boolean {
        if (bluetoothAdapter == null || address == null) {
            Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.")
            return false
        }


        val device = bluetoothAdapter!!.getRemoteDevice(address)
        bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
        Log.d(TAG, "Trying to create a new connection.")
        connectionState = STATE_CONNECTING
        return true
    }


    @SuppressLint("MissingPermission")
    fun disconnect() {
        if (bluetoothGatt == null) {
            Log.w(TAG, "BluetoothGatt not initialized")
            return
        }
        isManualDisconnect = true
        bluetoothGatt?.disconnect()
    }


    @SuppressLint("MissingPermission")
    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        reconnectRunnable?.let { handler.removeCallbacks(it) }
    }


    private fun broadcastUpdate(action: String, heartRate: Int? = null) {
        val intent = Intent(action)
        if (heartRate != null) {
            intent.putExtra(EXTRA_HEART_RATE, heartRate)
        }
        sendBroadcast(intent)
    }


    private fun handleDisconnect(status: Int) {
        Log.e(TAG, "Disconnected due to error: $status")
        reconnectRunnable = Runnable {
            connect(bluetoothGatt?.device?.address) // Attempt to reconnect
        }
        reconnectRunnable?.let { handler.postDelayed(it, 5000) } // Reconnect after 5 seconds
    }


    @SuppressLint("MissingPermission")
    private fun readCharacteristic(characteristic: BluetoothGattCharacteristic?) {
        bluetoothGatt?.readCharacteristic(characteristic)
    }


    companion object {
        private const val TAG = "BluetoothLeService"
        const val ACTION_GATT_CONNECTED = "com.example.tugasakhir.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.tugasakhir.ACTION_GATT_DISCONNECTED"
        const val ACTION_HEART_RATE_MEASUREMENT = "com.example.tugasakhir.ACTION_HEART_RATE_MEASUREMENT"
        const val EXTRA_HEART_RATE = "com.example.tugasakhir.EXTRA_HEART_RATE"
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2
    }
}




