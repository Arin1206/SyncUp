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
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
    private var batteryLevel: Int = -1

    private var lastHeartRate: Int = -1

    private val realtimeDatabase: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("heart_rate")

    private var reconnectRunnable: Runnable? = null
    private val heartRateBuffer = mutableListOf<Int>()

    private val saveToFirestoreRunnable = object : Runnable {
        override fun run() {
            if (heartRateBuffer.isNotEmpty()) {
                val mostFrequentHR = calculateMode(heartRateBuffer)
                val timestamp = System.currentTimeMillis()

                val ptt = 0.25
                val (systolicBP, diastolicBP) = calculateBloodPressure(
                    ptt,
                    mostFrequentHR,
                    previousSBP,
                    previousDBP
                )

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

                val intent = Intent(ACTION_GATT_CONNECTED)
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)



                handler.postDelayed(saveToFirestoreRunnable, SAVE_INTERVAL_MS)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                connectionState = STATE_DISCONNECTED
                broadcastUpdate(ACTION_GATT_DISCONNECTED)
                Log.w(TAG, "Disconnected from GATT server. Status: $status")

                if (isManualDisconnect) {
                    getActualPatientUID { patientUid ->
                        if (patientUid != null) {
                            FirebaseDatabase.getInstance().reference
                                .child("connected_device")
                                .child(patientUid)
                                .removeValue()
                                .addOnSuccessListener {
                                    Log.i(
                                        TAG,
                                        "Connected device removed for patient UID: $patientUid"
                                    )
                                }
                                .addOnFailureListener { e ->
                                    Log.e(TAG, "Failed to remove connected device: ${e.message}")
                                }
                        } else {
                            Log.e(TAG, "Failed to retrieve actual patient UID for device removal")
                        }
                    }
                } else {
                    Log.w(
                        TAG,
                        "Auto-disconnected, hiding UI without removing device from Firebase."
                    )
                }


                val intent = Intent(ACTION_DEVICE_DISCONNECTED)
                LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)



                if (!isManualDisconnect) {
                    handleDisconnect(status)
                } else {
                    Log.i(TAG, "Manually disconnected from GATT server.")
                    isManualDisconnect = false
                }
            }

        }


        private fun broadcastBatteryUpdate(battery: Int) {
            val intent = Intent(ACTION_BATTERY_LEVEL_MEASUREMENT)
            intent.putExtra(EXTRA_BATTERY_LEVEL, battery)
            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)

        }


        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                for (service in gatt?.services ?: emptyList()) {
                    if (service.uuid == UUID.fromString(HEART_RATE_SERVICE_UUID)) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == UUID.fromString(
                                    HEART_RATE_CHARACTERISTIC_UUID
                                )
                            ) {
                                enableHeartRateNotification(characteristic)
                            }
                        }
                    } else if (service.uuid == UUID.fromString(BATTERY_SERVICE_UUID)) {
                        for (characteristic in service.characteristics) {
                            if (characteristic.uuid == UUID.fromString(BATTERY_CHARACTERISTIC_UUID)) {
                                Log.i(
                                    TAG,
                                    "Battery Level characteristic found. Enabling Read & Notify..."
                                )

                                handler.postDelayed({
                                    val success = readBatteryLevel(characteristic)
                                    if (!success) {
                                        Log.w(
                                            TAG,
                                            "Battery Level READ failed. Enabling NOTIFY instead."
                                        )
                                        enableBatteryNotification(characteristic)
                                    }
                                }, 1000)

                                handler.postDelayed({
                                    if (batteryLevel == -1) {
                                        Log.e(
                                            TAG,
                                            "Battery Level Read Timeout! Switching to NOTIFY mode."
                                        )
                                        enableBatteryNotification(characteristic)
                                    }
                                }, 3000)
                            }
                        }
                    }
                }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && characteristic != null) {
                if (characteristic.uuid == UUID.fromString(BATTERY_CHARACTERISTIC_UUID)) {
                    val rawData = characteristic.value
                    Log.i(TAG, "Raw Battery Level Data (via READ): ${rawData?.joinToString(" ")}")

                    batteryLevel =
                        characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0)
                            ?: characteristic.value?.get(0)?.toInt() ?: -1
                    Log.i(TAG, "Battery level received via READ: $batteryLevel%")
                    broadcastBatteryUpdate(batteryLevel)
                }
            } else {
                Log.e(TAG, "Failed to read Battery Level characteristic. Status: $status")

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
                lastHeartRate = heartRate
                Log.i(TAG, "Heart rate characteristic changed: $heartRate")
                heartRateBuffer.add(heartRate)
                getActualPatientUID { patientUid ->
                    if (patientUid != null) {
                        val userHeartRateDatabase =
                            realtimeDatabase.child(patientUid).child("latest")
                        userHeartRateDatabase.setValue(heartRate)
                            .addOnSuccessListener {
                                Log.i(
                                    TAG,
                                    "Live heart rate updated for user $patientUid: $heartRate BPM"
                                )
                            }
                            .addOnFailureListener { e ->
                                Log.e(
                                    TAG,
                                    "Failed to update live heart rate for user $patientUid: ${e.message}"
                                )
                            }
                    } else {
                        Log.e(TAG, "Patient UID not found. Skipping heart rate update.")
                    }
                }
                broadcastUpdate(ACTION_HEART_RATE_MEASUREMENT, heartRate)
            }
            if (characteristic != null && characteristic.uuid == UUID.fromString(
                    BATTERY_CHARACTERISTIC_UUID
                )
            ) {
                val rawData = characteristic.value
                Log.i(TAG, "Raw Battery Level Data (via NOTIFY): ${rawData?.joinToString(" ")}")
                var tempBatteryLevel =
                    characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0) ?: -1
                if (tempBatteryLevel == -1 && rawData != null && rawData.isNotEmpty()) {
                    tempBatteryLevel = rawData[0].toInt()
                    Log.w(
                        TAG,
                        "Using manual value extraction for Battery Level: $tempBatteryLevel%"
                    )
                }
                batteryLevel = tempBatteryLevel
                Log.i(TAG, "Battery level updated via NOTIFY: $batteryLevel%")
                broadcastBatteryUpdate(batteryLevel)
                if (batteryLevel in 0..100) {
                    saveToFirestore(
                        lastHeartRate,
                        previousSBP,
                        previousDBP,
                        batteryLevel,
                        System.currentTimeMillis()
                    )
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
        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(intent)
        Log.d(TAG, "📢 Broadcast sent: $action with heartRate=$heartRate")
    }

    private fun calculateMode(values: List<Int>): Int {
        return values.filterNotNull()
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key ?: -1
    }


    @SuppressLint("MissingPermission")
    private fun readBatteryLevel(characteristic: BluetoothGattCharacteristic?): Boolean {
        return characteristic?.let {
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
        val a = 0.2458
        val b = 0.4579
        val c = 0.7162
        val d = 1.9881

        val e = 1.5851
        val f = 0.1518
        val g = -0.0649
        val h = 72.3263

        val sbp = (a * Math.log(ptt) + b * heartRate + c * previousSBP + d)
        val dbp = (e * Math.log(ptt) + f * heartRate + g * previousDBP + h)

        return sbp to dbp
    }

    private var lastSaveTime: Long = 0

    private fun getActualPatientUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else if (phoneNumber != null) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else {
            onResult(null)
        }
    }

    private fun saveToFirestore(
        heartRate: Int,
        sbp: Double,
        dbp: Double,
        batteryLevel: Int,
        timestamp: Long
    ) {
        val firestore = FirebaseFirestore.getInstance()

        val currentTime = System.currentTimeMillis()
        if (currentTime - lastSaveTime < SAVE_INTERVAL_MS) {
            Log.i(TAG, "Skipping data save: Last save was too recent.")
            return
        }
        lastSaveTime = currentTime

        getActualPatientUID { actualPatientUid ->
            if (actualPatientUid == null) {
                Log.e(TAG, "❌ Gagal menyimpan: UID pasien tidak ditemukan.")
                return@getActualPatientUID
            }

            val currentDate = Date()
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val formattedTimestamp = dateFormat.format(currentDate)

            val data = hashMapOf(
                "userId" to actualPatientUid,
                "heartRate" to heartRate,
                "systolicBP" to sbp,
                "diastolicBP" to dbp,
                "batteryLevel" to batteryLevel,
                "timestamp" to formattedTimestamp
            )

            firestore.collection("patient_heart_rate")
                .add(data)
                .addOnSuccessListener {
                    Log.i(TAG, "✅ Data berhasil disimpan untuk pasien UID: $actualPatientUid")
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "❌ Gagal menyimpan data: ${e.message}")
                }
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableBatteryNotification(characteristic: BluetoothGattCharacteristic?) {
        characteristic?.let {
            bluetoothGatt?.setCharacteristicNotification(it, true)

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

        private const val SAVE_INTERVAL_MS = 60000L

        const val ACTION_GATT_CONNECTED = "com.example.syncup.ACTION_GATT_CONNECTED"
        const val ACTION_GATT_DISCONNECTED = "com.example.syncup.ACTION_GATT_DISCONNECTED"
        const val ACTION_HEART_RATE_MEASUREMENT = "com.example.syncup.ACTION_HEART_RATE_MEASUREMENT"
        const val EXTRA_HEART_RATE = "com.example.syncup.EXTRA_HEART_RATE"

        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2

        private const val HEART_RATE_SERVICE_UUID = "0000180d-0000-1000-8000-00805f9b34fb"
        private const val HEART_RATE_CHARACTERISTIC_UUID = "00002a37-0000-1000-8000-00805f9b34fb"

        private const val CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805f9b34fb"

        private const val BATTERY_SERVICE_UUID = "0000180f-0000-1000-8000-00805f9b34fb"

        const val ACTION_BATTERY_LEVEL_MEASUREMENT =
            "com.example.syncup.ACTION_BATTERY_LEVEL_MEASUREMENT"
        const val EXTRA_BATTERY_LEVEL = "com.example.syncup.EXTRA_BATTERY_LEVEL"

        private const val BATTERY_CHARACTERISTIC_UUID = "00002a19-0000-1000-8000-00805f9b34fb"
        const val ACTION_DEVICE_DISCONNECTED = "com.example.syncup.ACTION_DEVICE_DISCONNECTED"
    }

}
