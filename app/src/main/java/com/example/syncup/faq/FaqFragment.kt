package com.example.syncup.faq

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.model.MessageRequest
import com.example.syncup.network.FirebaseFunctionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FaqFragment : Fragment() {

    private lateinit var emailEditText: EditText
    private lateinit var messageEditText: EditText
    private lateinit var submitButton: Button
    private lateinit var parentLayout: ConstraintLayout
    private lateinit var apiService: FirebaseFunctionService

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_faq, container, false)

        emailEditText = view.findViewById(R.id.ed_regis_fullname)
        messageEditText = view.findViewById(R.id.message_field)
        submitButton = view.findViewById(R.id.submit_button)
        parentLayout = view.findViewById(R.id.main)

        // Dismiss keyboard when clicking outside
        parentLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }

        // Setup Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://us-central1-sync-up-f40ee.cloudfunctions.net/") // Ganti sesuai project Firebase kamu
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiService = retrofit.create(FirebaseFunctionService::class.java)

        // Submit Button Click
        submitButton.setOnClickListener {
            sendMessage()
        }

        return view
    }

    private fun hideKeyboard() {
        val imm = requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun sendMessage() {
        val email = emailEditText.text.toString().trim()
        val message = messageEditText.text.toString().trim()

        if (email.isEmpty() || message.isEmpty()) {
            Toast.makeText(requireContext(), "Email and message cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val messageRequest = MessageRequest(email, message)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.sendMessage(messageRequest)
                requireActivity().runOnUiThread {
                    if (response.isSuccessful) {
                        Toast.makeText(requireContext(), "Message sent successfully!", Toast.LENGTH_SHORT).show()
                        emailEditText.text.clear()
                        messageEditText.text.clear()
                    } else {
                        Toast.makeText(requireContext(), "Failed to send message.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
