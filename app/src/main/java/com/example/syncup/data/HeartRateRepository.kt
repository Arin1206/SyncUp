package com.example.syncup.data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.database.*

object HeartRateRepository {
    private val _heartRateLiveData = MutableLiveData<Int>()
    val heartRateLiveData: LiveData<Int> get() = _heartRateLiveData

    // Mengambil referensi ke node "heart_rate" lalu ke child "latest"
    private val heartRateDatabase: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("heart_rate").child("latest")

    private val heartRateListener = object : ValueEventListener {
        override fun onDataChange(snapshot: DataSnapshot) {
            val rawValue = snapshot.value
            Log.d("HeartRateRepository", "onDataChange: Raw value = $rawValue")
            val heartRate = (snapshot.getValue(Long::class.java)?.toInt()) ?: -1
            Log.d("HeartRateRepository", "onDataChange: Received heart rate = $heartRate")
            _heartRateLiveData.postValue(heartRate)
        }

        override fun onCancelled(error: DatabaseError) {
            Log.e("HeartRateRepository", "onCancelled: ${error.message}")
        }
    }

    init {
        Log.d("HeartRateRepository", "Initializing and adding listener to node 'heart_rate/latest'")
        heartRateDatabase.addValueEventListener(heartRateListener)
    }
}
