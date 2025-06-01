package com.example.syncup

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.example.syncup.databinding.FragmentPatientListBinding
import com.example.syncup.home.NonScrollableLinearLayoutManager
import com.example.syncup.home.PatientCache
import com.example.syncup.search.PatientAdapter
import com.example.syncup.search.PatientData
import com.example.syncup.viewmodel.SharedViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class OnlineFragment : Fragment() {

    private var _binding: FragmentPatientListBinding? = null
    private val binding get() = _binding!!
    private lateinit var patientAdapter: PatientAdapter
    private val firestore = FirebaseFirestore.getInstance()

    private val realtimeDB = FirebaseDatabase.getInstance().reference
    val allPatients = mutableListOf<PatientData>()
    private val sharedViewModel: SharedViewModel by activityViewModels()
    private val assignedPatientIds = mutableListOf<String>()

    private val patientDetailsCache = mutableMapOf<String, PatientData>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentPatientListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        patientAdapter = PatientAdapter(mutableListOf())
        binding.recyclerView.adapter = patientAdapter
        binding.recyclerView.layoutManager = NonScrollableLinearLayoutManager(requireContext())

        patientAdapter.setHideAddButton(true)

        // Pertama: Dapatkan UID dokter, lalu fetch assigned patients dan data pasien
        getActualDoctorUID { doctorUid ->
            if (doctorUid != null) {
                fetchAssignedPatients(doctorUid) {
                    observePatientHeartRates(assignedPatientIds) // ✅ Sekarang data sudah siap
                }
            } else {
                Log.e("OnlineFragment", "Doctor UID not found")
            }
        }


    }


    private var assignedPatientsLoaded = false

    fun fetchAssignedPatients(doctorUid: String, onComplete: () -> Unit) {
        FirebaseFirestore.getInstance()
            .collection("assigned_patient")
            .whereEqualTo("doctorUid", doctorUid)
            .get()
            .addOnSuccessListener { documents ->
                assignedPatientIds.clear()
                assignedPatientIds.addAll(documents.mapNotNull { it.getString("patientId") })

                assignedPatientsLoaded = true
                patientAdapter.setAssignedPatients(assignedPatientIds)
                onComplete() // ✅ Panggil saat selesai
            }
    }




    private val patientList = mutableListOf<PatientData>()
    private val heartRateListeners = mutableMapOf<String, ValueEventListener>()

    private val lastHeartRates = mutableMapOf<String, Int>()
    private val lastUpdateTimestamps = mutableMapOf<String, Long>()

    private val patientListOnline = mutableListOf<PatientData>()
    private val patientListOffline = mutableListOf<PatientData>()

    private fun observePatientHeartRates(uidList: List<String>) {
        patientListOnline.clear()
        patientListOffline.clear()
        patientAdapter.updateList(patientListOnline)
        heartRateListeners.values.forEach {
            realtimeDB.removeEventListener(it)
        }
        heartRateListeners.clear()

        for (uid in uidList) {
            fetchPatientDataForOnline(uid) { patientData ->
                if (patientData != null) {
                    patientDetailsCache[uid] = patientData
                }
            }

            val heartRateRef = realtimeDB.child("heart_rate").child(uid).child("latest")
            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val heartRate = snapshot.getValue(Int::class.java) ?: 0
                    val currentTime = System.currentTimeMillis()
                    val lastRate = lastHeartRates[uid]
                    val lastUpdateTime = lastUpdateTimestamps[uid] ?: 0L

                    if (lastRate == null || heartRate != lastRate) {
                        lastUpdateTimestamps[uid] = currentTime
                    }
                    lastHeartRates[uid] = heartRate

                    val timeSinceLastUpdate = currentTime - (lastUpdateTimestamps[uid] ?: 0L)

                    val baseData = patientDetailsCache[uid]
                    val updatedPatient = baseData?.copy(
                        heartRate = heartRate.toString()
                    ) ?: PatientData(
                        id = uid,
                        name = "Example",
                        age = "0",
                        gender = "-",
                        heartRate = heartRate.toString(),
                        systolicBP = "None",
                        diastolicBP = "None",
                        photoUrl = "None",
                        email = "None",
                        phoneNumber = "None"
                    )

                    if (timeSinceLastUpdate <= 1000 && heartRate > 0) {
                        if (!patientListOnline.any { it.id == uid }) {
                            patientListOnline.add(updatedPatient)
                        } else {
                            val index = patientListOnline.indexOfFirst { it.id == uid }
                            patientListOnline[index] = updatedPatient
                        }
                        patientListOffline.removeAll { it.id == uid }
                    } else {
                        if (!patientListOffline.any { it.id == uid }) {
                            patientListOffline.add(updatedPatient)
                        } else {
                            val index = patientListOffline.indexOfFirst { it.id == uid }
                            patientListOffline[index] = updatedPatient
                        }
                        patientListOnline.removeAll { it.id == uid }
                    }

                    patientAdapter.updateList(patientListOnline.toList())
// Show/hide empty image
                    if (patientListOnline.isEmpty()) {
                        binding.emptyImage.visibility = View.VISIBLE
                        binding.emptyText.visibility = View.VISIBLE
                    } else {
                        binding.emptyImage.visibility = View.GONE
                        binding.emptyText.visibility = View.GONE
                    }

                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("OnlineFragment", "Failed to fetch heart rate for $uid: ${error.message}")
                }
            }

            heartRateRef.addValueEventListener(listener)
            heartRateListeners[uid] = listener
        }

    }


    private fun fetchPatientDataForOnline(patientId: String, onResult: (PatientData?) -> Unit) {
        val firestore = FirebaseFirestore.getInstance()

        fun fetchUserInfoAndBP(userDoc: Map<String, Any>?) {
            if (userDoc != null) {
                val age = userDoc["age"] as? String ?: "-"
                val gender = userDoc["gender"] as? String ?: "-"
                val email = userDoc["email"] as? String ?: "-"
                val phoneNumber = userDoc["phoneNumber"] as? String ?: "-"
                val name = userDoc["fullName"] as? String ?: ""

                firestore.collection("patient_heart_rate")
                    .whereEqualTo("userId", patientId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val bpDoc = querySnapshot.documents.firstOrNull()
                        val systolicBP = bpDoc?.getLong("systolicBP") ?: 0L
                        val diastolicBP = bpDoc?.getLong("diastolicBP") ?: 0L

                        firestore.collection("patient_photoprofile")
                            .whereEqualTo("userId", patientId)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { photoSnapshot ->
                                val photoDoc = photoSnapshot.documents.firstOrNull()
                                val photoUrl = photoDoc?.getString("photoUrl") ?: "None"

                                val patientData = PatientData(
                                    id = patientId,
                                    name = name,
                                    age = age,
                                    gender = gender,
                                    heartRate = "0",  // nanti diupdate dari observePatientHeartRates
                                    systolicBP = systolicBP.toString(),
                                    diastolicBP = diastolicBP.toString(),
                                    photoUrl = photoUrl,
                                    email = email,
                                    phoneNumber = phoneNumber
                                )

                                onResult(patientData)

                            }
                            .addOnFailureListener {
                                Log.e("OnlineFragment", "Failed to get photoUrl: ${it.message}")
                                onResult(null)
                            }
                    }
                    .addOnFailureListener {
                        Log.e("OnlineFragment", "Failed to get latest BP: ${it.message}")
                        onResult(null)
                    }
            } else {
                Log.e("OnlineFragment", "User data not found for $patientId")
                onResult(null)
            }
        }

        firestore.collection("users_patient_email")
            .whereEqualTo("userId", patientId)
            .get()
            .addOnSuccessListener { docsEmail ->
                val userDoc = docsEmail.firstOrNull()
                if (userDoc != null) {
                    fetchUserInfoAndBP(userDoc.data)
                } else {
                    firestore.collection("users_patient_phonenumber")
                        .whereEqualTo("userId", patientId)
                        .get()
                        .addOnSuccessListener { docsPhone ->
                            val userPhoneDoc = docsPhone.firstOrNull()
                            fetchUserInfoAndBP(userPhoneDoc?.data)
                        }
                        .addOnFailureListener {
                            Log.e("OnlineFragment", "Failed to get user by phone for $patientId: ${it.message}")
                            onResult(null)
                        }
                }
            }
            .addOnFailureListener {
                Log.e("OnlineFragment", "Failed to get user by email for $patientId: ${it.message}")
                onResult(null)
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        heartRateListeners.forEach { (uid, listener) ->
            realtimeDB.child("heart_rate").child(uid).child("latest").removeEventListener(listener)
        }
        heartRateListeners.clear()
        _binding = null
    }

    private fun getActualDoctorUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_doctor_email")
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
            firestore.collection("users_doctor_phonenumber")
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

}