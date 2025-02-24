package com.example.syncup.search

import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.google.firebase.firestore.FirebaseFirestore

class SearchPatientFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyImage: ImageView
    private lateinit var searchInput: EditText
    private lateinit var doctorAdapter: DoctorAdapter
    private lateinit var firestore: FirebaseFirestore

    private var doctors = mutableListOf<Doctor>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search_patient, container, false)

        // Inisialisasi RecyclerView dan ImageView untuk empty state
        recyclerView = view.findViewById(R.id.recycler_view_doctors)
        emptyImage = view.findViewById(R.id.empty_image)
        searchInput = view.findViewById(R.id.search_input)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Inisialisasi adapter kosong
        doctorAdapter = DoctorAdapter(doctors)
        recyclerView.adapter = doctorAdapter

        // Inisialisasi Firestore
        firestore = FirebaseFirestore.getInstance()

        // Ambil data dokter dari Firestore dari kedua collection
        fetchDoctorsFromFirestore()

        // Tambahkan listener untuk mendeteksi perubahan pada search input
        setupSearchListener()

        // Tambahkan listener untuk menyembunyikan keyboard saat klik di luar search input
        setupHideKeyboardListener(view)

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

    private fun fetchDoctorsFromFirestore() {
        val doctorList = mutableListOf<Doctor>()

        // Ambil data dari users_doctor_email
        firestore.collection("users_doctor_email")
            .get()
            .addOnSuccessListener { emailDocs ->
                for (document in emailDocs) {
                    var fullName = document.getString("fullName") ?: "Unknown Doctor"
                    val email = document.getString("email") ?: "No Email"
                    val profileImage = R.drawable.sample_doctor // Ganti dengan gambar default atau load dari URL

                    // Tambahkan "Dr." jika belum ada
                    if (!fullName.startsWith("Dr.")) {
                        fullName = "Dr. $fullName"
                    }

                    val doctor = Doctor(fullName, email, profileImage)
                    doctorList.add(doctor)
                }

                // Ambil data dari users_doctor_phonenumber setelah users_doctor_email selesai
                firestore.collection("users_doctor_phonenumber")
                    .get()
                    .addOnSuccessListener { phoneDocs ->
                        for (document in phoneDocs) {
                            var fullName = document.getString("fullName") ?: "Unknown Doctor"
                            val phoneNumber = document.getString("phoneNumber") ?: "No Phone Number"
                            val profileImage = R.drawable.sample_doctor

                            // Tambahkan "Dr." jika belum ada
                            if (!fullName.startsWith("Dr.")) {
                                fullName = "Dr. $fullName"
                            }

                            val doctor = Doctor(fullName, phoneNumber, profileImage)
                            doctorList.add(doctor)
                        }

                        // Perbarui daftar dokter setelah semua data diambil
                        doctors.clear()
                        doctors.addAll(doctorList)

                        // Cek apakah ada query dari HomeFragment
                        val searchQuery = arguments?.getString("searchQuery") ?: ""
                        if (searchQuery.isNotEmpty()) {
                            searchDoctor(searchQuery)
                        } else {
                            updateUI(doctors)
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("Firestore", "Error fetching doctors from phone collection: ", e)
                    }
            }
            .addOnFailureListener { e ->
                Log.e("Firestore", "Error fetching doctors from email collection: ", e)
            }
    }

    private fun searchDoctor(query: String) {
        val filteredDoctors = doctors.filter {
            it.name.contains(query, ignoreCase = true) || it.description.contains(query, ignoreCase = true)
        }

        if (filteredDoctors.isEmpty()) {
            Log.d("Search", "No doctors found matching query: $query")
        }

        updateUI(filteredDoctors)
    }

    private fun updateUI(filteredDoctors: List<Doctor>) {
        if (filteredDoctors.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyImage.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            emptyImage.visibility = View.GONE
            doctorAdapter.updateList(filteredDoctors)
        }
    }
}
