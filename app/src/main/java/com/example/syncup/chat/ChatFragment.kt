package com.example.syncup.chat

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.addCallback
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.home.HomeFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Locale

open class ChatFragment : Fragment() {

    lateinit var recyclerViewChats: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()
    private lateinit var searchInput: EditText

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat, container, false)

        recyclerViewChats = view.findViewById(R.id.recyclerViewChats)
        chatAdapter =
            ChatAdapter(chatList) { doctorName, doctorPhoneNumber, doctorUid, patientId, patientName, profileImage ->
                navigateToRoomChat(
                    doctorName,
                    doctorPhoneNumber,
                    doctorUid,
                    patientId,
                    patientName,
                    profileImage
                )
            }
        recyclerViewChats.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewChats.adapter = chatAdapter

        val arrow = view.findViewById<ImageView>(R.id.arrow)
        arrow.setOnClickListener {
            val fragment = HomeFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame, fragment)
                .addToBackStack(null)
                .commit()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val homeFragment = HomeFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame, homeFragment)
                .commit()
        }

        searchInput = view.findViewById(R.id.search_input)

        searchInput.addTextChangedListener {
            val query = it.toString()
            filterMessages(query)
        }

        if (chatList.isEmpty()) {
            fetchDoctorsData() // Fetch data if chatList is empty
        }

        return view
    }


    private fun getActualPatientUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        var phoneNumber = currentUser.phoneNumber

        // Format the phone number if it starts with "+62"
        phoneNumber = formatPhoneNumber(phoneNumber)

        val firestore = FirebaseFirestore.getInstance()

        Log.d("ProfilePatient", "Current User Email: $email")
        Log.d("ProfilePatient", "Formatted Phone: $phoneNumber")

        if (!email.isNullOrEmpty()) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfilePatient", "Email query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfilePatient", "No user document found for email")
                        onResult(null)  // No user document found for email
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfilePatient", "Found userId for email: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfilePatient", "Error querying email", e)
                    onResult(null)
                }
        } else if (!phoneNumber.isNullOrEmpty()) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    Log.d("ProfilePatient", "Phone number query result size: ${documents.size()}")
                    if (documents.isEmpty) {
                        Log.e("ProfilePatient", "No user document found for phone number")
                        onResult(null)  // No user document found for phone number
                    } else {
                        val uid = documents.firstOrNull()?.getString("userId")
                        Log.d("ProfilePatient", "Found userId for phone number: $uid")
                        onResult(uid)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("ProfilePatient", "Error querying phone number", e)
                    onResult(null)
                }
        } else {
            Log.e("ProfilePatient", "No email or phone number found for the current user")
            onResult(null)  // If neither email nor phone is available
        }
    }


    // Helper function to format phone number
    private fun formatPhoneNumber(phoneNumber: String?): String? {
        return phoneNumber?.let {
            if (it.startsWith("+62")) {
                "0" + it.substring(3)  // Replace +62 with 0
            } else {
                it  // Return phone number as is if it doesn't start with +62
            }
        }
    }
    private fun fetchDoctorsData() {
        val db = FirebaseFirestore.getInstance()
        chatList.clear()
        getActualPatientUID { patientId ->
            if (patientId == null) {
                Log.d("fetchDoctorsData", "Patient UID is null")
                return@getActualPatientUID
            }
            Log.d("fetchDoctorsData", "Fetching doctorUid for patientId: $patientId")
            db.collection("assigned_patient")
                .whereEqualTo("patientId", patientId)
                .get()
                .addOnSuccessListener { result ->
                    if (result.isEmpty) {
                        Log.d("fetchDoctorsData", "No doctors assigned to patientId: $patientId")
                        return@addOnSuccessListener
                    }
                    var processedCount = 0
                    val totalDoctors = result.size()
                    result.forEach { document ->
                        val doctorUid = document.getString("doctorUid") ?: return@forEach
                        Log.d("fetchDoctorsData", "Found doctorUid: $doctorUid for patientId: $patientId")
                        var doctorName: String? = null
                        var doctorPhone: String? = null
                        db.collection("users_doctor_email")
                            .document(doctorUid)
                            .get()
                            .addOnSuccessListener { doctorDoc ->
                                doctorName = doctorDoc.getString("fullName")
                                doctorPhone = doctorDoc.getString("phoneNumber")
                                val doctorEmail = doctorDoc.getString("email")
                                doctorName = doctorName?.let { "Dr. $it" } ?: "Dr. Unknown"
                                if (doctorName == "Dr. Unknown") {
                                    db.collection("users_doctor_phonenumber")
                                        .document(doctorUid)
                                        .get()
                                        .addOnSuccessListener { phoneDoc ->
                                            doctorName = phoneDoc.getString("fullName")?.let { "Dr. $it" } ?: "Dr. Unknown"
                                            doctorPhone = phoneDoc.getString("phoneNumber")
                                            db.collection("doctor_photoprofile")
                                                .document(doctorUid)
                                                .get()
                                                .addOnSuccessListener { photoDoc ->
                                                    val profileImageUrl = photoDoc.getString("photoUrl") ?: ""
                                                    db.collection("chats").document(doctorUid)
                                                        .collection("patients")
                                                        .document(patientId)
                                                        .collection("messages")
                                                        .orderBy("timestamp", Query.Direction.DESCENDING)
                                                        .limit(1)
                                                        .get()
                                                        .addOnSuccessListener { messageSnapshot ->
                                                            Log.d("fetchDoctorsData", "Fetched message snapshot: ${messageSnapshot.documents.size}")
                                                            if (messageSnapshot.isEmpty) {
                                                                Log.d("fetchDoctorsData", "No messages found for patientId: $patientId, displaying default message")
                                                                val newChat = Chat(
                                                                    doctorName = doctorName ?: "Dr. Unknown",
                                                                    message = "Start Message Now",
                                                                    date = "",
                                                                    doctorPhoneNumber = doctorPhone ?: "No Phone",
                                                                    doctorUid = doctorUid,
                                                                    unreadCount = 0,
                                                                    isUnread = false,
                                                                    doctorEmail = doctorEmail ?: "No Email",
                                                                    profileImage = profileImageUrl,
                                                                    patientId = patientId,
                                                                    patientName = "",
                                                                )
                                                                chatList.add(newChat)
                                                            } else {
                                                                messageSnapshot.documents.forEach { msg ->
                                                                    val message = msg.getString("message") ?: "No Message"
                                                                    val timestamp = msg.getString("timestamp") ?: "Unknown Timestamp"

                                                                    val chat = Chat(
                                                                        doctorName = doctorName ?: "Dr. Unknown",
                                                                        message = message,
                                                                        date = timestamp,
                                                                        doctorPhoneNumber = doctorPhone ?: "No Phone",
                                                                        doctorUid = doctorUid,
                                                                        unreadCount = 0,
                                                                        isUnread = false,
                                                                        doctorEmail = doctorEmail ?: "No Email",
                                                                        profileImage = profileImageUrl,
                                                                        patientId = patientId,
                                                                        patientName = ""
                                                                    )
                                                                    chatList.add(chat)
                                                                }
                                                            }

                                                            processedCount++
                                                            if (processedCount == totalDoctors) {
                                                                activity?.runOnUiThread {
                                                                    updateChatListWithSort(chatList)
                                                                    chatAdapter.notifyDataSetChanged()
                                                                }
                                                            }
                                                        }
                                                        .addOnFailureListener { e ->
                                                            Log.e("fetchDoctorsData", "Error fetching messages", e)
                                                        }
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("fetchDoctorsData", "Error fetching doctor profile image", e)
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("fetchDoctorsData", "Error fetching doctor details from users_doctor_phonenumber", e)
                                        }
                                } else {
                                    db.collection("doctor_photoprofile")
                                        .document(doctorUid)
                                        .get()
                                        .addOnSuccessListener { photoDoc ->
                                            val profileImageUrl = photoDoc.getString("photoUrl") ?: ""
                                            db.collection("chats").document(doctorUid)
                                                .collection("patients")
                                                .document(patientId)
                                                .collection("messages")
                                                .orderBy("timestamp", Query.Direction.DESCENDING)
                                                .limit(1)
                                                .get()
                                                .addOnSuccessListener { messageSnapshot ->
                                                    Log.d("fetchDoctorsData", "Fetched message snapshot: ${messageSnapshot.documents.size}")
                                                        if (messageSnapshot.isEmpty) {
                                                        Log.d("fetchDoctorsData", "No messages found for patientId: $patientId, displaying default message")
                                                        val newChat = Chat(
                                                            doctorName = doctorName ?: "Dr. Unknown",
                                                            message = "Start Message Now",
                                                            date = "",
                                                            doctorPhoneNumber = doctorPhone ?: "No Phone",
                                                            doctorUid = doctorUid,
                                                            unreadCount = 0,
                                                            isUnread = false,
                                                            doctorEmail = doctorEmail ?: "No Email",
                                                            profileImage = profileImageUrl,
                                                            patientId = patientId,
                                                            patientName = "",
                                                        )
                                                        chatList.add(newChat)
                                                    } else {
                                                        messageSnapshot.documents.forEach { msg ->
                                                            val message = msg.getString("message") ?: "No Message"
                                                            val timestamp = msg.getString("timestamp") ?: "Unknown Timestamp"

                                                            val chat = Chat(
                                                                doctorName = doctorName ?: "Dr. Unknown",
                                                                message = message,
                                                                date = timestamp,
                                                                doctorPhoneNumber = doctorPhone ?: "No Phone",
                                                                doctorUid = doctorUid,
                                                                unreadCount = 0,
                                                                isUnread = false,
                                                                doctorEmail = doctorEmail ?: "No Email",
                                                                profileImage = profileImageUrl,
                                                                patientId = patientId,
                                                                patientName = ""
                                                            )
                                                            chatList.add(chat)
                                                        }
                                                    }
                                                    processedCount++
                                                    if (processedCount == totalDoctors) {
                                                        activity?.runOnUiThread {
                                                            updateChatListWithSort(chatList)
                                                            chatAdapter.notifyDataSetChanged()
                                                        }
                                                    }
                                                }
                                                .addOnFailureListener { e ->
                                                    Log.e("fetchDoctorsData", "Error fetching messages", e)
                                                }
                                        }
                                        .addOnFailureListener { e ->
                                            Log.e("fetchDoctorsData", "Error fetching doctor profile image", e)
                                        }
                                }
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("fetchDoctorsData", "Error fetching assigned doctors for patientId: $patientId", exception)
                }
        }
    }







    fun updateChatListWithSort(chats: List<Chat>) {
        val sortedList = chats.sortedByDescending {
            try {
                SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(it.date)?.time
            } catch (e: Exception) {
                0L
            }
        }

        chatAdapter.updateList(sortedList)

    }


    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private fun filterMessages(query: String) {
        val loadingProgressBar = view?.findViewById<ProgressBar>(R.id.loadingProgressBar)
        loadingProgressBar?.visibility = View.VISIBLE

        if (query.isEmpty()) {
            chatList.clear()
            chatAdapter.notifyDataSetChanged()
            fetchDoctorsData()
            loadingProgressBar?.visibility = View.GONE
            return
        }

        searchRunnable?.let { handler.removeCallbacks(it) }

        searchRunnable = Runnable {
            getActualPatientUID { patientId ->
                if (patientId == null) {
                    Log.d("filterMessages", "Patient UID is null")
                    return@getActualPatientUID
                }

                val db = FirebaseFirestore.getInstance()

                db.collection("assigned_patient")
                    .whereEqualTo("patientId", patientId)
                    .get()
                    .addOnSuccessListener { result ->
                        if (result.isEmpty) {
                            Log.d("filterMessages", "No assigned doctor found")
                            return@addOnSuccessListener
                        }

                        val filteredList = mutableListOf<Chat>()
                        var processedCount = 0
                        val totalDoctors = result.size()

                        result.forEach { assignDoc ->
                            val doctorUid = assignDoc.getString("doctorUid") ?: return@forEach

                            // Fetch doctor details from 'users_doctor_email' collection
                            db.collection("users_doctor_email").document(doctorUid).get()
                                .addOnSuccessListener { doctorDoc ->
                                    var doctorName = doctorDoc.getString("fullName") ?: "Unknown Doctor"
                                    var doctorPhone = doctorDoc.getString("phoneNumber") ?: "No Phone"
                                    val doctorEmail = doctorDoc.getString("email") ?: "No Email"

                                    // If no phone number in email, we should try the 'users_doctor_phonenumber' collection
                                    if (doctorPhone == "No Phone") {
                                        db.collection("users_doctor_phonenumber")
                                            .document(doctorUid)
                                            .get()
                                            .addOnSuccessListener { phoneDoc ->
                                                // Update doctorName only if it was "Unknown Doctor"
                                                if (doctorName == "Unknown Doctor") {
                                                    doctorName = phoneDoc.getString("fullName") ?: "Unknown Doctor"
                                                }
                                                doctorPhone = phoneDoc.getString("phoneNumber") ?: "No Phone"

                                                Log.d("filterMessages", "Doctor phone number (from phone collection): $doctorPhone")
                                                Log.d("filterMessages", "Doctor name (from phone collection): $doctorName")

                                                // Fetch messages for the doctor and patient pair
                                                db.collection("chats").document(doctorUid)
                                                    .collection("patients").document(patientId)
                                                    .collection("messages").get()
                                                    .addOnSuccessListener { messageSnapshot ->
                                                        messageSnapshot.documents.forEach { msg ->
                                                            val message = msg.getString("message") ?: ""
                                                            val timestamp = msg.getString("timestamp") ?: "No Timestamp"

                                                            if (message.contains(query, ignoreCase = true)) {
                                                                val chat = Chat(
                                                                    doctorName = doctorName,  // Now this name will be correct
                                                                    message = message,
                                                                    date = timestamp,
                                                                    doctorPhoneNumber = doctorPhone,
                                                                    doctorUid = doctorUid,
                                                                    unreadCount = 0,
                                                                    isUnread = false,
                                                                    doctorEmail = doctorEmail,
                                                                    profileImage = "",
                                                                    patientId = patientId,
                                                                    patientName = ""
                                                                )
                                                                filteredList.add(chat)
                                                            }
                                                        }

                                                        processedCount++
                                                        if (processedCount == totalDoctors) {
                                                            updateChatListWithSort(filteredList)
                                                            loadingProgressBar?.visibility = View.GONE
                                                        }
                                                    }
                                            }
                                            .addOnFailureListener { exception ->
                                                Log.e("filterMessages", "Error fetching phone number from users_doctor_phonenumber", exception)
                                            }
                                    } else {
                                        // Fetch messages directly if phone number is available in email collection
                                        db.collection("chats").document(doctorUid)
                                            .collection("patients").document(patientId)
                                            .collection("messages").get()
                                            .addOnSuccessListener { messageSnapshot ->
                                                messageSnapshot.documents.forEach { msg ->
                                                    val message = msg.getString("message") ?: ""
                                                    val timestamp = msg.getString("timestamp") ?: "No Timestamp"

                                                    if (message.contains(query, ignoreCase = true)) {
                                                        val chat = Chat(
                                                            doctorName = doctorName,  // Use the name from email collection
                                                            message = message,
                                                            date = timestamp,
                                                            doctorPhoneNumber = doctorPhone,
                                                            doctorUid = doctorUid,
                                                            unreadCount = 0,
                                                            isUnread = false,
                                                            doctorEmail = doctorEmail,
                                                            profileImage = "",
                                                            patientId = patientId,
                                                            patientName = ""
                                                        )
                                                        filteredList.add(chat)
                                                    }
                                                }

                                                processedCount++
                                                if (processedCount == totalDoctors) {
                                                    updateChatListWithSort(filteredList)
                                                    loadingProgressBar?.visibility = View.GONE
                                                }
                                            }
                                            .addOnFailureListener { exception ->
                                                Log.e("filterMessages", "Error fetching messages", exception)
                                            }
                                    }
                                }
                                .addOnFailureListener { exception ->
                                    Log.e("filterMessages", "Error fetching doctor details from users_doctor_email", exception)
                                }
                        }
                    }
            }
        }

        handler.postDelayed(searchRunnable!!, 100)
    }



    private fun navigateToRoomChat(
        doctorName: String,
        doctorPhoneNumber: String,
        doctorUid: String,
        patientId: String,
        patientName: String,
        profileImage: String
    ) {
        val bundle = Bundle()
        bundle.putString("doctor_name", doctorName)
        bundle.putString("doctor_phone_number", doctorPhoneNumber)
        bundle.putString("receiverUid", doctorUid)
        bundle.putString("patientId", patientId)
        bundle.putString("patientName", patientName)
        bundle.putString("profileImage", profileImage)


        val roomChatFragment = RoomChatFragment()
        roomChatFragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, roomChatFragment)
            .addToBackStack(null)
            .commit()
    }
}
