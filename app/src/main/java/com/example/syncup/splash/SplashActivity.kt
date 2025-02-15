package com.example.syncup.splash

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.syncup.R
import com.example.syncup.main.MainDoctorActivity
import com.example.syncup.main.MainPatientActivity
import com.example.syncup.welcome.WelcomeActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@SuppressLint("CustomSplashScreen")
class SplashActivity : AppCompatActivity() {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        // **Fullscreen splash**
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            val controller = window.insetsController
            controller?.let {
                it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                it.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                            View.SYSTEM_UI_FLAG_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    )
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // **Cek autentikasi pengguna setelah splash**
        Handler(Looper.getMainLooper()).postDelayed({
            checkUserAuthentication()
        }, 2000)
    }
    private fun checkUserAuthentication() {
        val currentUser = auth.currentUser

        if (currentUser != null) {
            val userEmail = currentUser.email
            var userPhoneNumber = currentUser.phoneNumber

            Log.d("UserCheck", "Checking authentication for: email=$userEmail, phone=$userPhoneNumber")

            if (!userPhoneNumber.isNullOrEmpty()) {
                // **Format phoneNumber: Ubah "+62" ke "0" agar sesuai Firestore**
                if (userPhoneNumber.startsWith("+62")) {
                    userPhoneNumber = "0" + userPhoneNumber.substring(3)
                }
                checkUserByPhone(userPhoneNumber)
            } else if (!userEmail.isNullOrEmpty()) {
                checkUserByEmail(userEmail)
            } else {
                logoutAndRedirect()
            }
        } else {
            // **Jika belum login, masuk ke WelcomeActivity**
            logoutAndRedirect()
        }
    }
    private fun checkUserByPhone(userPhoneNumber: String) {
        val db = FirebaseFirestore.getInstance()

        val patientQuery = db.collection("users_patient_phonenumber")
            .whereEqualTo("phoneNumber", userPhoneNumber)
            .get()

        val doctorQuery = db.collection("users_doctor_phonenumber")
            .whereEqualTo("phoneNumber", userPhoneNumber)
            .get()

        // **Jalankan kedua query Firestore secara paralel**
        patientQuery.addOnSuccessListener { patientDocs ->
            doctorQuery.addOnSuccessListener { doctorDocs ->
                when {
                    !doctorDocs.isEmpty -> {
                        // **Jika nomor ditemukan di koleksi dokter, arahkan ke halaman dokter**
                        Log.d("UserCheck", "User found in users_doctor_phonenumber")
                        navigateToMainDoctor()
                    }
                    !patientDocs.isEmpty -> {
                        // **Jika nomor ditemukan di koleksi pasien, arahkan ke halaman pasien**
                        Log.d("UserCheck", "User found in users_patient_phonenumber")
                        navigateToMain()
                    }
                    else -> {
                        // **Jika nomor tidak ditemukan di kedua koleksi, logout dan redirect**
                        Log.d("UserCheck", "Phone number not found in both collections")
                        logoutAndRedirect()
                    }
                }
            }.addOnFailureListener {
                Log.e("UserCheck", "Error checking doctor phone number in Firestore", it)
                logoutAndRedirect()
            }
        }.addOnFailureListener {
            Log.e("UserCheck", "Error checking patient phone number in Firestore", it)
            logoutAndRedirect()
        }
    }


    private fun checkUserByEmail(userEmail: String) {
        val db = FirebaseFirestore.getInstance()

        val patientQuery = db.collection("users_patient_email")
            .whereEqualTo("email", userEmail)
            .get()

        val doctorQuery = db.collection("users_doctor_email")
            .whereEqualTo("email", userEmail)
            .get()

        // **Jalankan kedua query Firestore secara paralel**
        patientQuery.addOnSuccessListener { patientDocs ->
            doctorQuery.addOnSuccessListener { doctorDocs ->
                when {
                    !doctorDocs.isEmpty -> {
                        // **Jika email ditemukan di koleksi dokter, arahkan ke halaman dokter**
                        Log.d("UserCheck", "User found in users_doctor_email")
                        navigateToMainDoctor()
                    }
                    !patientDocs.isEmpty -> {
                        // **Jika email ditemukan di koleksi pasien, arahkan ke halaman pasien**
                        Log.d("UserCheck", "User found in users_patient_email")
                        navigateToMain()
                    }
                    else -> {
                        // **Jika email tidak ditemukan di kedua koleksi, logout dan redirect**
                        Log.d("UserCheck", "Email not found in both collections")
                        logoutAndRedirect()
                    }
                }
            }.addOnFailureListener {
                Log.e("UserCheck", "Error checking doctor email in Firestore", it)
                logoutAndRedirect()
            }
        }.addOnFailureListener {
            Log.e("UserCheck", "Error checking patient email in Firestore", it)
            logoutAndRedirect()
        }
    }


    private fun navigateToMain() {
        startActivity(Intent(this, MainPatientActivity::class.java))
        finish()
    }
    private fun navigateToMainDoctor() {
        startActivity(Intent(this, MainDoctorActivity::class.java))
        finish()
    }

    private fun logoutAndRedirect() {
        auth.signOut()
        startActivity(Intent(this, WelcomeActivity::class.java))
        finish()
    }
}
