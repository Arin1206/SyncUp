package com.example.syncup.search

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.google.android.gms.tasks.Tasks
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot

class SearchDoctorFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyImage: ImageView
    private lateinit var searchInput: EditText
    private lateinit var doctorAdapter: PatientAdapter
    private lateinit var firestore: FirebaseFirestore

    private var doctors = mutableListOf<PatientData>()

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search_doctor, container, false)

        // Inisialisasi RecyclerView dan ImageView untuk empty state
        recyclerView = view.findViewById(R.id.recycler_view_doctors)
        emptyImage = view.findViewById(R.id.empty_image)
        searchInput = view.findViewById(R.id.search_input)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Inisialisasi adapter kosong
        doctorAdapter = PatientAdapter(doctors, requireContext())
        recyclerView.adapter = doctorAdapter

        // Inisialisasi Firestore
        firestore = FirebaseFirestore.getInstance()

        // Ambil data dokter dari Firestore dari kedua collection
        fetchPatientFromFirestoreRealtime()

        // Tambahkan listener untuk mendeteksi perubahan pada search input
        setupSearchListener()

        // Tambahkan listener untuk menyembunyikan keyboard saat klik di luar search input
        setupHideKeyboardListener(view)

        val bottomNavView = activity?.findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNavView?.visibility = View.VISIBLE

        val scan = activity?.findViewById<FrameLayout>(R.id.scanButtonContainer)
        scan?.visibility = View.VISIBLE

        return view
    }

    private fun setupSearchListener() {
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                searchDoctor(s.toString().trim()) // Panggil fungsi pencarian setelah teks berubah
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
    }

    private fun setupHideKeyboardListener(view: View) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private val heartRateListeners = mutableListOf<ListenerRegistration>()


    private fun fetchPatientFromFirestoreRealtime() {
        val patientList = mutableListOf<PatientData>()

        // Bersihkan listener sebelumnya
        heartRateListeners.forEach { it.remove() }
        heartRateListeners.clear()

        val emailTask = firestore.collection("users_patient_email").get()
        val phoneTask = firestore.collection("users_patient_phonenumber").get()

        Tasks.whenAllSuccess<QuerySnapshot>(emailTask, phoneTask)
            .addOnSuccessListener { snapshots ->
                val combinedDocs = snapshots.flatMap { it.documents }

                for (document in combinedDocs) {
                    val userId = document.getString("userId") ?: continue
                    val name = document.getString("fullName") ?: "Unknown"
                    val gender = document.getString("gender") ?: "None"
                    val age = document.getString("age") ?: "None"
                    val email = document.getString("email") ?: "None"
                    val phonenumber = document.getString("phoneNumber") ?: "None"

                    val listener = firestore.collection("patient_heart_rate")
                        .whereEqualTo("userId", userId)
                        .addSnapshotListener { snapshot, error ->
                            if (error != null) {
                                Log.e("Firestore", "Error listening heart rate for userId: $userId", error)
                                return@addSnapshotListener
                            }
                            if (snapshot == null) return@addSnapshotListener

                            var totalHeartRate = 0.0
                            var totalSystolic = 0.0
                            var totalDiastolic = 0.0
                            var count = 0

                            for (doc in snapshot.documents) {
                                val heartRate = doc.getDouble("heartRate") ?: 0.0
                                val systolicBP = doc.getDouble("systolicBP") ?: 0.0
                                val diastolicBP = doc.getDouble("diastolicBP") ?: 0.0

                                if (heartRate == 0.0) continue

                                totalHeartRate += heartRate
                                totalSystolic += systolicBP
                                totalDiastolic += diastolicBP
                                count++
                            }

                            val avgHeartRate = if (count > 0) (totalHeartRate / count).toInt().toString() else "None"
                            val avgSystolic = if (count > 0) (totalSystolic / count).toInt().toString() else "None"
                            val avgDiastolic = if (count > 0) (totalDiastolic / count).toInt().toString() else "None"

                            // 🔽 Ambil photoUrl dari Firestore (patient_photoprofile)
                            firestore.collection("patient_photoprofile").document(userId).get()
                                .addOnSuccessListener { photoDoc ->
                                    val photoUrl = photoDoc.getString("photoUrl") ?: ""

                                    val patient = PatientData(
                                        id = userId,
                                        name = name,
                                        age = age,
                                        gender = gender,
                                        heartRate = avgHeartRate,
                                        systolicBP = avgSystolic,
                                        diastolicBP = avgDiastolic,
                                        photoUrl = photoUrl,
                                        email = email,
                                        phoneNumber = phonenumber
                                    )

                                    val existingIndex = patientList.indexOfFirst { it.id == userId }
                                    if (existingIndex >= 0) {
                                        patientList[existingIndex] = patient
                                    } else {
                                        patientList.add(patient)
                                    }

                                    doctors.clear()
                                    doctors.addAll(patientList)

                                    val searchQuery = arguments?.getString("searchQuery") ?: ""
                                    if (searchQuery.isNotEmpty()) {
                                        searchDoctor(searchQuery)
                                    } else {
                                        updateUI(doctors)
                                    }
                                }
                        }

                    heartRateListeners.add(listener)
                }
            }
            .addOnFailureListener {
                Log.e("Firestore", "Error fetching patient documents", it)
            }
    }

    private fun searchDoctor(query: String) {
        val filteredDoctors = doctors.filter {
            it.name.contains(query, ignoreCase = true) || it.age.contains(query, ignoreCase = true)
                    || it.email.contains(query, ignoreCase = true) || it.phoneNumber.contains(query, ignoreCase = true)
        }

        if (filteredDoctors.isEmpty()) {
            Log.d("Search", "No doctors found matching query: $query")
        }

        updateUI(filteredDoctors)
    }

    private fun updateUI(filteredPatients: List<PatientData>) {
        if (filteredPatients.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyImage.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyImage.visibility = View.GONE
            doctorAdapter.updateList(filteredPatients)
        }
    }

}