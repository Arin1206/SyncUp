package com.example.syncup.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ProgressBar
import androidx.core.widget.addTextChangedListener
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
    private lateinit var searchInput: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        recyclerViewChats = view.findViewById(R.id.recyclerViewChats)
        chatAdapter = ChatAdapter(chatList) { doctorName, doctorPhoneNumber, doctorUid ->
            navigateToRoomChat(doctorName, doctorPhoneNumber, doctorUid)
        }
        recyclerViewChats.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewChats.adapter = chatAdapter

        // Initialize the search input
        searchInput = view.findViewById(R.id.search_input)

        // Set the listener for search input
        searchInput.addTextChangedListener {
            val query = it.toString()
            filterMessages(query) // Filter the loaded data based on the search query
        }

        // Initial data fetch
        fetchDoctorsData()  // Load doctor data and messages here

        return view
    }

    private fun fetchDoctorsData() {
        val db = FirebaseFirestore.getInstance()
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        // Clear the previous data in the chat list before adding new data
        chatList.clear()
        chatAdapter.notifyDataSetChanged()

        val emailCollection = db.collection("users_doctor_email").get()
        val phoneCollection = db.collection("users_doctor_phonenumber").get()

        emailCollection.addOnSuccessListener { emailResult ->
            phoneCollection.addOnSuccessListener { phoneResult ->

                val allDocs = emailResult.documents + phoneResult.documents

                // Use a map to keep track of the latest message per doctor
                val doctorMap = mutableMapOf<String, Chat>()

                for (document in allDocs) {
                    val doctorName = document.getString("fullName") ?: "Unknown"
                    val doctorEmail = document.getString("email") ?: "No Email"
                    val doctorPhone = document.getString("phoneNumber") ?: "No Phone"
                    val doctorUid = document.id

                    val messagesFromDoctorRef = db.collection("messages").document(doctorUid)
                    val messagesFromUserRef = db.collection("messages").document(currentUserUid)

                    messagesFromDoctorRef.addSnapshotListener { snapshotDoctor, _ ->
                        val messagesFromDoctor = snapshotDoctor?.get("messages") as? List<Map<String, Any>> ?: emptyList()

                        messagesFromUserRef.addSnapshotListener { snapshotUser, _ ->
                            val messagesFromUser = snapshotUser?.get("messages") as? List<Map<String, Any>> ?: emptyList()

                            // Combine messages from both doctor and user
                            val combinedMessages = (messagesFromDoctor + messagesFromUser)

                            // If no messages exist, only show doctors
                            if (combinedMessages.isEmpty()) {
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
                                // Ensure the doctor is in the map only once
                                doctorMap[doctorUid] = chat
                                chatAdapter.notifyDataSetChanged()
                                return@addSnapshotListener // Skip further processing since no messages
                            }

                            // If there are messages, process the latest one
                            val latest = combinedMessages.filter {
                                val receiverName = it["receiverName"]?.toString()
                                receiverName == doctorName // Only include messages for this doctor
                            }.maxByOrNull {
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

                            if (latest != null) {
                                val latestMsgText = latest["message"]?.toString() ?: "Start Message Now"
                                val latestMsgDate = latest["timestamp"]?.toString() ?: ""
                                val senderUid = latest["senderUid"]?.toString()?.takeIf { it != "null" } ?: ""

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

                                // Update the map with the latest message for this doctor
                                doctorMap[doctorUid] = chat

                                // After processing all doctors, update the chat list
                                chatList.clear()
                                chatList.addAll(doctorMap.values)

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
    }

    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private fun filterMessages(query: String) {
        // Show the loading indicator
        val loadingProgressBar = view?.findViewById<ProgressBar>(R.id.loadingProgressBar)
        loadingProgressBar?.visibility = View.VISIBLE

        // If the query is empty, reload only doctors without their associated messages
        if (query.isEmpty()) {
            // Clear previous data to avoid old search results
            chatList.clear()
            chatAdapter.notifyDataSetChanged()

            fetchDoctorsData()
            // Fetch doctors' data again without messages

            // Hide loading indicator after fetchDoctorsData() completes
            loadingProgressBar?.visibility = View.GONE
        } else {
            // If there is a query, filter messages based on the search query
            searchRunnable?.let { handler.removeCallbacks(it) }

            // Debounce the search action (set to 100ms delay)
            searchRunnable = Runnable {
                val db = FirebaseFirestore.getInstance()
                val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return@Runnable

                db.collection("users_doctor_email").get().addOnSuccessListener { emailResult ->
                    db.collection("users_doctor_phonenumber").get().addOnSuccessListener { phoneResult ->
                        val allDocs = emailResult.documents + phoneResult.documents

                        // Clear previous chat list before filtering to avoid mixing old and new results
                        val filteredList = mutableListOf<Chat>()

                        for (document in allDocs) {
                            val doctorUid = document.id
                            val doctorName = document.getString("fullName") ?: "Unknown"
                            val doctorPhone = document.getString("phoneNumber") ?: "No Phone"

                            val messagesFromDoctorRef = db.collection("messages").document(doctorUid)
                            val messagesFromUserRef = db.collection("messages").document(currentUserUid)

                            messagesFromDoctorRef.addSnapshotListener { snapshotDoctor, _ ->
                                val messagesFromDoctor = snapshotDoctor?.get("messages") as? List<Map<String, Any>> ?: emptyList()

                                messagesFromUserRef.addSnapshotListener { snapshotUser, _ ->
                                    val messagesFromUser = snapshotUser?.get("messages") as? List<Map<String, Any>> ?: emptyList()

                                    // Combine messages from both doctor and user
                                    val combinedMessages = (messagesFromDoctor + messagesFromUser)

                                    // Filter the combined messages based on the search query and receiverName
                                    val filteredMessages = combinedMessages.filter {
                                        val messageText = it["message"]?.toString() ?: ""
                                        val doctorText = doctorName.contains(query, ignoreCase = true)
                                        val messageMatch = messageText.contains(query, ignoreCase = true)
                                        val receiverMatch = it["receiverName"]?.toString() == doctorName
                                        doctorText || messageMatch && receiverMatch
                                    }

                                    // Track the latest message for each doctor and receiverName
                                    filteredMessages.forEach { messageData ->
                                        val receiverName = messageData["receiverName"]?.toString()

                                        // Ensure we only include messages for the correct receiverName
                                        if (receiverName == doctorName && messageData["message"]?.toString()?.contains(query, ignoreCase = true) == true) {
                                            val lastMessage = filteredMessages.maxByOrNull {
                                                val ts = it["timestamp"]?.toString() ?: ""
                                                if (ts.isEmpty()) {
                                                    return@maxByOrNull 0L
                                                }
                                                try {
                                                    val format = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault())
                                                    format.parse(ts)?.time ?: 0L
                                                } catch (e: Exception) {
                                                    0L
                                                }
                                            }

                                            lastMessage?.let { latestMsg ->
                                                val latestMsgText = latestMsg["message"]?.toString() ?: "Start Message Now"
                                                val latestMsgDate = latestMsg["timestamp"]?.toString() ?: ""
                                                val senderUid = latestMsg["senderUid"]?.toString() ?: ""

                                                val doctorEmail = document.getString("email") ?: "No Email"
                                                val chat = Chat(
                                                    doctorName = doctorName,
                                                    message = latestMsgText,
                                                    date = latestMsgDate,
                                                    doctorPhoneNumber = doctorPhone,
                                                    doctorUid = doctorUid,
                                                    doctorEmail = doctorEmail,
                                                    unreadCount = 0, // Adjust unread count logic as needed
                                                    isUnread = senderUid.isEmpty()
                                                )

                                                // Avoid adding duplicate entries
                                                if (!filteredList.contains(chat)) {
                                                    filteredList.add(chat)
                                                }
                                            }
                                        }
                                    }

                                    // Sort by the latest message
                                    filteredList.sortByDescending {
                                        SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(it.date)?.time ?: 0L
                                    }

                                    // Update the chat list with the filtered and sorted results
                                    chatList.clear()
                                    chatList.addAll(filteredList)
                                    chatAdapter.notifyDataSetChanged()

                                    // Hide loading indicator after data is loaded and filtered
                                    loadingProgressBar?.visibility = View.GONE
                                }
                            }
                        }
                    }
                }
            }

            // Set a shorter debounce delay to improve responsiveness (100ms)
            handler.postDelayed(searchRunnable!!, 100) // Wait 100ms before making the request
        }
    }


    private fun navigateToRoomChat(doctorName: String, doctorPhoneNumber: String, doctorUid: String) {
        val bundle = Bundle()
        bundle.putString("doctor_name", doctorName)
        bundle.putString("doctor_phone_number", doctorPhoneNumber)
        bundle.putString("receiverUid", doctorUid)

        val roomChatFragment = RoomChatFragment()
        roomChatFragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, roomChatFragment)
            .addToBackStack(null)
            .commit()
    }
}
