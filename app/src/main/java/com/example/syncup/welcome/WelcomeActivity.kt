package com.example.syncup.welcome

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import com.example.syncup.main.MainPatientActivity
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WelcomeActivity : AppCompatActivity() {

    private lateinit var tabLayout: TabLayout
    private lateinit var viewPager2: ViewPager2
    private var doubleBackPressed = false
    private val handler = Handler(Looper.getMainLooper())

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_welcome)

        enableEdgeToEdge()

        // Set warna navigation bar ke abu-abu terang
        window.navigationBarColor = getColor(R.color.black)

        // Initialize TabLayout and ViewPager2
        tabLayout = findViewById(R.id.tabLayout)
        viewPager2 = findViewById(R.id.viewpager2)

        // Set up the adapter for ViewPager2
        val adapter = ViewPagerAdapter(this)
        viewPager2.adapter = adapter

        // Connect TabLayout with ViewPager2 using TabLayoutMediator
        TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
            tab.text = when (position) {
                0 -> "Patient"
                1 -> "Doctor"
                else -> "Tab $position"
            }
        }.attach()
        val fragmentType = intent.getStringExtra("fragment")
        if (fragmentType == "doctor") {
            viewPager2.post {
                viewPager2.currentItem = 1 // Pindah ke tab "Doctor"
            }
        }
// Auto-resize ViewPager2 when page is changed
        viewPager2.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                adjustViewPagerHeight(position) // Panggil fungsi untuk menyesuaikan tinggi ViewPager2
            }
        })



        // Set listener untuk menyembunyikan keyboard ketika area non-input ditekan
        setHideKeyboardListener(findViewById(R.id.main))
        setHideKeyboardListener(viewPager2)
        setHideKeyboardListener(tabLayout)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // Menggunakan OnBackPressedDispatcher untuk menangani tombol back
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (doubleBackPressed) {
                    finishAffinity() // Keluar dari aplikasi
                } else {
                    doubleBackPressed = true
                    Toast.makeText(this@WelcomeActivity, "Tekan sekali lagi untuk keluar", Toast.LENGTH_SHORT).show()
                    handler.postDelayed({ doubleBackPressed = false }, 2000)
                }
            }
        })


    }
    private fun adjustViewPagerHeight(position: Int) {
        viewPager2.post {
            val fragment = (viewPager2.adapter as? ViewPagerAdapter)?.getFragment(position)
            fragment?.view?.post {
                Handler(Looper.getMainLooper()).postDelayed({
                    val params = viewPager2.layoutParams
                    params.height = fragment.view?.measuredHeight ?: ViewGroup.LayoutParams.WRAP_CONTENT
                    viewPager2.layoutParams = params
                }, 100) // Tambahkan delay 100ms agar fragment sempat dirender
            }
        }
    }


    private fun setHideKeyboardListener(view: View) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
}
