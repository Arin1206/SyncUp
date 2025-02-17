package com.example.syncup.history

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.syncup.history.fragment.DateFragment
import com.example.syncup.history.fragment.MonthFragment
import com.example.syncup.history.fragment.WeekFragment
import com.example.syncup.history.fragment.YearFragment

class HistoryPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {
    override fun getItemCount(): Int = 4 // Jumlah tab

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> DateFragment()
            1 -> WeekFragment()
            2 -> MonthFragment()
            3 -> YearFragment()
            else -> Fragment()
        }
    }
}
