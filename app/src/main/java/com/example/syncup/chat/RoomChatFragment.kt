package com.example.syncup.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout // Use RelativeLayout here
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.example.syncup.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.firestore.FirebaseFirestore

class RoomChatFragment : Fragment() {

    private lateinit var doctorNameTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var doctorName: String
    private lateinit var doctorPhoneNumber: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_room_chat, container, false)

        doctorNameTextView = view.findViewById(R.id.doctor_name)

        // Get doctor name and phone number from arguments
        doctorName = arguments?.getString("doctor_name") ?: "Unknown"
        doctorPhoneNumber = arguments?.getString("doctor_phone_number") ?: "Unknown" // Get phone number

        // Set doctor name
        doctorNameTextView.text = doctorName

        // Set the status bar color to purple dark
        activity?.window?.statusBarColor = resources.getColor(R.color.purple_dark, null)

        // Load chat message
        loadChatMessage()

        // Hide the bottom navigation bar (RelativeLayout containing the bottom navigation)
        val bottomNavLayout = activity?.findViewById<RelativeLayout>(R.id.bottom_navigation) // Cast to RelativeLayout
        bottomNavLayout?.visibility = View.GONE

        return view
    }

    private fun loadChatMessage() {
        val db = FirebaseFirestore.getInstance()

        db.collection("messages")
            .whereEqualTo("doctorName", doctorName)  // Filter chat by doctor name
            .get()
            .addOnSuccessListener { result ->
                if (!result.isEmpty) {
                    val message = result.first().getString("message") ?: "No message"
                    // Set the message to TextView
                    messageTextView.text = message
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        // Reset the status bar color to default when navigating back
        activity?.window?.statusBarColor = resources.getColor(android.R.color.transparent, null)

        // Show Bottom Navigation when fragment is destroyed or popped
        val bottomNavLayout = activity?.findViewById<RelativeLayout>(R.id.bottom_navigation) // Cast to RelativeLayout
        bottomNavLayout?.visibility = View.VISIBLE
    }
}
