package com.example.syncup.utils

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.example.syncup.R
import com.example.syncup.profile.ProfilePatientFragment
import com.example.syncup.search.SearchPatientFragment

object NavigationUtils {
    fun navigateToPatientFragment(activity: FragmentActivity) {
        val fragment = ProfilePatientFragment()
        activity.supportFragmentManager.beginTransaction()
            .replace(R.id.frame, fragment) // Sesuaikan dengan ID container di layout
            .addToBackStack(null) // Tambahkan ke backstack agar bisa kembali ke Home
            .commit()
    }
}
