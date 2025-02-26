package com.example.syncup.data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

object HeartRateRepository {
    private val _heartRateLiveData = MutableLiveData<Int>()
    val heartRateLiveData: LiveData<Int> get() = _heartRateLiveData

    private val userId = FirebaseAuth.getInstance().currentUser?.uid ?: "unknown_user"

    private val heartRateDatabase: DatabaseReference =
        FirebaseDatabase.getInstance().reference.child("heart_rate").child(userId).child("latest")

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
