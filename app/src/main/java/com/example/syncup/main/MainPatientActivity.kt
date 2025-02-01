package com.example.syncup.main

import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.databinding.ActivityMainPatientBinding
import com.example.syncup.home.HomeFragment


class MainPatientActivity : AppCompatActivity() {
    internal lateinit var binding: ActivityMainPatientBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainPatientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableFullscreen()

        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNav) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, 0, 0, systemBars.bottom) // Menambahkan padding bawah sesuai tinggi navigation bar HP
            insets
        }
        replaceFragment(HomeFragment())
        binding.bottomNav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.homepage -> replaceFragment(HomeFragment())
                else -> {}
            }
            true
        }
    }


    private fun enableFullscreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
        actionBar?.hide()
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
            finish()
        } else {
            super.onBackPressed()
        }
    }


}