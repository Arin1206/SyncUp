package com.example.syncup.otp

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.syncup.R
import com.example.syncup.databinding.ActivityOtpDoctorBinding
import com.example.syncup.main.MainDoctorActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthProvider
import com.google.android.gms.auth.api.phone.SmsRetriever
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.os.Build
import com.google.firebase.FirebaseException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import java.util.concurrent.TimeUnit

class OtpDoctorActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOtpDoctorBinding
    private lateinit var auth: FirebaseAuth
    private var verificationId: String? = null
    private var otpReceiver: OtpRetriever? = null
    // Tambahkan variabel di atas
    private var autoCredential: PhoneAuthCredential? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Mengaktifkan View Binding
        binding = ActivityOtpDoctorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()
        verificationId = intent.getStringExtra("verificationId")

        // Setup OTP auto-move input
        setupOtpInputs()

        // Start SMS Retriever API for auto OTP retrieval
        startAutoOtpReceiver()

        // Set navigation bar color
        window.navigationBarColor = getColor(R.color.black)

        // Hide keyboard when clicking outside input fields
        hideKeyboardWhenClickedOutside()

        // Resend OTP on button click
        binding.login.setOnClickListener {
            resendOtp()
        }

        // Verify OTP on button click
        binding.customTextView.setOnClickListener {
            if (autoCredential != null) {
                // Gunakan credential auto-verifikasi
                auth.signInWithCredential(autoCredential!!)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            Toast.makeText(this, "OTP Verified (Auto)!", Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this, MainDoctorActivity::class.java))
                            finish()
                        } else {
                            Toast.makeText(this, "Auto verification failed.", Toast.LENGTH_SHORT).show()
                        }
                    }
            } else {
                val otpCode = getOtpCode()
                if (otpCode.length == 6 && verificationId != null) {
                    verifyOtp(otpCode)
                } else {
                    Toast.makeText(this, "Please enter a valid OTP", Toast.LENGTH_SHORT).show()
                }
            }
        }

    }

    private fun startAutoOtpReceiver() {
        otpReceiver = OtpRetriever().also { receiver ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(otpReceiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION), Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(otpReceiver, IntentFilter(SmsRetriever.SMS_RETRIEVED_ACTION))
            }
            receiver.initListener(object : OtpRetriever.OtpReceiverListener {
                override fun onOtpSuccess(otp: String) {
                    // Set OTP in text fields automatically
                    binding.otp1.setText(otp[0].toString())
                    binding.otp2.setText(otp[1].toString())
                    binding.otp3.setText(otp[2].toString())
                    binding.otp4.setText(otp[3].toString())
                    binding.otp5.setText(otp[4].toString())
                    binding.otp6.setText(otp[5].toString())
                }

                override fun onOtpTimeout() {
                    Toast.makeText(this@OtpDoctorActivity, "Failed to retrieve OTP.", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    private fun setupOtpInputs() {
        val otpFields = listOf(
            binding.otp1, binding.otp2, binding.otp3,
            binding.otp4, binding.otp5, binding.otp6
        )

        for (i in otpFields.indices) {
            otpFields[i].addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    if (s?.length == 1 && i < otpFields.size - 1) {
                        otpFields[i + 1].requestFocus() // Move focus to the next OTP field
                    } else if (s?.isEmpty() == true && i > 0) {
                        otpFields[i - 1].requestFocus() // Move focus back to previous field
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

    // Resend OTP
    private fun resendOtp() {
        val phoneNumber = intent.getStringExtra("phoneNumber")
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber!!)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Simpan credential untuk digunakan nanti saat user klik tombol
                    autoCredential = credential

                    // Jika ada SMS code, isi otomatis field OTP (optional)
                    val smsCode = credential.smsCode
                    if (!smsCode.isNullOrEmpty() && smsCode.length == 6) {
                        binding.otp1.setText(smsCode[0].toString())
                        binding.otp2.setText(smsCode[1].toString())
                        binding.otp3.setText(smsCode[2].toString())
                        binding.otp4.setText(smsCode[3].toString())
                        binding.otp5.setText(smsCode[4].toString())
                        binding.otp6.setText(smsCode[5].toString())
                    }

                    // Jangan panggil signInWithCredential di sini!!!
                    // Tunggu user klik tombol
                }


                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(this@OtpDoctorActivity, e.localizedMessage, Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@OtpDoctorActivity.verificationId = verificationId
                    Toast.makeText(this@OtpDoctorActivity, "OTP Resent!", Toast.LENGTH_SHORT).show()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    // Unregister the OTP receiver to avoid memory leaks
    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(otpReceiver)
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
            e.printStackTrace()
        }
    }

}
