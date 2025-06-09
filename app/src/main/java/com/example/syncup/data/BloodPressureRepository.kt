package com.example.syncup.data

import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.roundToInt
import com.google.firebase.firestore.Query

object BloodPressureRepository {
    private val _bloodPressureLiveData = MutableLiveData<BloodPressure>()

    val bloodPressureLiveData: LiveData<BloodPressure> get() = _bloodPressureLiveData


    private val firestore = FirebaseFirestore.getInstance()
    private val bpCollection = firestore.collection("patient_heart_rate")

    private val handler = Handler(Looper.getMainLooper())
    private val updateInterval = 30000L

    private var lastUpdatedTime: Long = 0
    private var cachedBloodPressure: BloodPressure? = null

    private val bpRunnable = object : Runnable {
        override fun run() {
            fetchLatestBloodPressure()
            handler.postDelayed(this, updateInterval)
        }
    }

    private fun fetchLatestBloodPressure() {
        val currentTime = System.currentTimeMillis()

        if (cachedBloodPressure != null && (currentTime - lastUpdatedTime) < 60000) {
            Log.d("BloodPressureRepository", "🟢 Menggunakan cached BP: $cachedBloodPressure")
            _bloodPressureLiveData.postValue(cachedBloodPressure)
            return
        }
        bpCollection.orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(5)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) {
                    for (data in snapshot.documents) {
                        val hr = data.getDouble("heartRate")?.roundToInt() ?: 0
                        if (hr > 0) {
                            val sbp =
                                data.get("systolicBP")?.toString()?.toDoubleOrNull()?.roundToInt()
                                    ?: -1
                            val dbp =
                                data.get("diastolicBP")?.toString()?.toDoubleOrNull()?.roundToInt()
                                    ?: -1

                            if (sbp > 0 && dbp > 0) {
                                val newBP = BloodPressure(sbp, dbp)
                                cachedBloodPressure = newBP
                                lastUpdatedTime = System.currentTimeMillis()
                                Log.d("BloodPressureRepository", "🔥 BP Terbaru: $newBP")
                                _bloodPressureLiveData.postValue(newBP)
                            } else {
                                Log.e("BloodPressureRepository", "❌ Data BP tidak valid")
                                _bloodPressureLiveData.postValue(BloodPressure(-1, -1))
                            }
                            return@addOnSuccessListener
                        }
                    }
                    _bloodPressureLiveData.postValue(BloodPressure(-1, -1))
                }
            }
            .addOnFailureListener { error ->
                Log.e("BloodPressureRepository", "🔥 Gagal polling data BP: ${error.message}")
            }
    }


    fun startPolling() {
        Log.d("BloodPressureRepository", "▶ Memulai polling BP...")
        handler.post(bpRunnable)
    }

    fun delayedStartPolling() {
        Log.d("BloodPressureRepository", "⏳ Menunda polling BP selama 5 menit...")
        handler.postDelayed(bpRunnable, 60000)
    }

    fun stopPolling() {
        handler.removeCallbacks(bpRunnable)
        Log.d("BloodPressureRepository", "🛑 Polling BP dihentikan.")
    }
}
