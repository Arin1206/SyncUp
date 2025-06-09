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

        fetchDoctorsData()

        return view
    }


    private fun getActualPatientUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_patient_email")
                .whereEqualTo("email", email)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else if (phoneNumber != null) {
            firestore.collection("users_patient_phonenumber")
                .whereEqualTo("phoneNumber", phoneNumber)
                .get()
                .addOnSuccessListener { documents ->
                    val uid = documents.firstOrNull()?.getString("userId")
                    onResult(uid)
                }
                .addOnFailureListener {
                    onResult(null)
                }
        } else {
            onResult(null)
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
                        Log.d(
                            "fetchDoctorsData",
                            "Found doctorUid: $doctorUid for patientId: $patientId"
                        )

                        db.collection("users_doctor_email")
                            .document(doctorUid)
                            .get()
                            .addOnSuccessListener { doctorDoc ->
                                val doctorName = doctorDoc.getString("fullName") ?: "Unknown Doctor"
                                val doctorPhone = doctorDoc.getString("phoneNumber") ?: "No Phone"
                                val doctorEmail = doctorDoc.getString("email") ?: "No Email"

                                val doctorNameWithPrefix = "Dr. $doctorName"

                                db.collection("doctor_photoprofile").document(doctorUid).get()
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
                                                Log.d(
                                                    "fetchDoctorsData",
                                                    "Fetched message snapshot: ${messageSnapshot.documents.size}"
                                                )

                                                if (messageSnapshot.isEmpty) {
                                                    Log.d(
                                                        "fetchDoctorsData",
                                                        "No messages found for patientId: $patientId, displaying default message"
                                                    )
                                                    val newChat = Chat(
                                                        doctorName = doctorNameWithPrefix,
                                                        message = "Start Message Now",
                                                        date = "",
                                                        doctorPhoneNumber = doctorPhone,
                                                        doctorUid = doctorUid,
                                                        unreadCount = 0,
                                                        isUnread = false,
                                                        doctorEmail = doctorEmail,
                                                        profileImage = profileImageUrl,
                                                        patientId = patientId,
                                                        patientName = "",
                                                    )
                                                    chatList.add(newChat)
                                                } else {
                                                    messageSnapshot.documents.forEach { doc ->
                                                        val message =
                                                            doc.getString("message") ?: "No Message"
                                                        val timestamp = doc.getString("timestamp")
                                                            ?: "Unknown Timestamp"

                                                        val chat = Chat(
                                                            doctorName = doctorNameWithPrefix,
                                                            message = message,
                                                            date = timestamp,
                                                            doctorPhoneNumber = doctorPhone,
                                                            doctorUid = doctorUid,
                                                            unreadCount = 0,
                                                            isUnread = false,
                                                            doctorEmail = doctorEmail,
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
                                                Log.e(
                                                    "fetchDoctorsData",
                                                    "Error fetching messages",
                                                    e
                                                )
                                            }

                                    }
                                    .addOnFailureListener { e ->
                                        Log.e(
                                            "fetchDoctorsData",
                                            "Error fetching doctor profile image",
                                            e
                                        )
                                    }
                            }
                            .addOnFailureListener { e ->
                                Log.e("fetchDoctorsData", "Error fetching doctor details", e)
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(
                        "fetchDoctorsData",
                        "Error fetching assigned doctors for patientId: $patientId",
                        exception
                    )
                }
        }
    }


    private fun updateChatListWithSort(chats: List<Chat>) {
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

                            db.collection("users_doctor_email").document(doctorUid).get()
                                .addOnSuccessListener { doctorDoc ->
                                    val doctorName =
                                        doctorDoc.getString("fullName") ?: "Unknown Doctor"
                                    val doctorPhone =
                                        doctorDoc.getString("phoneNumber") ?: "No Phone"
                                    val doctorEmail = doctorDoc.getString("email") ?: "No Email"

                                    db.collection("chats").document(doctorUid)
                                        .collection("patients").document(patientId)
                                        .collection("messages").get()
                                        .addOnSuccessListener { messageSnapshot ->
                                            messageSnapshot.documents.forEach { msg ->
                                                val message = msg.getString("message") ?: ""
                                                val timestamp = msg.getString("timestamp") ?: ""

                                                if (message.contains(query, ignoreCase = true)) {
                                                    val chat = Chat(
                                                        doctorName = doctorName,
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
