package com.example.syncup.chat

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.google.firebase.firestore.FirebaseFirestore

class ChatFragment : Fragment() {

    private lateinit var recyclerViewChats: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        recyclerViewChats = view.findViewById(R.id.recyclerViewChats)
        chatAdapter = ChatAdapter(chatList) { doctorName, doctorPhoneNumber ->
            // Trigger navigation to RoomChatFragment when an item is clicked
            navigateToRoomChat(doctorName, doctorPhoneNumber)
        }
        recyclerViewChats.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewChats.adapter = chatAdapter

        // Fetch doctor data from Firestore
        fetchDoctorsData()

        return view
    }

    private fun fetchDoctorsData() {
        val db = FirebaseFirestore.getInstance()

        chatList.clear()  // <-- Tambahkan ini agar tidak terjadi duplikat

        // Fetch dari 'users_doctor_email'
        db.collection("users_doctor_email")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val doctorName = document.getString("fullName") ?: "Unknown"
                    val doctorEmail = document.getString("email") ?: "No Email"
                    val doctorPhoneNumber = document.getString("phoneNumber") ?: "No Phone"

                    chatList.add(Chat(doctorName, "Start Message Now", "12-01-24", doctorEmail, doctorPhoneNumber))
                }
                chatAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                // Handle error
            }

        // Fetch dari 'users_doctor_phonenumber'
        db.collection("users_doctor_phonenumber")
            .get()
            .addOnSuccessListener { result ->
                for (document in result) {
                    val doctorName = document.getString("fullName") ?: "Unknown"
                    val doctorEmail = document.getString("email") ?: "No Email"
                    val doctorPhoneNumber = document.getString("phoneNumber") ?: "No Phone"

                    chatList.add(Chat(doctorName, "Start Message Now", "12-01-24", doctorEmail, doctorPhoneNumber))
                }
                chatAdapter.notifyDataSetChanged()
            }
            .addOnFailureListener { exception ->
                // Handle error
            }
    }

    private fun navigateToRoomChat(doctorName: String, doctorPhoneNumber: String) {
        val bundle = Bundle()
        bundle.putString("doctor_name", doctorName)
        bundle.putString("doctor_phone_number", doctorPhoneNumber) // Pass the phone number here

        val roomChatFragment = RoomChatFragment()
        roomChatFragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, roomChatFragment)
            .addToBackStack(null)
            .commit()
    }

}
