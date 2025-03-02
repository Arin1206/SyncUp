package com.example.syncup.history

import android.annotation.SuppressLint
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

    private lateinit var viewPager: ViewPager2

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_history_patient, container, false)

        val tabLayout = view.findViewById<TabLayout>(R.id.tabLayout)
        viewPager = view.findViewById(R.id.viewPager)

        val adapter = HistoryPagerAdapter(this)
        viewPager.adapter = adapter

        // **Optimasi: Simpan semua fragment di memori agar tidak reload**
        viewPager.offscreenPageLimit = 3  // Semua tab tetap aktif di memori

        val tabTitles = arrayOf(
            getString(R.string.tab_date),
            getString(R.string.tab_week),
            getString(R.string.tab_month),
            getString(R.string.tab_year)
        )

        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            tab.text = formatText(tabTitles[position])
        }.attach()

        // **Set tinggi ViewPager ke match_parent setelah layout selesai dibuat**
        viewPager.post { updateViewPagerHeight() }

        // **Listener untuk memperbarui tinggi saat tab berubah**
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateViewPagerHeight()
            }
        })

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

    override fun onResume() {
        super.onResume()
        updateViewPagerHeight()
    }

    // **Pastikan ViewPager2 selalu match_parent tanpa delay**
    private fun updateViewPagerHeight() {
        viewPager.post {
            val layoutParams = viewPager.layoutParams
            layoutParams.height = ViewGroup.LayoutParams.MATCH_PARENT
            viewPager.layoutParams = layoutParams
            viewPager.requestLayout() // 🔥 Pastikan perubahan langsung diterapkan
        }
    }

    private fun formatText(text: String): String {
        return text.lowercase().replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }
}
