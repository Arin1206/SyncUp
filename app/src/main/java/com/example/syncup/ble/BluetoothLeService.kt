package com.example.syncup.ble

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.*

class BluetoothLeService : Service() {
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED
    private var isManualDisconnect = false
    private val handler = Handler(Looper.getMainLooper())
    private val database: DatabaseReference = FirebaseDatabase.getInstance().reference.child("connected_device")
    private val heartRateDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference.child("heart_rate")
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
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Log.w(TAG, "Disconnected from GATT server. Status: $status")

                // Hapus perangkat dari Firebase jika terputus
                database.removeValue().addOnSuccessListener {
                    Log.d(TAG, "Device removed from Firebase Realtime Database.")
                }.addOnFailureListener {
                    Log.e(TAG, "Failed to remove device from Firebase: ${it.message}")
                }

                if (!isManualDisconnect) {
                    handleDisconnect(status)
                } else {
                    Log.i(TAG, "Manually disconnected from GATT server.")
                    isManualDisconnect = false
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

                    if (serviceUuid == UUID.fromString(HEART_RATE_SERVICE_UUID)) {
                        for (characteristic in service.characteristics) {
                            val characteristicUuid = characteristic.uuid
                            Log.i(TAG, "Characteristic UUID: $characteristicUuid")

                            if (characteristicUuid == UUID.fromString(HEART_RATE_CHARACTERISTIC_UUID)) {
                                enableHeartRateNotification(characteristic)
                            }
                        }
                    }
                }
            } else {
                Log.w(TAG, "onServicesDiscovered received: $status")
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic != null) {
                Log.i(TAG, "Characteristic Changed UUID: ${characteristic.uuid}")

                if (characteristic.uuid == UUID.fromString(HEART_RATE_CHARACTERISTIC_UUID)) {
                    // Gunakan getIntValue(FORMAT_UINT8, 1) untuk ekstraksi nilai heart rate yang benar
                    val heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1) ?: -1
                    Log.i(TAG, "Heart rate characteristic changed: $heartRate")

                    // Broadcast update heart rate
                    broadcastUpdate(ACTION_HEART_RATE_MEASUREMENT, heartRate)

                    // Simpan heart rate ke Firebase
                    heartRateDatabase.setValue(heartRate).addOnSuccessListener {
                        Log.i(TAG, "Heart rate saved to Firebase: $heartRate")
                    }.addOnFailureListener {
                        Log.e(TAG, "Failed to save heart rate to Firebase: ${it.message}")
                    }
                } else {
                    Log.w(TAG, "Received data from an unknown characteristic: ${characteristic.uuid}")
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
            connect(bluetoothGatt?.device?.address)
        }
        reconnectRunnable?.let { handler.postDelayed(it, 5000) }
    }

    @SuppressLint("MissingPermission")
    private fun enableHeartRateNotification(characteristic: BluetoothGattCharacteristic?) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)
        Log.i(TAG, "Heart rate notification enabled")

        // Cari descriptor CCCD dan tulis untuk mengaktifkan notifikasi
        characteristic?.descriptors?.forEach { descriptor ->
            if (descriptor.uuid == UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt?.writeDescriptor(descriptor)
                Log.i(TAG, "Writing to CCCD to enable notifications")
            }
        }
    }

    companion object {
        private const val TAG = "BluetoothLeService"
        const val ACTION_GATT_CONNECTED = "com.example.syncup.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.syncup.ACTION_GATT_DISCONNECTED"
        const val ACTION_HEART_RATE_MEASUREMENT = "com.example.syncup.ACTION_HEART_RATE_MEASUREMENT"
        const val EXTRA_HEART_RATE = "com.example.syncup.EXTRA_HEART_RATE"

        private const val STATE_DISCONNECTED = 0
        private const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2

        private const val HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
        private const val HEART_RATE_CHARACTERISTIC_UUID = "00002a37-0000-1000-8000-00805f9b34fb"
    }
}
