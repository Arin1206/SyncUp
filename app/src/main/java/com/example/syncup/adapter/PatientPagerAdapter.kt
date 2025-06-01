package com.example.syncup.adapter

import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.syncup.OfflineFragment
import com.example.syncup.OnlineFragment

class PatientPagerAdapter(fragment: Fragment) : FragmentStateAdapter(fragment) {

    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when(position) {
            0 -> OnlineFragment()
            1 -> OfflineFragment()
            else -> throw IllegalStateException("Invalid position $position")
        }
    }
}