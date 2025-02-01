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
    }

    private fun registerWithFirestore() {
        val fullName = binding.edRegisFullname.text.toString().trim()
        val phoneNumber = binding.edRegisPhone.text.toString().trim()
        val age = binding.edRegisAge.text.toString().trim()
        val gender = binding.genderSpinner.selectedItem.toString()

        // **Validasi input sebelum mengirim ke Firestore**
        if (fullName.isEmpty() || phoneNumber.isEmpty() || age.isEmpty() || gender == "Select Gender") {
            Toast.makeText(this, "Please fill in all fields", Toast.LENGTH_SHORT).show()
            return
        }

        // **Nonaktifkan tombol untuk mencegah klik ganda**
        binding.customTextView.isEnabled = false
        binding.customTextView.text = "Registering..."

        // **Gunakan nomor telepon sebagai userId unik**
        val userId = phoneNumber

        val userData = hashMapOf(
            "userId" to userId,
            "fullName" to fullName,
            "phoneNumber" to phoneNumber,
            "age" to age,
            "gender" to gender
        )

        db.collection("users").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "User Registered Successfully!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(this, WelcomeActivity::class.java))
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving data!", Toast.LENGTH_SHORT).show()
                // **Aktifkan kembali tombol jika terjadi error**
                binding.customTextView.isEnabled = true
                binding.customTextView.text = "Continue"
            }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("652599682808-b41jbc9uolpnssgmptijkbufgbo1vvrd.apps.googleusercontent.com")
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)
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

                    val userData = hashMapOf(
                        "userId" to userId,
                        "fullName" to (user?.displayName ?: "Google User"),
                        "email" to (user?.email ?: ""),
                        "age" to "N/A",
                        "gender" to "N/A"
                    )

                    db.collection("users").document(userId)
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
                    Toast.makeText(this, "Google Authentication Failed", Toast.LENGTH_SHORT).show()
                }
            }
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
