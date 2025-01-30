package com.example.syncup

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import com.example.syncup.otp.OtpDoctorActivity
import com.example.syncup.register.SignUpDoctorActivity

class DoctorLoginFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_doctor_login, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Set listener untuk menyembunyikan keyboard ketika area non-input ditekan
        setHideKeyboardListener(view)

        val signUpTextView = view.findViewById<View>(R.id.textView4)
        signUpTextView.setOnClickListener {
            val intent = Intent(requireContext(), SignUpDoctorActivity::class.java)
            startActivity(intent)
        }
        val otpTextView = view.findViewById<View>(R.id.customTextView2)
        otpTextView.setOnClickListener {
            val intent = Intent(requireContext(), OtpDoctorActivity::class.java)
            startActivity(intent)
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
