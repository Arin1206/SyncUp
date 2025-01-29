package com.example.syncup.welcome

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.syncup.DoctorLoginFragment
import com.example.syncup.PatientLoginFragment

class ViewPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    private val fragmentList = mutableMapOf<Int, Fragment>()

    override fun getItemCount(): Int = 2 // Dua tab: Patient dan Doctor

    override fun createFragment(position: Int): Fragment {
        val fragment = when (position) {
            0 -> PatientLoginFragment()
            1 -> DoctorLoginFragment()
            else -> throw IllegalStateException("Invalid position: $position")
        }
        fragmentList[position] = fragment
        return fragment
    }

    fun getFragment(position: Int): Fragment? {
        return fragmentList[position]
    }

    override fun getItemId(position: Int): Long {
        return position.toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return fragmentList.containsKey(itemId.toInt())
    }
}
