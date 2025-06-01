package com.example.syncup.data

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore

object HeartRateRepository {
    private val _heartRateLiveData = MutableLiveData<Int>()
    val heartRateLiveData: LiveData<Int> get() = _heartRateLiveData

    private var heartRateDatabase: DatabaseReference? = null
    private var heartRateListener: ValueEventListener? = null

    fun init() {
        getActualPatientUID { patientUid ->
            if (patientUid == null) {
                Log.e("HeartRateRepository", "Failed to get actual patient UID")
                return@getActualPatientUID
            }

            heartRateDatabase = FirebaseDatabase.getInstance()
                .reference.child("heart_rate").child(patientUid).child("latest")

            heartRateListener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val rawValue = snapshot.value
                    Log.d("HeartRateRepository", "onDataChange: Raw value = $rawValue")
                    val heartRate = snapshot.getValue(Long::class.java)?.toInt() ?: -1
                    Log.d("HeartRateRepository", "onDataChange: Received heart rate = $heartRate")
                    _heartRateLiveData.postValue(heartRate)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("HeartRateRepository", "onCancelled: ${error.message}")
                }
            }

            Log.d("HeartRateRepository", "Adding listener to heart_rate/$patientUid/latest")
            heartRateDatabase?.addValueEventListener(heartRateListener as ValueEventListener)
        }
    }

    private fun getActualPatientUID(onResult: (String?) -> Unit) {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return onResult(null)
        val firestore = FirebaseFirestore.getInstance()

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

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

    fun clear() {
        heartRateListener?.let {
            heartRateDatabase?.removeEventListener(it)
        }
        heartRateDatabase = null
        heartRateListener = null
    }
}
