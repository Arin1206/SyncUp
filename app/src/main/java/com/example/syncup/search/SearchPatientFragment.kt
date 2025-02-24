package com.example.syncup.search

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R

class SearchPatientFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyImage: ImageView
    private lateinit var doctorAdapter: DoctorAdapter

    private var doctors = listOf(
        Doctor("Dr. Yanuar", "Lorem ipsum dolor sit amet...", R.drawable.sample_doctor),
        Doctor("Dr. Rina", "Spesialis Jantung dan Pembuluh Darah", R.drawable.sample_doctor),
        Doctor("Dr. Budi", "Dokter Umum dengan pengalaman 10 tahun", R.drawable.sample_doctor)
    )

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_search_patient, container, false)

        // Inisialisasi RecyclerView dan ImageView untuk empty state
        recyclerView = view.findViewById(R.id.recycler_view_doctors)
        emptyImage = view.findViewById(R.id.empty_image)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Inisialisasi adapter
        doctorAdapter = DoctorAdapter(doctors)
        recyclerView.adapter = doctorAdapter

        val searchQuery = arguments?.getString("searchQuery") ?: ""
        if (searchQuery.isNotEmpty()) {
            searchDoctor(searchQuery)
        } else {
            updateUI(doctors)
        }

        return view
    }

    private fun searchDoctor(query: String) {
        val filteredDoctors = doctors.filter { it.name.contains(query, ignoreCase = true) }
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
