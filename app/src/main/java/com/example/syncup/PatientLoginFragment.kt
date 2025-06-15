package com.example.syncup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.syncup.main.MainPatientActivity
import com.example.syncup.otp.OtpPatientActivity
import com.example.syncup.register.SignUpPatientActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.FirebaseException
import com.google.firebase.auth.GoogleAuthProvider
import java.util.concurrent.TimeUnit

class PatientLoginFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var verificationId: String? = null
    private lateinit var googleSignInClient: GoogleSignInClient

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_patient_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()

        // Set listener untuk menyembunyikan keyboard ketika area non-input ditekan
        setHideKeyboardListener(view)

        val signUpTextView = view.findViewById<View>(R.id.signup)
        signUpTextView.setOnClickListener {
            val intent = Intent(requireContext(), SignUpPatientActivity::class.java)
            startActivity(intent)
        }

        val otpTextView = view.findViewById<View>(R.id.customTextView)
        otpTextView.setOnClickListener {
            val phoneEditText = view.findViewById<EditText>(R.id.ed_login_phone)
            val phoneNumber = phoneEditText.text.toString().trim()

            if (phoneNumber.isEmpty()) {
                phoneEditText.error = "Please enter your phone number"
                phoneEditText.requestFocus()
                return@setOnClickListener
            }

            checkPhoneNumberInFirestore(phoneNumber)
        }
        setupGoogleSignIn()

        val googleSignInButton = view.findViewById<View>(R.id.customgoogle)
        googleSignInButton.setOnClickListener {
            signInWithGoogle()
        }
    }
    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("652599682808-b41jbc9uolpnssgmptijkbufgbo1vvrd.apps.googleusercontent.com")
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }


    private val googleSignInLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            firebaseAuthWithGoogle(account.idToken!!)
        } catch (e: ApiException) {
            Toast.makeText(requireContext(), "Google Sign-In Failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
    }


    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val email = user?.email ?: ""

                    // **Cek apakah email sudah ada di Firestore**
                    db.collection("users_patient_email")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                // **Jika email ditemukan, masuk ke MainPatientActivity**
                                Toast.makeText(requireContext(), "Welcome back!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(requireContext(), MainPatientActivity::class.java))
                                requireActivity().finish()
                            } else {
                                // **Jika email tidak ada, tampilkan pesan**
                                auth.signOut()
                                googleSignInClient.signOut() // Logout dari Google
                                Toast.makeText(requireContext(), "Email not registered. Please sign up first.", Toast.LENGTH_LONG).show()
                            }
                        }
                        .addOnFailureListener {
                            Toast.makeText(requireContext(), "Error checking user data", Toast.LENGTH_SHORT).show()
                        }
                } else {
                    Toast.makeText(requireContext(), "Google Authentication Failed", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun checkPhoneNumberInFirestore(phoneNumber: String) {
        // Using application context to avoid IllegalStateException
        val context = activity?.applicationContext ?: return

        db.collection("users_patient_phonenumber")
            .whereEqualTo("phoneNumber", phoneNumber)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    // If phone number is registered, send OTP
                    sendOTP(phoneNumber)
                } else {
                    // If phone number is not registered, show toast
                    Toast.makeText(context, "Phone number not registered", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                Toast.makeText(context, "Error checking phone number", Toast.LENGTH_SHORT).show()
            }
    }

    private fun sendOTP(phoneNumber: String) {
        var formattedPhoneNumber = phoneNumber.trim()

        // Jika nomor dimulai dengan '0', ganti dengan '+62'
        if (formattedPhoneNumber.startsWith("0")) {
            formattedPhoneNumber = "+62" + formattedPhoneNumber.substring(1)
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(requireActivity())
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // **HAPUS BAGIAN INI supaya tidak langsung verifikasi otomatis**
                    // signInWithCredential(credential)
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    this@PatientLoginFragment.verificationId = verificationId
                    val intent = Intent(requireContext(), OtpPatientActivity::class.java)
                    intent.putExtra("phoneNumber", formattedPhoneNumber)
                    intent.putExtra("verificationId", verificationId)
                    startActivity(intent)

                    Toast.makeText(requireContext(), "OTP sent to $formattedPhoneNumber", Toast.LENGTH_SHORT).show()
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }


    private fun signInWithCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Toast.makeText(requireContext(), "OTP Verified!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(requireContext(), OtpPatientActivity::class.java))
                    requireActivity().finish()
                } else {
                    Toast.makeText(requireContext(), "OTP Verification Failed!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setHideKeyboardListener(view: View) {
        view.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }
    }

    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = requireActivity().currentFocus
        if (view != null) {
            imm.hideSoftInputFromWindow(view.windowToken, 0)
            view.clearFocus()
        }
    }
}
