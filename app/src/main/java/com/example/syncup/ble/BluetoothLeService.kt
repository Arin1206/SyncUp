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
import com.example.syncup.data.HeartRateRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

class BluetoothLeService : Service() {
    private val binder = LocalBinder()
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var connectionState = STATE_DISCONNECTED
    private var isManualDisconnect = false
    private val handler = Handler(Looper.getMainLooper())
    private var previousSBP: Double = 120.0
    private var previousDBP: Double = 80.0
    private var batteryLevel: Int = -1 // Default -1 jika belum ada pembacaan

    private var lastHeartRate: Int = -1
    // Firestore dan Realtime Database
    private val realtimeDatabase: DatabaseReference = FirebaseDatabase.getInstance().reference.child("heart_rate")
    private val firestore = FirebaseFirestore.getInstance()

    private var reconnectRunnable: Runnable? = null
    private val heartRateBuffer = mutableListOf<Int>() // Buffer untuk menyimpan heart rate selama 5 menit

    // Handler untuk menyimpan data ke Firestore setiap 5 menit
    private val saveToFirestoreRunnable = object : Runnable {
        override fun run() {
            if (heartRateBuffer.isNotEmpty()) {
                val mostFrequentHR = calculateMode(heartRateBuffer)
                val timestamp = System.currentTimeMillis()

                val ptt = 0.25
                val (systolicBP, diastolicBP) = calculateBloodPressure(ptt, mostFrequentHR, previousSBP, previousDBP)

                // **Simpan ke Firestore hanya jika sudah lebih dari 5 menit**
                saveToFirestore(mostFrequentHR, systolicBP, diastolicBP, batteryLevel, timestamp)

                heartRateBuffer.clear()
            }
            handler.postDelayed(this, SAVE_INTERVAL_MS)
        }
    }


    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connectionState = STATE_CONNECTED
                broadcastUpdate(ACTION_GATT_CONNECTED)
                Log.i(TAG, "Connected to GATT server.")
                bluetoothGatt?.discoverServices()

                val intent = Intent(ACTION_GATT_CONNECTED) // 🔹 Kirim broadcast ke UI
                sendBroadcast(intent)

                // Jalankan penyimpanan ke Firestore setiap 5 menit
                handler.postDelayed(saveToFirestoreRunnable, SAVE_INTERVAL_MS)

            }else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Log.w(TAG, "Disconnected from GATT server. Status: $status")

                if (isManualDisconnect) {
                    // ✅ Jika Disconnect Manual → Hapus data dari Firebase
                    FirebaseDatabase.getInstance().reference.child("connected_device").removeValue()
                        .addOnSuccessListener { Log.i(TAG, "Connected device removed from Firebase.") }
                        .addOnFailureListener { e -> Log.e(TAG, "Failed to remove connected device: ${e.message}") }
                } else {
                    // 🔹 Jika Disconnect Otomatis, hanya kirim broadcast ke UI tanpa hapus data
                    Log.w(TAG, "Auto-disconnected, hiding UI without removing device from Firebase.")
                }

                // 🔹 Kirim broadcast ke UI agar bisa menyembunyikan tampilan
                val intent = Intent(ACTION_DEVICE_DISCONNECTED)
                sendBroadcast(intent)

                if (!isManualDisconnect) {
                    handleDisconnect(status)  // Reconnect otomatis jika perlu
                } else {
                    Log.i(TAG, "Manually disconnected from GATT server.")
                    isManualDisconnect = false
                }
            }

        }


        private fun broadcastBatteryUpdate(battery: Int) {
            val intent = Intent(ACTION_BATTERY_LEVEL_MEASUREMENT)
            intent.putExtra(EXTRA_BATTERY_LEVEL, battery)
            sendBroadcast(intent)
        }


        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt?.services ?: emptyList()) {
                    if (service.uuid == UUID.fromString(HEART_RATE_SERVICE_UUID)) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == UUID.fromString(HEART_RATE_CHARACTERISTIC_UUID)) {
                                enableHeartRateNotification(characteristic)
                            }
                        }
                    }
                    // ✅ Tambahkan timeout jika BLE tidak merespons dalam 2 detik
                    else if (service.uuid == UUID.fromString(BATTERY_SERVICE_UUID)) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == UUID.fromString(BATTERY_CHARACTERISTIC_UUID)) {
                                Log.i(TAG, "Battery Level characteristic found. Enabling Read & Notify...")

                                handler.postDelayed({
                                    val success = readBatteryLevel(characteristic)
                                    if (!success) {
                                        Log.w(TAG, "Battery Level READ failed. Enabling NOTIFY instead.")
                                        enableBatteryNotification(characteristic)
                                    }
                                }, 1000) // Delay 1 detik sebelum mencoba Read

                                // 🔹 Timeout jika tidak ada respons dalam 2 detik
                                handler.postDelayed({
                                    if (batteryLevel == -1) {
                                        Log.e(TAG, "Battery Level Read Timeout! Switching to NOTIFY mode.")
                                        enableBatteryNotification(characteristic)
                                    }
                                }, 3000) // Timeout setelah 3 detik
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                if (characteristic.uuid == UUID.fromString(BATTERY_CHARACTERISTIC_UUID)) {
                    val rawData = characteristic.value
                    Log.i(TAG, "Raw Battery Level Data (via READ): ${rawData?.joinToString(" ")}")

                    batteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?:
                            characteristic.value?.get(0)?.toInt() ?: -1
                    Log.i(TAG, "Battery level received via READ: $batteryLevel%")
                    broadcastBatteryUpdate(batteryLevel)
                }
            } else {
                Log.e(TAG, "Failed to read Battery Level characteristic. Status: $status")

                // 🔹 Jika READ gagal, gunakan NOTIFY sebagai cadangan
                Log.w(TAG, "Using NOTIFY as a fallback for Battery Level.")
                enableBatteryNotification(characteristic)
            }
        }



        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            super.onCharacteristicChanged(gatt, characteristic)

            if (characteristic != null && characteristic.uuid == UUID.fromString(
                    HEART_RATE_CHARACTERISTIC_UUID
                )
            ) {
                val heartRate =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1) ?: -1
                lastHeartRate = heartRate // ✅ Simpan heart rate terbaru
                Log.i(TAG, "Heart rate characteristic changed: $heartRate")

                // **Tambahkan heart rate ke buffer (untuk Firestore setiap 5 menit)**
                heartRateBuffer.add(heartRate)

