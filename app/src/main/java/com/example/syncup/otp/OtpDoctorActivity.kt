package com.example.syncup.otp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.syncup.R
import com.example.syncup.databinding.ActivityOtpDoctorBinding
import com.example.syncup.main.MainDoctorActivity
import com.example.syncup.main.MainPatientActivity
import com.example.syncup.welcome.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider

class OtpDoctorActivity : AppCompatActivity() {
    private lateinit var binding: ActivityOtpDoctorBinding
    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Mengaktifkan View Binding
        binding = ActivityOtpDoctorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        verificationId = intent.getStringExtra("verificationId")
        val phoneNumber = intent.getStringExtra("phoneNumber")
        // Setup OTP auto-move inpu
        setupOtpInputs()

        // Set navigation bar color
        window.navigationBarColor = getColor(R.color.black)

        // Hide keyboard when clicking outside input fields
        hideKeyboardWhenClickedOutside()
        // Mengatur padding untuk menyesuaikan dengan insets sistem
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, 0, systemBars.right, systemBars.bottom) // Hilangkan padding atas
            insets
        }
        binding.arrow.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("fragment", "doctor")
            startActivity(intent)
        }
        binding.customTextView.setOnClickListener {
            val otpCode = getOtpCode()
            if (otpCode.length == 6 && verificationId != null) {
                verifyOtp(otpCode)
            } else {
                Toast.makeText(this, "Please enter a valid OTP", Toast.LENGTH_SHORT).show()
            }
        }

    }

//    private fun hideStatusBar() {
//        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
//            window.insetsController?.hide(WindowInsets.Type.statusBars())
//            window.insetsController?.systemBarsBehavior =
//                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
//        } else {
//            @Suppress("DEPRECATION")
//            window.decorView.systemUiVisibility = (
//                    android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
//                            or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
//                            or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
//                            or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
//                    )
//        }
//    }

    private fun setupOtpInputs() {
        val otpFields = listOf(
            binding.otp1, binding.otp2, binding.otp3,
            binding.otp4, binding.otp5, binding.otp6
        )

        for (i in otpFields.indices) {
            otpFields[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1) {
                        if (i < otpFields.size - 1) {
                            otpFields[i + 1].requestFocus()
                        }
                    } else if (s?.isEmpty() == true) {
                        if (i > 0) {
                            otpFields[i - 1].requestFocus()
                        }
                    }
                }

                override fun afterTextChanged(s: Editable?) {}
            })
        }
    }

    private fun getOtpCode(): String {
        return binding.otp1.text.toString().trim() +
                binding.otp2.text.toString().trim() +
                binding.otp3.text.toString().trim() +
                binding.otp4.text.toString().trim() +
                binding.otp5.text.toString().trim() +
                binding.otp6.text.toString().trim()
    }

    private fun verifyOtp(code: String) {
        val credential = PhoneAuthProvider.getCredential(verificationId!!, code)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "OTP Verified!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this, MainDoctorActivity::class.java))
                    finish()
                } else {
                    Toast.makeText(this, "Invalid OTP. Please try again.", Toast.LENGTH_SHORT).show()
                }
            }
    }



    private fun hideKeyboardWhenClickedOutside() {
        binding.main.setOnTouchListener { _, event ->
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
