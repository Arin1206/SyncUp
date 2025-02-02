package com.example.syncup.register

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.syncup.R
import com.example.syncup.databinding.ActivitySignUpPatientBinding
import com.example.syncup.welcome.WelcomeActivity
import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class SignUpPatientActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpPatientBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpPatientBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()

        setupGoogleSignIn()
        setupGenderSpinner()
        hideKeyboardWhenClickedOutside()

        window.navigationBarColor = getColor(R.color.black)

        // **Navigasi kembali**
        binding.arrow.setOnClickListener {
            startActivity(Intent(this, WelcomeActivity::class.java))
        }

        // **Submit Data ke Firestore ketika `customTextView` diklik**
        binding.customTextView.setOnClickListener {
            registerWithFirestore()
        }

        // **Sign Up dengan Google**
        binding.customgoogle.setOnClickListener {
            signInWithGoogle()
        }

//        // **Logout Button (Tambahan)**
//        binding.logoutbutton.setOnClickListener {
//            logoutUser()
//        }
    }

    private fun setupGenderSpinner() {
        val genderOptions = arrayOf("Select Gender", "Male", "Female")

        val spinnerAdapter = object : ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, genderOptions) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 16f
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(Color.WHITE)
                view.textSize = 16f
                view.setPadding(20, 20, 20, 20)
                return view
            }
        }

        binding.genderSpinner.adapter = spinnerAdapter
    }
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("652599682808-b41jbc9uolpnssgmptijkbufgbo1vvrd.apps.googleusercontent.com")
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
    }

    private fun registerWithFirestore() {
        val fullName = binding.edRegisFullname.text.toString().trim()
        val phoneNumber = binding.edRegisPhone.text.toString().trim()
        val age = binding.edRegisAge.text.toString().trim()
        val gender = binding.genderSpinner.selectedItem.toString()

        // **Validasi: Full Name dan Phone Number harus diisi**
        if (fullName.isEmpty()) {
            binding.edRegisFullname.error = "Full Name is required"
            binding.edRegisFullname.requestFocus()
            return
        }

        if (phoneNumber.isEmpty()) {
            binding.edRegisPhone.error = "Phone Number is required"
            binding.edRegisPhone.requestFocus()
            return
        }

        // **Nonaktifkan tombol untuk mencegah klik ganda**
        binding.customTextView.isEnabled = false
        binding.customTextView.text = "Checking phone number..."

        // **Cek apakah nomor telepon sudah ada di Firestore**
        db.collection("users_patient_phonenumber")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // **Jika nomor telepon sudah ada, beri pesan error**
                    Toast.makeText(this, "Phone number already registered!", Toast.LENGTH_SHORT).show()
                    binding.edRegisPhone.error = "Phone number already in use"
                    binding.edRegisPhone.requestFocus()

                    // **Aktifkan kembali tombol karena registrasi gagal**
                    binding.customTextView.isEnabled = true
                    binding.customTextView.text = "Continue"
                } else {
                    // **Jika nomor telepon belum digunakan, lanjutkan registrasi**
                    registerNewUser(fullName, phoneNumber, age, gender)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking phone number!", Toast.LENGTH_SHORT).show()
                binding.customTextView.isEnabled = true
                binding.customTextView.text = "Continue"
            }
    }

    private fun registerNewUser(fullName: String, phoneNumber: String, age: String, gender: String) {
        // **Generate user ID acak**
        val userId = UUID.randomUUID().toString()

        val userData = hashMapOf(
            "userId" to userId,
            "fullName" to fullName,
            "phoneNumber" to phoneNumber,
            "age" to if (age.isNotEmpty()) age else "N/A",
            "gender" to if (gender != "Select Gender") gender else "N/A"
        )

        db.collection("users_patient_phonenumber").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "User Registered Successfully!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving data!", Toast.LENGTH_SHORT).show()
                binding.customTextView.isEnabled = true
                binding.customTextView.text = "Continue"
            }
    }


    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(this, "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInWithGoogle() {
        val signInIntent = googleSignInClient.signInIntent
        googleSignInLauncher.launch(signInIntent)
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val userId = user?.uid ?: ""
                    val email = user?.email ?: ""

                    db.collection("users_patient_email").whereEqualTo("email", email).get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                Toast.makeText(this, "Welcome back!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(this, WelcomeActivity::class.java))
                                finish()
                            } else {
                                if (email.isNotEmpty()) {
                                    val userData = hashMapOf(
                                        "userId" to userId,
                                        "fullName" to (user?.displayName ?: "Google User"),
                                        "email" to email,
                                        "age" to "N/A",
                                        "gender" to "N/A"
                                    )
                                    db.collection("users_patient_email").document(userId)
                                        .set(userData)
                                        .addOnSuccessListener {
                                            Toast.makeText(this, "Google Sign-In Successful", Toast.LENGTH_SHORT).show()
                                            startActivity(Intent(this, WelcomeActivity::class.java))
                                            finish()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(this, "Failed to save Google user data", Toast.LENGTH_SHORT).show()
                                        }
                                } else {
                                    Toast.makeText(this, "Google account not found, please register.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, "Error checking user data", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(this, "Google Authentication Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

//    private fun logoutUser() {
//        auth.signOut()  // Logout dari Firebase Authentication
//        googleSignInClient.signOut().addOnCompleteListener {
//            Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
//            val intent = Intent(this, SignUpPatientActivity::class.java)
//            startActivity(intent)
//            finish()
//        }
//    }

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
