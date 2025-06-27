package com.example.syncup.history

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.syncup.history.fragment.DateFragment
import com.example.syncup.history.fragment.MonthFragment
import com.example.syncup.history.fragment.WeekFragment
import com.example.syncup.history.fragment.YearFragment

class HistoryPagerAdapter(fragment: Fragment, private val patientId: String? = null) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4 // Jumlah tab

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> {
                val dateFragment = DateFragment()
                val bundle = Bundle()
                patientId?.let {
                    bundle.putString("patientId", it)  // Mengirimkan patientId ke DateFragment jika ada
                }
                dateFragment.arguments = bundle
                dateFragment
            }
            1 -> {
                val weekFragment = WeekFragment()
                val bundle = Bundle()
                patientId?.let {
                    bundle.putString("patientId", it)  // Mengirimkan patientId ke WeekFragment jika ada
                }
                weekFragment.arguments = bundle
                weekFragment
            }
            2 -> {
                val monthFragment = MonthFragment()
                val bundle = Bundle()
                patientId?.let {
                    bundle.putString("patientId", it)  // Mengirimkan patientId ke MonthFragment jika ada
                }
                monthFragment.arguments = bundle
                monthFragment
            }
            3 -> {
                val yearFragment = YearFragment()
                val bundle = Bundle()
                patientId?.let {
                    bundle.putString("patientId", it)  // Mengirimkan patientId ke YearFragment jika ada
                }
                yearFragment.arguments = bundle
                yearFragment
            }
            else -> Fragment() // Default fragment
        }
    }
}
