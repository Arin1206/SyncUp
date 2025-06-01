package com.example.syncup.main

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.chat.ChatDoctorFragment
import com.example.syncup.databinding.ActivityMainDoctorBinding
import com.example.syncup.databinding.ActivityMainPatientBinding
import com.example.syncup.faq.FaqDoctorFragment
import com.example.syncup.history.AlertDoctorFragment
import com.example.syncup.home.HomeDoctorFragment
import com.example.syncup.home.HomeFragment
import com.example.syncup.inbox.InboxDoctorFragment
import com.google.firebase.auth.FirebaseAuth

class MainDoctorActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityMainDoctorBinding
    private var backPressedTime: Long = 0
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainDoctorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.statusBarColor = getColor(android.R.color.transparent)  // Status bar transparan

        auth = FirebaseAuth.getInstance()  // **Inisialisasi FirebaseAuth untuk Logout**

        window.navigationBarColor = getColor(R.color.black)

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            val navigationBarHeight = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom

            // Angkat BottomNavigationView agar berada di atas navigation bar bawaan
            v.translationY = -navigationBarHeight.toFloat()

            // Angkat Floating Button (scanButtonContainer) juga agar sejajar dengan BottomNavigationView
            binding.scanButtonContainer.translationY = -(navigationBarHeight.toFloat() + 15)

            insets
        }
        replaceFragment(HomeDoctorFragment())

        binding.scanButton.setOnClickListener {
            val alertFragment = AlertDoctorFragment()
            supportFragmentManager.beginTransaction()
                .replace(R.id.frame, alertFragment) // Sesuaikan dengan id FrameLayout fragment container di activity_main_doctor.xml
                .addToBackStack(null)
                .commit()
        }



        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.homepage -> replaceFragment(HomeDoctorFragment())
                R.id.inbox -> replaceFragment(InboxDoctorFragment())
                R.id.faq -> replaceFragment(FaqDoctorFragment())
                R.id.chat -> replaceFragment(ChatDoctorFragment())
                else -> {}
            }
            true
        }
    }


    fun replaceFragment(fragment: Fragment, hideBottomNavigation: Boolean = false) {
        val fragmentManager = supportFragmentManager
        val fragmentTransaction = fragmentManager.beginTransaction()
        fragmentTransaction.replace(R.id.frame, fragment)

        if (hideBottomNavigation) {
            binding.bottomNav.visibility = View.GONE
        } else {
            binding.bottomNav.visibility = View.VISIBLE
        }

        fragmentTransaction.commit()
    }

    override fun onBackPressed() {
        val currentFragment = supportFragmentManager.findFragmentById(R.id.frame)

        if (currentFragment is HomeFragment) {
            if (backPressedTime + 2000 > System.currentTimeMillis()) {
                super.onBackPressed()
                finishAffinity()  // **Keluar dari aplikasi sepenuhnya**
            } else {
                Toast.makeText(this, "Press back again to exit", Toast.LENGTH_SHORT).show()
            }
            backPressedTime = System.currentTimeMillis()
        } else {
            super.onBackPressed()
        }
    }
}