package com.example.syncup

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.syncup.databinding.FragmentPatientListBinding
import com.example.syncup.home.NonScrollableLinearLayoutManager
import com.example.syncup.search.PatientAdapter
import com.example.syncup.search.PatientData
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
class OfflineFragment : Fragment() {

    private var _binding: FragmentPatientListBinding? = null
      private val binding get() = _binding ?: throw IllegalStateException("View binding is only valid between onCreateView and onDestroyView")

    private lateinit var patientAdapter: PatientAdapter

    private val realtimeDB = FirebaseDatabase.getInstance().reference

    private val patientListOffline = mutableListOf<PatientData>()
    private val heartRateListeners = mutableMapOf<String, ValueEventListener>()
    private val lastHeartRates = mutableMapOf<String, Int>()
    private val lastUpdateTimestamps = mutableMapOf<String, Long>()
    private var connectedDeviceListener: ValueEventListener? = null
    private val handler = Handler(Looper.getMainLooper())
    private val assignedPatientIds = mutableListOf<String>()


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
        binding.recyclerView.layoutManager = NonScrollableLinearLayoutManager(requireContext())
        binding.recyclerView.adapter = patientAdapter

        getActualDoctorUID { actualUid ->
            actualUid?.let {
                fetchAssignedPatients(it)
            } ?: run {
                Log.e("OfflineFragment", "Doctor UID tidak ditemukan dari email/phone")
            }
        }


        patientAdapter.setHideAddButton(true)

