package com.example.syncup.register

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.syncup.R
import com.example.syncup.databinding.ActivitySignUpPatientBinding
import com.example.syncup.welcome.WelcomeActivity

class SignUpPatientActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpPatientBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivitySignUpPatientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Setting padding for system bars
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Set navigation bar color
        window.navigationBarColor = getColor(R.color.black)

        // Set up Intent to WelcomeActivity on arrow click
        binding.arrow.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("fragment", "patient")
            startActivity(intent)
        }
    }
}
