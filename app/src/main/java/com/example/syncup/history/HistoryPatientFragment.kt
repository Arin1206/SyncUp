package com.example.syncup.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.MarginLayoutParams
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.example.syncup.R
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class HistoryPatientFragment : Fragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history_patient, container, false)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = view.findViewById<ViewPager2>(R.id.viewPager)

        val adapter = HistoryPagerAdapter(this)
        viewPager.adapter = adapter

        // Ambil teks dari R.string
        val tabTitles = arrayOf(
            getString(R.string.tab_date),
            getString(R.string.tab_week),
            getString(R.string.tab_month),
            getString(R.string.tab_year)
        )

        // Sambungkan TabLayout dengan ViewPager2
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = formatText(tabTitles[position])
        }.attach()

        // **Menambahkan Margin Antar Tab Setelah Layout Selesai Render**
        tabLayout.post {
            for (i in 0 until tabLayout.tabCount) {
                val tab = (tabLayout.getChildAt(0) as ViewGroup).getChildAt(i)
                val params = tab.layoutParams as MarginLayoutParams
                params.setMargins(10, 15, 10, 15) // Mengatur margin antar tab
                tab.requestLayout()
            }
        }

        return view
    }

    // **Pastikan hanya huruf pertama kapital, sisanya lowercase**
    private fun formatText(text: String): String {
        return text.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
