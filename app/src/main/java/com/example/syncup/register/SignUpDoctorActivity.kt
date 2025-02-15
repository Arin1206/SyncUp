package com.example.syncup.register

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.syncup.R
import com.example.syncup.databinding.ActivitySignUpDoctorBinding
import com.example.syncup.main.MainDoctorActivity
import com.example.syncup.welcome.WelcomeActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.UUID

class SignUpDoctorActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySignUpDoctorBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient
    private val db = FirebaseFirestore.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpDoctorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()

        setupGoogleSignIn()
        setupGenderSpinner()
        hideKeyboardWhenClickedOutside()

        logoutUser()

        window.navigationBarColor = getColor(R.color.black)

        // **Navigasi kembali**
        binding.arrow.setOnClickListener {
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("fragment", "doctor")
            startActivity(intent)
        }

        // **Submit Data ke Firestore ketika `customTextView` diklik**
        binding.customTextView.setOnClickListener {
            registerWithFirestore()
        }

        // **Sign Up dengan Google**
        binding.customgoogle.setOnClickListener {
            signInWithGoogle()
        }

        binding.login.setOnClickListener{
            val intent = Intent(this, WelcomeActivity::class.java)
            intent.putExtra("fragment", "doctor")
            startActivity(intent)
        }
    }

    private fun logoutUser() {
        auth.signOut()
        googleSignInClient.signOut()
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

    private fun registerWithFirestore() {
        val fullName = binding.edRegisFullname.text.toString().trim()
        val phoneNumber = binding.edRegisPhone.text.toString().trim()
        val age = binding.edRegisAge.text.toString().trim()
        val gender = binding.genderSpinner.selectedItem.toString()
        val str = binding.edRegisStr.text.toString().trim()

        // **Validasi: Full Name dan Phone Number harus diisi**
        if (fullName.isEmpty()) {
            binding.edRegisFullname.error = "Full Name is required"
            binding.edRegisFullname.requestFocus()
            return
        }

        if (str.isEmpty()) {
            binding.edRegisStr.error = "STR is required"
            binding.edRegisStr.requestFocus()
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

        val db = FirebaseFirestore.getInstance()

        val patientQuery = db.collection("users_patient_phonenumber")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()

        val doctorQuery = db.collection("users_doctor_phonenumber")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()

        // **Jalankan kedua query Firestore secara paralel**
        patientQuery.addOnSuccessListener { patientDocs ->
            doctorQuery.addOnSuccessListener { doctorDocs ->
                if (!patientDocs.isEmpty || !doctorDocs.isEmpty) {
                    // **Jika nomor sudah terdaftar di salah satu koleksi**
                    Toast.makeText(this, "Phone number already registered!", Toast.LENGTH_SHORT).show()
                    binding.edRegisPhone.error = "Phone number already in use"
                    binding.edRegisPhone.requestFocus()

                    // **Aktifkan kembali tombol karena registrasi gagal**
                    binding.customTextView.isEnabled = true
                    binding.customTextView.text = "Continue"
                } else {
                    // **Jika nomor telepon belum digunakan, lanjutkan registrasi**
                    registerNewUser(fullName, phoneNumber, age, gender, str)
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Error checking doctor phone number!", Toast.LENGTH_SHORT).show()
                binding.customTextView.isEnabled = true
                binding.customTextView.text = "Continue"
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Error checking patient phone number!", Toast.LENGTH_SHORT).show()
            binding.customTextView.isEnabled = true
            binding.customTextView.text = "Continue"
        }
    }

    private fun registerNewUser(fullName: String, phoneNumber: String, age: String, gender: String, str: String) {
        // **Generate user ID acak**
        val userId = UUID.randomUUID().toString()

        val userData = hashMapOf(
            "userId" to userId,
            "fullName" to fullName,
            "phoneNumber" to phoneNumber,
            "age" to if (age.isNotEmpty()) age else "N/A",
            "gender" to if (gender != "Select Gender") gender else "N/A",
            "str" to str
        )

        db.collection("users_doctor_phonenumber").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "User Registered Successfully!", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, WelcomeActivity::class.java)
                intent.putExtra("fragment", "doctor")
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error saving data!", Toast.LENGTH_SHORT).show()
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
                    val email = user?.email ?: ""

                    // **Cek apakah email sudah ada di users_patient_email**
                    db.collection("users_patient_email").whereEqualTo("email", email).get()
                        .addOnSuccessListener { patientDocs ->
                            if (!patientDocs.isEmpty) {
                                // **Langsung tampilkan akun Google untuk dipilih ulang tanpa pesan tambahan**
                                auth.signOut()
                                googleSignInClient.signOut()
                                signInWithGoogle()
                            } else {
                                // **Jika tidak ada di users_patient_email, cek di users_doctor_email**
                                checkDoctorEmailBeforeRegister(user?.uid ?: "", email, user?.displayName)
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

    private fun checkDoctorEmailBeforeRegister(userId: String, email: String, fullName: String?) {
        db.collection("users_doctor_email").whereEqualTo("email", email).get()
            .addOnSuccessListener { doctorDocs ->
                if (!doctorDocs.isEmpty) {
                    Toast.makeText(this, "Welcome back, Doctor!", Toast.LENGTH_SHORT).show()
                    val intent = Intent(this, MainDoctorActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    registerNewDoctor(userId, email, fullName)
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, "Error checking doctor database", Toast.LENGTH_SHORT).show()
            }
    }

    private fun registerNewDoctor(userId: String, email: String, fullName: String?) {
        val userData = hashMapOf(
            "userId" to userId,
            "fullName" to (fullName ?: "Google User"),
            "email" to email,
            "age" to "N/A",
            "gender" to "N/A",
            "str" to "N/A"
        )

        db.collection("users_doctor_email").document(userId)
            .set(userData)
            .addOnSuccessListener {
                Toast.makeText(this, "Google Sign-Up Successful", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, MainDoctorActivity::class.java)
                startActivity(intent)
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, "Failed to save Google user data", Toast.LENGTH_SHORT).show()
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
