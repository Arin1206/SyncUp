package com.example.syncup.ble

import android.annotation.SuppressLint
import android.app.Service
import android.bluetooth.*
import android.bluetooth.BluetoothAdapter.STATE_CONNECTED
import android.bluetooth.BluetoothAdapter.STATE_DISCONNECTED
import android.content.ContentValues.TAG
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.firestore.FirebaseFirestore
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

                // Simulasi nilai PTT (Pulse Transit Time) untuk perhitungan BP
                val ptt = 0.25 // Harus diganti dengan nilai aktual dari sensor
                val previousBP = 120.0 // Bisa diambil dari Firestore jika sudah ada

                // Hitung Blood Pressure (BP)
                // Dapatkan nilai SBP dan DBP sebagai Pair
                val (systolicBP, diastolicBP) = calculateBloodPressure(ptt, mostFrequentHR, previousSBP, previousDBP)

// Simpan ke Firestore dengan memisahkan SBP dan DBP
                saveToFirestore(mostFrequentHR, systolicBP, diastolicBP, timestamp)


                // Kosongkan buffer setelah penyimpanan
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

                // Jalankan penyimpanan ke Firestore setiap 5 menit
                handler.postDelayed(saveToFirestoreRunnable, SAVE_INTERVAL_MS)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Log.w(TAG, "Disconnected from GATT server. Status: $status")

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
                for (service in gatt?.services ?: emptyList()) {
                    if (service.uuid == UUID.fromString(HEART_RATE_SERVICE_UUID)) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == UUID.fromString(HEART_RATE_CHARACTERISTIC_UUID)) {
                                enableHeartRateNotification(characteristic)
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic != null && characteristic.uuid == UUID.fromString(HEART_RATE_CHARACTERISTIC_UUID)) {
                val heartRate = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1) ?: -1
                Log.i(TAG, "Heart rate characteristic changed: $heartRate")

                // **Tambahkan heart rate ke buffer (untuk Firestore setiap 5 menit)**
                heartRateBuffer.add(heartRate)

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
        }
    }

    inner class LocalBinder : Binder() {
        fun getService(): BluetoothLeService = this@BluetoothLeService
    }

    override fun onBind(intent: Intent): IBinder = binder

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
        return values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key ?: -1
    }

    private fun calculateBloodPressure(
        ptt: Double,
        heartRate: Int,
        previousSBP: Double,
        previousDBP: Double
    ): Pair<Double, Double> {
        // Parameter optimal dari hasil optimasi Python
        val a = 28.25  // Pengaruh utama PTT terhadap SBP
        val b = 0.39   // Pengaruh HR terhadap SBP
        val c = 0.71   // Pengaruh tekanan darah sebelumnya terhadap SBP
        val d = 43.88  // Konstanta dasar SBP

        val e = -3.36  // Pengaruh utama PTT terhadap DBP
        val f = -0.52  // Pengaruh HR terhadap DBP
        val g = 0.55   // Pengaruh tekanan darah sebelumnya terhadap DBP
        val h = 69.83  // Konstanta dasar DBP

        // Normalisasi PTT agar tetap dalam batas fisiologis
        val normalizedPTT = if (ptt in 0.2..0.4) ptt else 0.3

        // Hitung SBP dan DBP menggunakan parameter yang telah dioptimalkan
        val sbp = (a * Math.log(normalizedPTT) + b * heartRate + c * previousSBP + d)
        val dbp = (e * Math.log(normalizedPTT) + f * heartRate + g * previousDBP + h)

        // Pastikan nilai berada dalam rentang fisiologis dan mendekati data asli
        return Pair(sbp.coerceIn(90.0, 160.0), dbp.coerceIn(60.0, 100.0))
    }



    private fun saveToFirestore(heartRate: Int, sbp: Double, dbp: Double, timestamp: Long) {
        val data = hashMapOf(
            "heartRate" to heartRate,
            "systolicBP" to sbp,
            "diastolicBP" to dbp,
            "timestamp" to timestamp
        )

        firestore.collection("patient_heart_rate")
            .add(data)
            .addOnSuccessListener {
                Log.i(TAG, "Heart rate and BP saved: $heartRate BPM, SBP: $sbp mmHg, DBP: $dbp mmHg")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to save data: ${e.message}")
            }
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
    }

}
