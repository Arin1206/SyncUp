package com.example.syncup.faq

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.example.syncup.home.HomeFragment
import com.example.syncup.inbox.InboxPatientFragment
import com.example.syncup.model.MessageRequest
import com.example.syncup.network.FirebaseFunctionService
import com.google.firebase.auth.FirebaseAuth
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
    val currentUser = FirebaseAuth.getInstance().currentUser


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_faq, container, false)

        emailEditText = view.findViewById(R.id.ed_regis_fullname)
        messageEditText = view.findViewById(R.id.message_field)
        submitButton = view.findViewById(R.id.submit_button)
        parentLayout = view.findViewById(R.id.main)
        val arrow = view.findViewById<ImageView>(R.id.arrow)

        // Dismiss keyboard when clicking outside
        parentLayout.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                hideKeyboard()
            }
            false
        }

        arrow.setOnClickListener {
            // Check if we're currently in the HomeFragment and navigate back to HomeFragment
            val fragment = HomeFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame, fragment)  // Assuming 'frame' is your container ID
                .addToBackStack(null)  // Optionally add the transaction to back stack if you want to allow back navigation
                .commit()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            // Navigate to HomeFragment when back is pressed
            val homeFragment = HomeFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.frame,
                    homeFragment
                )  // Ensure 'frame' is the container ID for fragments
                .commit()
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
        val imm =
            requireActivity().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view?.windowToken, 0)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "message_channel_id"
            val channelName = "Message Notifications"
            val channelDescription = "Notifications for messages sent"
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, channelName, importance)
            channel.description = channelDescription

            val notificationManager =
                requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        createNotificationChannel() // Panggil ini dulu

        val notificationManager =
            requireContext().getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "message_channel_id"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (requireContext().checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1001)
                return
            }
        }

        val notification = NotificationCompat.Builder(requireContext(), channelId)
            .setContentTitle("Message Sent")
            .setContentText("Your message has been sent successfully.")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .build()

        Log.d("NotifDebug", "showNotification dipanggil")
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun sendMessage() {
        val email = emailEditText.text.toString().trim()
        val message = messageEditText.text.toString().trim()

        val userId = currentUser?.uid ?: ""
        if (email.isEmpty() || message.isEmpty()) {
            Toast.makeText(
                requireContext(),
                "Email and message cannot be empty.",
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val messageRequest = MessageRequest(email, message, userId)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = apiService.sendMessage(messageRequest)
                if (isAdded) {
                    activity?.runOnUiThread {
                        if (response.isSuccessful) {
                            Toast.makeText(
                                requireContext(),
                                "Message sent successfully!",
                                Toast.LENGTH_SHORT
                            ).show()
                            emailEditText.text.clear()
                            messageEditText.text.clear()
                            showNotification()

                            parentFragmentManager.beginTransaction()
                                .replace(R.id.frame, InboxPatientFragment())
                                .addToBackStack(null)
                                .commit()
                        } else {
                            Toast.makeText(
                                requireContext(),
                                "Failed to send message.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            } catch (e: Exception) {
                if (isAdded) {
                    activity?.runOnUiThread {
                        Toast.makeText(requireContext(), "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

    }
}
