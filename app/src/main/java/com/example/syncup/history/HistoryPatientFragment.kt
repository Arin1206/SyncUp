package com.example.syncup.history

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.syncup.R
import com.example.syncup.history.fragment.DateFragment
import com.example.syncup.home.HomeDoctorFragment
import com.example.syncup.home.HomeFragment
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class HistoryPatientFragment : Fragment() {

    private lateinit var viewPager: ViewPager2

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history_patient, container, false)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        val arrow = view.findViewById<ImageView>(R.id.arrow)
        val patientId = arguments?.getString("patientId")

// Log the patientId (even if it is null)
        Log.d("HistoryPatientFragment", "Received patientId: $patientId")

// Create the adapter even if patientId is null (you can pass null as the patientId)
        val adapter = HistoryPagerAdapter(this, patientId ?: "")

        viewPager.adapter = adapter

// **Optimasi: Simpan semua fragment di memori agar tidak reload**
        viewPager.offscreenPageLimit = 3  // Semua tab tetap aktif di memori

// **Attach the TabLayoutMediator after the adapter is set**
        val tabTitles = arrayOf(
            getString(R.string.tab_date),
            getString(R.string.tab_week),
            getString(R.string.tab_month),
            getString(R.string.tab_year)
        )

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = formatText(tabTitles[position])
        }.attach()

        if (patientId == null) {
            Log.d("HistoryPatientFragment", "Patient ID tidak ditemukan, but continuing anyway.")
        }

        // **Set tinggi ViewPager ke match_parent setelah layout selesai dibuat**
        viewPager.post { updateViewPagerHeight() }

        // **Listener untuk memperbarui tinggi saat tab berubah**
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateViewPagerHeight()
            }
        })



        val isFromDoctorFragment = arguments?.getBoolean("isFromDoctorFragment", false) ?: false


        arrow.setOnClickListener {
            val fragmentTransaction = requireActivity().supportFragmentManager.beginTransaction()

            if (isFromDoctorFragment) {
                // Kembali ke HomeDoctorFragment jika asalnya dari DoctorFragment
                val homeDoctorFragment = HomeDoctorFragment()
                fragmentTransaction.replace(R.id.frame, homeDoctorFragment)
            } else {
                // Kembali ke HomeFragment jika asalnya dari HomeFragment
                val homeFragment = HomeFragment()
                fragmentTransaction.replace(R.id.frame, homeFragment)
            }

            fragmentTransaction.addToBackStack(null)  // Menambahkan ke backstack
            fragmentTransaction.commit()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Periksa jika berasal dari DoctorFragment atau HomeFragment
            if (isFromDoctorFragment) {
                // Kembali ke HomeDoctorFragment jika asalnya dari DoctorFragment
                val homeDoctorFragment = HomeDoctorFragment()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, homeDoctorFragment)  // Ensure 'frame' is the container ID for fragments
                    .commit()
            } else {
                // Kembali ke HomeFragment jika asalnya dari HomeFragment
                val homeFragment = HomeFragment()
                requireActivity().supportFragmentManager.beginTransaction()
                    .replace(R.id.frame, homeFragment)  // Ensure 'frame' is the container ID for fragments
                    .commit()
            }
        }


        // **Menambahkan Margin Antar Tab**
        tabLayout.post {
            for (i in 0 until tabLayout.tabCount) {
                val tab = (tabLayout.getChildAt(0) as ViewGroup).getChildAt(i)
                val params = tab.layoutParams as MarginLayoutParams
                params.setMargins(10, 15, 10, 15)
                tab.requestLayout()
            }
        }

        return view
    }

    private fun navigateToDateFragment(patientId: String) {
        val dateFragment = DateFragment()

        // Mengirim patientId melalui Bundle
        val bundle = Bundle().apply {
            putString("patientId", patientId)  // Mengirimkan patientId ke DateFragment
        }

        dateFragment.arguments = bundle

        // Navigasi ke DateFragment
        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, dateFragment)
            .addToBackStack(null)
            .commit()
    }
    override fun onResume() {
        super.onResume()
        updateViewPagerHeight()
    }

    // **Pastikan ViewPager2 selalu match_parent tanpa delay**
    private fun updateViewPagerHeight() {
        viewPager.post {
            val layoutParams = viewPager.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT // Ikuti tinggi konten
            viewPager.layoutParams = layoutParams
            viewPager.requestLayout()
        }
    }


    private fun formatText(text: String): String {
        return text.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
