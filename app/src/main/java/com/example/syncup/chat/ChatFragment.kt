package com.example.syncup.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Locale

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
        chatAdapter = ChatAdapter(chatList) { doctorName, doctorPhoneNumber, doctorUid ->
            // Trigger navigation to RoomChatFragment when an item is clicked
            navigateToRoomChat(doctorName, doctorPhoneNumber, doctorUid)
        }
        recyclerViewChats.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewChats.adapter = chatAdapter

        // Fetch doctor data from Firestore
        fetchDoctorsData()

        return view
    }

    private fun fetchDoctorsData() {
        val db = FirebaseFirestore.getInstance()
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        chatList.clear()

        val emailCollection = db.collection("users_doctor_email").get()
        val phoneCollection = db.collection("users_doctor_phonenumber").get()

        emailCollection.addOnSuccessListener { emailResult ->
            phoneCollection.addOnSuccessListener { phoneResult ->

                val allDocs = emailResult.documents + phoneResult.documents

                for (document in allDocs) {
                    val doctorName = document.getString("fullName") ?: "Unknown"
                    val doctorEmail = document.getString("email") ?: "No Email"
                    val doctorPhone = document.getString("phoneNumber") ?: "No Phone"
                    val doctorUid = document.id

                    // Fetch messages from doctor and user
                    val messagesFromDoctorRef = db.collection("messages").document(doctorUid)
                    val messagesFromUserRef = db.collection("messages").document(currentUserUid)

                    messagesFromDoctorRef.addSnapshotListener { snapshotDoctor, _ ->
                        val messagesFromDoctor = snapshotDoctor?.get("messages") as? List<Map<String, Any>> ?: emptyList()

                        messagesFromUserRef.addSnapshotListener { snapshotUser, _ ->
                            val messagesFromUser = snapshotUser?.get("messages") as? List<Map<String, Any>> ?: emptyList()

                            // Combine messages from doctor and user
                            val combined = (messagesFromDoctor + messagesFromUser)

                            // If no messages exist, only show doctors
                            if (combined.isEmpty()) {
                                val chat = Chat(
                                    doctorName = doctorName,
                                    message = "Start Message Now",  // Placeholder message
                                    date = "",  // No date
                                    doctorEmail = doctorEmail,
                                    doctorPhoneNumber = doctorPhone,
                                    doctorUid = doctorUid,
                                    unreadCount = 0,
                                    isUnread = false
                                )
                                // Add doctor with placeholder message to chatList
                                val index = chatList.indexOfFirst { it.doctorUid == doctorUid }
                                if (index != -1) {
                                    chatList[index] = chat
                                } else {
                                    chatList.add(chat)
                                }

                                chatAdapter.notifyDataSetChanged()
                                return@addSnapshotListener // Skip further processing since no messages
                            }

                            // If there are messages, process the latest one
                            val latest = combined.maxByOrNull {
                                val ts = it["timestamp"] as? String
                                // Handle missing or empty timestamp
                                if (ts.isNullOrEmpty()) {
                                    return@maxByOrNull 0L
                                }
                                try {
                                    val format = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                                    format.parse(ts)?.time ?: 0L
                                } catch (e: Exception) {
                                    0L
                                }
                            }

                            val latestMsgText = latest?.get("message")?.toString() ?: "Start Message Now"
                            val latestMsgDate = latest?.get("timestamp")?.toString() ?: ""
                            val senderUid = latest?.get("senderUid")?.toString()?.takeIf { it != "null" } ?: ""

                            // Count unread messages
                            val unreadCount = messagesFromDoctor.count {
                                val sid = it["senderUid"]?.toString()?.trim() ?: ""
                                sid.isEmpty() // sender is empty => message from doctor
                            }

                            // Determine if the message is unread
                            val isUnread = senderUid.isEmpty() // Check if it's from the doctor (if senderUid is empty)

                            val chat = Chat(
                                doctorName = doctorName,
                                message = latestMsgText,
                                date = latestMsgDate,
                                doctorEmail = doctorEmail,
                                doctorPhoneNumber = doctorPhone,
                                doctorUid = doctorUid,
                                unreadCount = unreadCount,
                                isUnread = isUnread
                            )

                            val index = chatList.indexOfFirst { it.doctorUid == doctorUid }
                            if (index != -1) {
                                chatList[index] = chat
                            } else {
                                chatList.add(chat)
                            }

                            // Sort by latest message time
                            chatList.sortByDescending {
                                SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(it.date)?.time ?: 0L
                            }

                            chatAdapter.notifyDataSetChanged()
                        }
                    }
                }
            }
        }
    }



    private fun navigateToRoomChat(doctorName: String, doctorPhoneNumber: String, doctorUid: String) {
        val bundle = Bundle()
        bundle.putString("doctor_name", doctorName)
        bundle.putString("doctor_phone_number", doctorPhoneNumber) // Pass the phone number here
        bundle.putString("receiverUid", doctorUid)  // Pass the doctor's UID to the RoomChatFragment

        val roomChatFragment = RoomChatFragment()
        roomChatFragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, roomChatFragment)
            .addToBackStack(null)
            .commit()
    }

}
