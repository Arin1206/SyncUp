package com.example.syncup.register

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Spinner
import android.widget.TextView
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
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom)
            insets
        }

        // Set navigation bar color
        window.navigationBarColor = getColor(R.color.black)

        // Hide keyboard when clicking outside input fields
        hideKeyboardWhenClickedOutside()

        // Set up Intent to WelcomeActivity on arrow click
        binding.arrow.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("fragment", "patient")
            startActivity(intent)
        }
        binding.login.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("fragment", "patient")
            startActivity(intent)
        }


        val genderSpinner: Spinner = findViewById(R.id.genderSpinner)
        val genderOptions = arrayOf("Select Gender", "Male", "Female")

        val spinnerAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, genderOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE) // Warna teks Spinner putih
                view.textSize = 16f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE) // Warna teks dropdown putih
                view.textSize = 16f
                view.setPadding(20, 20, 20, 20) // Memberikan ruang di dropdown
                return view
            }
        }

        genderSpinner.adapter = spinnerAdapter
    }

    // Fungsi untuk menyembunyikan keyboard saat klik di luar input field
    private fun hideKeyboardWhenClickedOutside() {
        binding.main.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    // Fungsi untuk menyembunyikan keyboard
    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = currentFocus
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
}
