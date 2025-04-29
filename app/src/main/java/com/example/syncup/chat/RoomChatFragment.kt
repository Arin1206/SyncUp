package com.example.syncup.chat

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.SetOptions

class RoomChatFragment : Fragment() {

    private lateinit var doctorNameTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var doctorName: String
    private lateinit var doctorPhoneNumber: String
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageView
    private var receiverUid: String? = null
    private var senderUid: String? = null
    private lateinit var recyclerViewMessages: RecyclerView
    private val messageList = mutableListOf<Message>()
    private lateinit var chatAdapter: RoomChatAdapter
    private var currentUserUid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var userName: String? = null // Make this nullable to avoid accessing before initialization

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_room_chat, container, false)

        doctorNameTextView = view.findViewById(R.id.doctor_name)
        editTextMessage = view.findViewById(R.id.editTextMessage)
        buttonSend = view.findViewById(R.id.buttonSend)
        recyclerViewMessages = view.findViewById(R.id.recyclerViewMessages)

        // Get doctor name and phone number from arguments
        doctorName = arguments?.getString("doctor_name") ?: "Unknown"
        doctorPhoneNumber = arguments?.getString("doctor_phone_number") ?: "Unknown" // Get phone number
        receiverUid = arguments?.getString("receiverUid")

        // Set doctor name
        doctorNameTextView.text = doctorName

        chatAdapter = RoomChatAdapter(messageList, currentUserUid ?: "Unknown")
        recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewMessages.adapter = chatAdapter

        // Fetch logged-in user's full name using userId
        fetchUserData()

        listenForMessages()
        activity?.window?.statusBarColor = resources.getColor(R.color.purple_dark, null)

        // Set up send message button click listener
        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString()
            if (message.isNotEmpty() && userName != null) {
                sendMessageToFirestore(message)
            } else {
                // Handle case where userName is still null (not fetched yet)
                showToast("Please wait for user data to load.")
            }
        }

        // Hide Bottom Navigation when this fragment is loaded
        val bottomNavLayout = activity?.findViewById<RelativeLayout>(R.id.bottom_navigation)
        bottomNavLayout?.visibility = View.GONE

        return view
    }

    private fun formatNomorTelepon(phone: String): String {
        // Implement your phone number formatting logic here, if necessary
        return phone.replace("-", "").trim()
    }

    private fun fetchUserData() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        val userEmail = currentUser.email
        var userPhone = currentUser.phoneNumber?.let { formatNomorTelepon(it) } // Format phone if available

        Log.d("RoomChatFragment", "User Email: $userEmail | User Phone (Formatted): $userPhone")

        val query: Pair<String, String>? = when {
            !userEmail.isNullOrEmpty() -> Pair("users_patient_email", userEmail)
            !userPhone.isNullOrEmpty() -> Pair("users_patient_phonenumber", userPhone)
            else -> null
        }

        if (query == null) {
            Toast.makeText(requireContext(), "No email or phone found", Toast.LENGTH_SHORT).show()
            return
        }

        val (collection, identifier) = query
        val firestore = FirebaseFirestore.getInstance()

        firestore.collection(collection).whereEqualTo(
            if (collection == "users_patient_email") "email" else "phoneNumber", identifier
        )
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {
                    val document = documents.documents[0]
                    val userName = document.getString("fullName") ?: "Unknown Name"

                    // Set the userName for message sending or display
                    this.userName = userName

                    Log.d("RoomChatFragment", "User Data Loaded: $userName, Identifier: $identifier")
                } else {
                    Toast.makeText(requireContext(), "No user data found", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Log.e("RoomChatFragment", "Error fetching user data", e)
                Toast.makeText(requireContext(), "Failed to load user data", Toast.LENGTH_SHORT).show()
            }
    }


    private fun sendMessageToFirestore(message: String) {
        if (userName == null || receiverUid == null) {
            showToast("Please wait for user data to load.")
            return
        }

        val db = FirebaseFirestore.getInstance()
        val timestamp = System.currentTimeMillis()

        // Format the timestamp to "dd MMM yyyy, HH:mm:ss" using SimpleDateFormat
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(java.util.Date(timestamp))

        val currentUser = FirebaseAuth.getInstance().currentUser
        val senderUid = currentUser?.uid ?: "Unknown"

        // Create the message object including receiverUid and formatted timestamp inside the message
        val messageData = mapOf(
            "senderName" to userName,
            "receiverName" to doctorName,
            "message" to message,
            "timestamp" to formattedDate,
            "senderUid" to senderUid,// Use the formatted date here
            "receiverUid" to receiverUid // Include receiverUid in each message
        )



        // Create or update the sender's document in Firestore
        db.collection("messages")
            .document(senderUid)  // Use senderUid as the document ID
            .get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    // If the document exists, add the message to the 'messages' array
                    db.collection("messages")
                        .document(senderUid)
                        .update(
                            "messages", FieldValue.arrayUnion(messageData)  // Add the new message to the array
                        )
                        .addOnSuccessListener {
                            editTextMessage.text.clear()  // Clear the input field after sending
                        }
                        .addOnFailureListener {
                            showToast("Failed to send message.")
                        }
                } else {
                    // If the document does not exist, create a new one with the message and receiverUid
                    val newMessageList = listOf(messageData)
                    db.collection("messages")
                        .document(senderUid)
                        .set(
                            mapOf("messages" to newMessageList)  // Add the message data
                        )
                        .addOnSuccessListener {
                            editTextMessage.text.clear()  // Clear the input field after sending
                        }
                        .addOnFailureListener {
                            showToast("Failed to send message.")
                        }
                }
            }
            .addOnFailureListener { e ->
                showToast("Error checking document: ${e.message}")
            }
    }

    private fun listenForMessages() {
        val db = FirebaseFirestore.getInstance()
        val senderUid = FirebaseAuth.getInstance().currentUser?.uid ?: "Unknown"

        // Listen for changes in the messages array for this sender in Firestore
        db.collection("messages")
            .document(senderUid)  // Use senderUid as the document ID
            .addSnapshotListener { snapshot, e ->
                if (e != null) {
                    Log.w("RoomChatFragment", "Listen failed.", e)
                    return@addSnapshotListener
                }

                if (snapshot != null && snapshot.exists()) {
                    val messages = snapshot.get("messages") as? List<Map<String, Any>> ?: listOf()
                    messageList.clear()  // Clear existing messages before updating

                    // Convert the list of messages to Message objects and add them to the message list
                    for (messageMap in messages) {
                        val message = Message(
                            senderName = messageMap["senderName"] as? String ?: "Unknown",
                            receiverName = messageMap["receiverName"] as? String ?: "Unknown",
                            message = messageMap["message"] as? String ?: "",
                            timestamp = messageMap["timestamp"] as? String ?: "",
                            senderUid = messageMap["senderUid"] as? String ?: "",
                            receiverUid = messageMap["receiverUid"] as? String ?: ""
                        )
                        messageList.add(message)  // Add the new message
                    }

                    // Notify the adapter to update the UI
                    chatAdapter.notifyDataSetChanged()

                    // Scroll to the bottom to show the latest message
                    recyclerViewMessages.scrollToPosition(messageList.size - 1)
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()

        activity?.window?.statusBarColor = resources.getColor(android.R.color.transparent, null)
        // Show Bottom Navigation when fragment is destroyed or popped
        val bottomNavLayout = activity?.findViewById<RelativeLayout>(R.id.bottom_navigation)
        bottomNavLayout?.visibility = View.VISIBLE
    }

    private fun showToast(message: String) {
        // Show a toast with a message
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}