// Update Firebase Realtime (contoh: menyimpan ke node "heart_rate/latest")
                realtimeDatabase.child("latest").setValue(heartRate)
                    .addOnSuccessListener {
                        Log.i(TAG, "Live heart rate updated: $heartRate BPM")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update live heart rate: ${e.message}")
                    }


                // **Broadcast ke UI atau komponen lain**
                broadcastUpdate(ACTION_HEART_RATE_MEASUREMENT, heartRate)

                // **Simpan langsung ke Realtime Database setiap detik**
                realtimeDatabase.child("latest").setValue(heartRate as Any)
                    .addOnSuccessListener {
                        Log.i(TAG, "Live heart rate updated: $heartRate BPM")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to update live heart rate: ${e.message}")
                    }
            }

            // ✅ Pastikan Battery Level diterima melalui Notify
            if (characteristic != null && characteristic.uuid == UUID.fromString(
                    BATTERY_CHARACTERISTIC_UUID
                )
            ) {
                val rawData = characteristic.value
                Log.i(TAG, "Raw Battery Level Data (via NOTIFY): ${rawData?.joinToString(" ")}")

                var tempBatteryLevel = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: -1

                if (tempBatteryLevel == -1 && rawData != null && rawData.isNotEmpty()) {
                    tempBatteryLevel = rawData[0].toInt()
                    Log.w(TAG, "Using manual value extraction for Battery Level: $tempBatteryLevel%")
                }

                batteryLevel = tempBatteryLevel
                Log.i(TAG, "Battery level updated via NOTIFY: $batteryLevel%")
                broadcastBatteryUpdate(batteryLevel)
                if (batteryLevel in 0..100) {
                    saveToFirestore(lastHeartRate, previousSBP, previousDBP, batteryLevel, System.currentTimeMillis())
                } else {
                    Log.e(TAG, "Invalid Battery Level received: $batteryLevel%")
                }
            }
        }
    }

        inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onUnbind(intent: Intent?): Boolean {
        return super.onUnbind(intent)
    }

    fun initialize(): Boolean {
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        return bluetoothAdapter != null
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String?): Boolean {
        if (bluetoothAdapter == null || address == null) return false

        val device = bluetoothAdapter!!.getRemoteDevice(address)
        bluetoothGatt = device.connectGatt(this, false, bluetoothGattCallback)
        connectionState = STATE_CONNECTING
        return true
    }

    @SuppressLint("MissingPermission")
    fun disconnect() {
        bluetoothGatt?.disconnect()
        isManualDisconnect = true
    }

    @SuppressLint("MissingPermission")
    fun close() {
        bluetoothGatt?.close()
        bluetoothGatt = null
        reconnectRunnable?.let { handler.removeCallbacks(it) }
    }

    private fun handleDisconnect(status: Int) {
        reconnectRunnable = Runnable { connect(bluetoothGatt?.device?.address) }
        handler.postDelayed(reconnectRunnable!!, 5000)
    }

    @SuppressLint("MissingPermission")
    private fun enableHeartRateNotification(characteristic: BluetoothGattCharacteristic?) {
        bluetoothGatt?.setCharacteristicNotification(characteristic, true)

        // **Menulis ke Client Characteristic Configuration Descriptor (CCCD)**
        characteristic?.descriptors?.forEach { descriptor ->
            if (descriptor.uuid == UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)) {
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                bluetoothGatt?.writeDescriptor(descriptor)
                Log.i(TAG, "Writing to CCCD to enable notifications")
            }
        }
    }

    private fun broadcastUpdate(action: String, heartRate: Int? = null) {
        val intent = Intent(action)
        if (heartRate != null) {
            intent.putExtra(EXTRA_HEART_RATE, heartRate)
        }
        sendBroadcast(intent)
    }

    private fun calculateMode(values: List<Int>): Int {
        return values.filterNotNull() // pastikan tidak ada nilai null
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: -1
    }


    @SuppressLint("MissingPermission")
    private fun readBatteryLevel(characteristic: BluetoothGattCharacteristic?): Boolean {
        return characteristic?.let {
            // ✅ Periksa apakah karakteristik mendukung operasi READ
            if ((it.properties and BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
                Log.e(TAG, "Battery Level Characteristic does NOT support READ!")
                return false
            }

            val success = bluetoothGatt?.readCharacteristic(it) ?: false
            if (success) {
                Log.i(TAG, "Battery Level read request sent successfully.")
            } else {
                Log.e(TAG, "Failed to read Battery Level characteristic.")
            }
            success
        } ?: false
    }


    private fun calculateBloodPressure(
        ptt: Double,
        heartRate: Int,
        previousSBP: Double,
        previousDBP: Double
    ): Pair<Double, Double> {
        // Parameter hasil optimasi
        val a = 28.25
        val b = 0.3949
        val c = 0.7149
        val d = 43.88

        val e = -3.36
        val f = -0.5194
        val g = 0.5470
        val h = 69.83

        // Tidak ada normalisasi PTT agar bisa mendeteksi anomali tekanan darah
        val sbp = (a * Math.log(ptt) + b * heartRate + c * previousSBP + d)
        val dbp = (e * Math.log(ptt) + f * heartRate + g * previousDBP + h)

        return sbp to dbp
    }

    private var lastSaveTime: Long = 0 // Waktu terakhir penyimpanan data

    private fun saveToFirestore(heartRate: Int, sbp: Double, dbp: Double, batteryLevel: Int, timestamp: Long) {
        val firestore = FirebaseFirestore.getInstance()
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser

        val currentTime = System.currentTimeMillis()

        // **Cek jika sudah lebih dari 5 menit sejak penyimpanan terakhir**
        if (currentTime - lastSaveTime < SAVE_INTERVAL_MS) {
            Log.i(TAG, "Skipping data save: Last save was too recent.")
            return
        }

        // **Update timestamp terakhir penyimpanan**
        lastSaveTime = currentTime

        if (currentUser != null) {
            val userId = currentUser.uid

            val currentDate = Date()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedTimestamp = dateFormat.format(currentDate)

            val data = hashMapOf(
                "userId" to userId,
                "heartRate" to heartRate,
                "systolicBP" to sbp,
                "diastolicBP" to dbp,
                "batteryLevel" to batteryLevel,
                "timestamp" to formattedTimestamp
            )

            firestore.collection("patient_heart_rate")
                .add(data)
                .addOnSuccessListener {
                    Log.i(TAG, "Data saved: User: $userId, Heart Rate: $heartRate BPM, SBP: $sbp mmHg, DBP: $dbp mmHg, Battery: $batteryLevel%, Timestamp: $formattedTimestamp")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Failed to save data: ${e.message}")
                }
        } else {
            Log.e(TAG, "Failed to save data: No user is logged in!")
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBatteryNotification(characteristic: BluetoothGattCharacteristic?) {
        characteristic?.let {
            bluetoothGatt?.setCharacteristicNotification(it, true)

            // **Menulis ke Client Characteristic Configuration Descriptor (CCCD)**
            for (descriptor in it.descriptors) {
                if (descriptor.uuid == UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG)) {
                    descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                    bluetoothGatt?.writeDescriptor(descriptor)
                    Log.i(TAG, "Writing to CCCD to enable battery notifications")
                }
            }
        }
    }

    fun isConnected(): Boolean {
        return connectionState == STATE_CONNECTED
    }


    companion object {
        private const val TAG = "BluetoothLeService"

        // **Interval penyimpanan ke Firestore setiap 5 menit (300.000 ms)**
        private const val SAVE_INTERVAL_MS = 300000L

        // **Action Constants untuk Broadcast**
        const val ACTION_GATT_CONNECTED = "com.example.syncup.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.syncup.ACTION_GATT_DISCONNECTED"
        const val ACTION_HEART_RATE_MEASUREMENT = "com.example.syncup.ACTION_HEART_RATE_MEASUREMENT"
        const val EXTRA_HEART_RATE = "com.example.syncup.EXTRA_HEART_RATE"

        // **Status Koneksi**
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2

        // **UUID untuk Service & Characteristic Heart Rate**
        private const val HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
        private const val HEART_RATE_CHARACTERISTIC_UUID = "00002a37-0000-1000-8000-00805f9b34fb"

        // **UUID untuk Client Characteristic Configuration Descriptor (CCCD)**
        private const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

        private const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"

        const val ACTION_BATTERY_LEVEL_MEASUREMENT = "com.example.syncup.ACTION_BATTERY_LEVEL_MEASUREMENT"
        const val EXTRA_BATTERY_LEVEL = "com.example.syncup.EXTRA_BATTERY_LEVEL"

        // ✅ UUID untuk Battery Level Characteristic
        private const val BATTERY_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb"
        const val ACTION_DEVICE_DISCONNECTED = "com.example.syncup.ACTION_DEVICE_DISCONNECTED"
    }

}
