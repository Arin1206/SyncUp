package com.example.syncup

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import com.example.syncup.main.MainDoctorActivity
import com.example.syncup.otp.OtpDoctorActivity
import com.example.syncup.otp.OtpPatientActivity
import com.example.syncup.register.SignUpDoctorActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class DoctorLoginFragment : Fragment() {
    private lateinit var auth: FirebaseAuth
    private val db = FirebaseFirestore.getInstance()
    private var verificationId: String? = null
    private lateinit var googleSignInClient: GoogleSignInClient
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_doctor_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        auth = FirebaseAuth.getInstance()
        // Set listener untuk menyembunyikan keyboard ketika area non-input ditekan
        setHideKeyboardListener(view)

        val signUpTextView = view.findViewById<View>(R.id.textView4)
        signUpTextView.setOnClickListener {
            val intent = Intent(requireContext(), SignUpDoctorActivity::class.java)
            startActivity(intent)
        }
        val otpTextView = view.findViewById<View>(R.id.customTextView2)
        otpTextView.setOnClickListener {
            val phoneEditText = view.findViewById<EditText>(R.id.ed_regis_phone)
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

    private fun checkPhoneNumberInFirestore(phoneNumber: String) {
        if (!isAdded) {
            // Fragment is not attached to the activity, return early
            return
        }

        val context = activity?.applicationContext ?: return

        try {
            db.collection("users_doctor_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        sendOTP(phoneNumber,requireActivity())
                    } else {
                        Toast.makeText(context, "Phone number or STR not registered", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(context, "Error checking phone number: ${e.message}", Toast.LENGTH_SHORT).show()
                    e.printStackTrace() // Debugging error
                }
        } catch (e: Exception) {
            Toast.makeText(context, "Unexpected error: ${e.message}", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }



    private fun sendOTP(phoneNumber: String, activity: Activity) {
        var formattedPhoneNumber = phoneNumber.trim()

        if (formattedPhoneNumber.startsWith("0")) {
            formattedPhoneNumber = "+62" + formattedPhoneNumber.substring(1)
        }

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity) // gunakan dari parameter, bukan requireActivity()
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {}

                override fun onVerificationFailed(e: FirebaseException) {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
                    this@DoctorLoginFragment.verificationId = verificationId
                    val intent = Intent(requireContext(), OtpDoctorActivity::class.java)
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
                    startActivity(Intent(requireContext(), OtpDoctorActivity::class.java))
                    requireActivity().finish()
                } else {
                    Toast.makeText(requireContext(), "OTP Verification Failed!", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupGoogleSignIn() {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("652599682808-b41jbc9uolpnssgmptijkbufgbo1vvrd.apps.googleusercontent.com")
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(requireContext(), gso)
    }
    private fun signInWithGoogle() {
        googleSignInClient.signOut().addOnCompleteListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }
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

    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    val email = user?.email ?: ""

                    // **Cek apakah email sudah ada di Firestore**
                    db.collection("users_doctor_email")
                        .whereEqualTo("email", email)
                        .get()
                        .addOnSuccessListener { documents ->
                            if (!documents.isEmpty) {
                                // **Jika email ditemukan, masuk ke MainDoctorActivity**
                                Toast.makeText(requireContext(), "Welcome back, Doctor!", Toast.LENGTH_SHORT).show()
                                startActivity(Intent(requireContext(), MainDoctorActivity::class.java))
                                requireActivity().finish()
                            } else {
                                // **Jika email tidak ada, hapus akun Firebase sebelum logout**
                                user?.delete()?.addOnCompleteListener {
                                    auth.signOut()
                                    googleSignInClient.signOut()
                                    Toast.makeText(requireContext(), "Email not registered. Please sign up first.", Toast.LENGTH_LONG).show()
                                }
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
            view.clearFocus() // Hapus fokus agar input tidak aktif lagi
        }
    }
}