        // Mulai loop refresh setiap 1 detik
        handler.post(refreshRunnable)
    }

    private var assignedPatientsLoaded = false

    fun fetchAssignedPatients(doctorUid: String) {
        FirebaseFirestore.getInstance()
            .collection("assigned_patient")
            .whereEqualTo("doctorUid", doctorUid)
            .get()
            .addOnSuccessListener { documents ->
                assignedPatientIds.clear()
                assignedPatientIds.addAll(documents.mapNotNull { it.getString("patientId") })

                assignedPatientsLoaded = true // ✅ Tandai bahwa data siap
                patientAdapter.setAssignedPatients(assignedPatientIds)
            }
    }

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (assignedPatientsLoaded) {
                fetchOfflinePatients()
            }

            handler.postDelayed(this, 1000)
        }
    }


    fun fetchOfflinePatients() {
        showLoading() // ⬅️ Tampilkan loading

        getActualDoctorUID { doctorUid ->
            if (doctorUid == null) {
                Log.e("OfflineFragment", "Doctor UID tidak ditemukan")
                hideLoading() // ⬅️ Pastikan disembunyikan kalau gagal
                return@getActualDoctorUID
            }

            FirebaseFirestore.getInstance()
                .collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUid)
                .get()
                .addOnSuccessListener { documents ->
                    assignedPatientIds.clear()
                    assignedPatientIds.addAll(documents.mapNotNull { it.getString("patientId") })
                    patientAdapter.setAssignedPatients(assignedPatientIds)

                    val connectedDeviceListener = object : ValueEventListener {
                        override fun onDataChange(snapshot: DataSnapshot) {
                            val uidList = mutableListOf<String>()
                            for (userSnapshot in snapshot.children) {
                                val uid = userSnapshot.key ?: continue
                                if ((userSnapshot.hasChild("deviceAddress") || userSnapshot.hasChild("deviceName"))
                                    && assignedPatientIds.contains(uid)
                                ) {
                                    uidList.add(uid)
                                }
                            }

                            Log.d("OfflineFragment", "Filtered Assigned UIDs: $uidList")
                            observePatientHeartRates(uidList)
                            hideLoading() // ⬅️ Sembunyikan loading setelah selesai
                        }

                        override fun onCancelled(error: DatabaseError) {
                            Log.e("OfflineFragment", "DB Error: ${error.message}")
                            hideLoading()
                        }
                    }

                    realtimeDB.child("connected_device").addListenerForSingleValueEvent(connectedDeviceListener)
                }
                .addOnFailureListener {
                    Log.e("OfflineFragment", "Gagal mengambil assigned patients")
                    hideLoading()
                }
        }
    }


    private fun showLoading() {
        binding.loadingOverlay.visibility = View.VISIBLE
    }

    private fun hideLoading() {
        if (view != null && isAdded && _binding != null) {
            binding.progressBar.isVisible = false
            binding.recyclerView.isVisible = true
        }
    }


    private fun observePatientHeartRates(uidList: List<String>) {
        // Bersihkan listener sebelumnya
        heartRateListeners.forEach { (uid, listener) ->
            realtimeDB.child("heart_rate").child(uid).child("latest").removeEventListener(listener)
        }
        heartRateListeners.clear()

        for (uid in uidList) {
            val heartRateRef = realtimeDB.child("heart_rate").child(uid).child("latest")

            val listener = object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val heartRate = snapshot.getValue(Int::class.java) ?: 0

                    if (heartRate == 0) {
                        fetchPatientDataForOffline(uid) // ⬅️ Tambahkan ini untuk memanggil data lengkap dari Firestore
                    } else {
                        // Kalau pasien sekarang online, hapus dari list offline kalau ada
                        patientListOffline.removeAll { it.id == uid }
                    }



                    patientAdapter.updateList(patientListOffline)
                    patientAdapter.setAssignedPatients(assignedPatientIds)

                    Log.d("OfflineFragment", "UID: $uid, heartRate: $heartRate")

                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("OfflineFragment", "Error at $uid: ${error.message}")
                }
            }

            heartRateRef.addValueEventListener(listener)
            heartRateListeners[uid] = listener
        }
    }

    private fun fetchPatientDataForOffline(patientId: String) {
        val firestore = FirebaseFirestore.getInstance()


        fun fetchUserInfoAndBP(userDoc: Map<String, Any>?) {
            if (userDoc != null) {
                val age = userDoc["age"] as? String ?: "-"
                val gender = userDoc["gender"] as? String ?: "-"
                val email = userDoc["email"] as? String ?: "-"
                val phoneNumber = userDoc["phoneNumber"] as? String ?: "-"
                val name = userDoc["fullName"] as? String ?: ""


                // Ambil tekanan darah terbaru
                firestore.collection("patient_heart_rate")
                    .whereEqualTo("userId", patientId)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { querySnapshot ->
                        val bpDoc = querySnapshot.documents.firstOrNull()
                        val systolicBP = bpDoc?.getLong("systolicBP") ?: "-"
                        val diastolicBP = bpDoc?.getLong("diastolicBP") ?: "-"


                        // Ambil photoUrl
                        firestore.collection("patient_photoprofile")
                            .whereEqualTo("userId", patientId)
                            .limit(1)
                            .get()
                            .addOnSuccessListener { photoSnapshot ->
                                val photoDoc = photoSnapshot.documents.firstOrNull()
                                val photoUrl = photoDoc?.getString("photoUrl") ?: "None"


                                val updatedPatient = PatientData(
                                    id = patientId,
                                    name = name,
                                    age = age,
                                    gender = gender,
                                    heartRate = "0",
                                    systolicBP = systolicBP.toString(),
                                    diastolicBP = diastolicBP.toString(),
                                    photoUrl = photoUrl,
                                    email = email,
                                    phoneNumber = phoneNumber
                                )


                                val index = patientListOffline.indexOfFirst { it.id == patientId }
                                if (index != -1) {
                                    patientListOffline[index] = updatedPatient
                                } else {
                                    patientListOffline.add(updatedPatient)
                                }


                                patientAdapter.updateList(patientListOffline)
                                patientAdapter.setAssignedPatients(assignedPatientIds)
                            }
                            .addOnFailureListener {
                                Log.e("OfflineFragment", "Gagal ambil photoUrl: ${it.message}")
                            }
                    }
                    .addOnFailureListener {
                        Log.e("OfflineFragment", "Gagal ambil tekanan darah terbaru: ${it.message}")
                    }
            } else {
                Log.e("OfflineFragment", "Data user tidak ditemukan untuk $patientId")
            }
        }


        // Cari data user di dua koleksi
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
                            Log.e("OfflineFragment", "Gagal ambil user dari phoneNumber untuk $patientId: ${it.message}")
                        }
                }
            }
            .addOnFailureListener {
                Log.e("OfflineFragment", "Gagal ambil user dari email untuk $patientId: ${it.message}")
            }
    }



    override fun onDestroyView() {
        super.onDestroyView()

        // Hentikan handler untuk mencegah NullPointerException
        handler.removeCallbacks(refreshRunnable)

        // Bersihkan listener
        heartRateListeners.forEach { (uid, listener) ->
            realtimeDB.child("heart_rate").child(uid).child("latest").removeEventListener(listener)
        }
        heartRateListeners.clear()

        connectedDeviceListener?.let {
            realtimeDB.child("connected_device").removeEventListener(it)
        }
        connectedDeviceListener = null

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
