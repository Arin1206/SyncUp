package com.example.syncup.history

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.syncup.history.fragement_doctor.DateDoctorFragment
import com.example.syncup.history.fragement_doctor.MonthDoctorFragment
import com.example.syncup.history.fragement_doctor.WeekDoctorFragment
import com.example.syncup.history.fragement_doctor.YearDoctorFragment
import com.example.syncup.history.fragment.DateFragment
import com.example.syncup.history.fragment.MonthFragment
import com.example.syncup.history.fragment.WeekFragment
import com.example.syncup.history.fragment.YearFragment

class HistoryPagerAdapterDoctor(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4 // Jumlah tab

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DateDoctorFragment()
            1 -> WeekDoctorFragment()
            2 -> MonthDoctorFragment()
            3 -> YearDoctorFragment()
            else -> Fragment()
        }
    }
}
