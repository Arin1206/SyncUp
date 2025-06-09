package com.example.syncup.chat

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.activity.addCallback
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.home.HomeDoctorFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query


class ChatDoctorFragment : Fragment() {

    private lateinit var recyclerViewChats: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val chatList = mutableListOf<Chat>()
    private lateinit var searchInput: EditText

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_chat_doctor, container, false)

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

        searchInput = view.findViewById(R.id.search_input)

        searchInput.addTextChangedListener {
            val query = it.toString()
            filterMessages(query)
        }

        val arrow = view.findViewById<ImageView>(R.id.arrow)
        arrow.setOnClickListener {

            val fragment = HomeDoctorFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(R.id.frame, fragment)
                .addToBackStack(null)
                .commit()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            val homeFragment = HomeDoctorFragment()

            requireActivity().supportFragmentManager.beginTransaction()
                .replace(
                    R.id.frame,
                    homeFragment
                )
                .commit()
        }
        return view
    }

    private fun getActualDoctorUID(onResult: (String?) -> Unit) {
        val auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser ?: return onResult(null)

        val email = currentUser.email
        val phoneNumber = currentUser.phoneNumber

        val firestore = FirebaseFirestore.getInstance()

        if (email != null) {
            firestore.collection("users_doctor_email")
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
            firestore.collection("users_doctor_phonenumber")
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
        chatAdapter.notifyDataSetChanged()

        getActualDoctorUID { doctorUid ->
            if (doctorUid == null) {
                Log.d("fetchDoctorsData", "Doctor UID is null")
                return@getActualDoctorUID
            }

            Log.d("fetchDoctorsData", "Fetching assigned patients for doctorUid: $doctorUid")

            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUid)
                .get()
                .addOnSuccessListener { result ->
                    Log.d(
                        "fetchDoctorsData",
                        "Assigned patients fetched, result size: ${result.size()}"
                    )

                    result.forEach { document ->
                        val patientId = document.getString("patientId") ?: return@forEach
                        Log.d("fetchDoctorsData", "Processing patientId: $patientId")

                        val emailQuery =
                            db.collection("users_patient_email").whereEqualTo("userId", patientId)
                                .get()
                        val phoneQuery = db.collection("users_patient_phonenumber")
                            .whereEqualTo("userId", patientId).get()

                        com.google.android.gms.tasks.Tasks.whenAllSuccess<com.google.firebase.firestore.QuerySnapshot>(
                            emailQuery,
                            phoneQuery
                        )
                            .addOnSuccessListener { results ->
                                val emailDocs =
                                    results[0] as com.google.firebase.firestore.QuerySnapshot
                                val phoneDocs =
                                    results[1] as com.google.firebase.firestore.QuerySnapshot
                                val patientDoc =
                                    (emailDocs.documents + phoneDocs.documents).firstOrNull()

                                if (patientDoc != null) {
                                    val patientName =
                                        patientDoc.getString("fullName") ?: "Unknown Patient"
                                    val patientPhone =
                                        patientDoc.getString("phoneNumber") ?: "No Phone"

                                    db.collection("patient_photoprofile")
                                        .document(patientId)
                                        .get()
                                        .addOnSuccessListener { photoDoc ->
                                            val profileImageUrl =
                                                photoDoc.getString("photoUrl") ?: ""
                                            Log.d(
                                                "fetchDoctorsData",
                                                "Profile image URL fetched for patientId: $patientId"
                                            )

                                            db.collection("chats").document(doctorUid)
                                                .collection("patients")
                                                .document(patientId)
                                                .collection("messages")
                                                .orderBy(
                                                    "timestamp",
                                                    Query.Direction.DESCENDING
                                                )
                                                .limit(1)
                                                .get()
                                                .addOnSuccessListener { messageSnapshot ->
                                                    if (messageSnapshot.isEmpty) {
                                                        Log.d(
                                                            "fetchDoctorsData",
                                                            "No messages found for patientId: $patientId, displaying default message"
                                                        )

                                                        val newChat = Chat(
                                                            doctorName = patientName,
                                                            message = "Start Message Now",
                                                            date = "",
                                                            doctorPhoneNumber = patientPhone,
                                                            doctorUid = doctorUid,
                                                            unreadCount = 0,
                                                            isUnread = false,
                                                            doctorEmail = "",
                                                            profileImage = profileImageUrl,
                                                            patientId = patientId,
                                                            patientName = patientName
                                                        )
                                                        chatList.add(newChat)
                                                        chatAdapter.notifyDataSetChanged()
                                                    } else {
                                                        val latestMessage =
                                                            messageSnapshot.documents.first()
                                                                .getString("message")
                                                                ?: "Start Message Now"
                                                        val latestDate =
                                                            messageSnapshot.documents.firstOrNull()
                                                                ?.getString("timestamp") ?: ""

                                                        Log.d(
                                                            "fetchDoctorsData",
                                                            "Latest message: $latestMessage, timestamp: $latestDate"
                                                        )

                                                        val chat = Chat(
                                                            doctorName = patientName,
                                                            message = latestMessage,
                                                            date = latestDate,
                                                            doctorPhoneNumber = patientPhone,
                                                            doctorUid = doctorUid,
                                                            unreadCount = 0,
                                                            isUnread = false,
                                                            doctorEmail = "",
                                                            profileImage = profileImageUrl,
                                                            patientId = patientId,
                                                            patientName = patientName
                                                        )
                                                        chatList.add(chat)
                                                        chatAdapter.notifyDataSetChanged()
                                                    }
                                                }
                                                .addOnFailureListener { exception ->
                                                    Log.e(
                                                        "fetchDoctorsData",
                                                        "Error fetching messages for patientId: $patientId",
                                                        exception
                                                    )
                                                }
                                        }
                                        .addOnFailureListener { exception ->
                                            Log.e(
                                                "fetchDoctorsData",
                                                "Error fetching profile image for patientId: $patientId",
                                                exception
                                            )
                                        }
                                } else {
                                    Log.w(
                                        "fetchDoctorsData",
                                        "No patient doc found in either email or phone collections for $patientId"
                                    )
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e(
                                    "fetchDoctorsData",
                                    "Error fetching patient details from both collections for patientId: $patientId",
                                    exception
                                )
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("fetchDoctorsData", "Error fetching assigned patients", exception)
                }
        }
    }


    private fun fetchAllMessagesForDoctor() {
        val db = FirebaseFirestore.getInstance()

        getActualDoctorUID { doctorUid ->
            if (doctorUid == null) {
                Log.d("fetchAllMessagesForDoctor", "Doctor UID is null, cannot fetch messages.")
                return@getActualDoctorUID
            }

            Log.d("fetchAllMessagesForDoctor", "Fetching all messages for doctorUid: $doctorUid")

            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUid)
                .get()
                .addOnSuccessListener { result ->
                    Log.d(
                        "fetchAllMessagesForDoctor",
                        "Assigned patients fetched, result size: ${result.size()}"
                    )

                    result.forEach { document ->
                        val patientId = document.getString("patientId") ?: return@forEach
                        Log.d(
                            "fetchAllMessagesForDoctor",
                            "Fetching messages for patientId: $patientId"
                        )

                        db.collection("chats").document(doctorUid).collection("patients")
                            .document(patientId)
                            .collection("messages")
                            .get()
                            .addOnSuccessListener { messageSnapshot ->
                                if (messageSnapshot.isEmpty) {
                                    Log.d(
                                        "fetchAllMessagesForDoctor",
                                        "No messages found for patientId: $patientId"
                                    )
                                } else {
                                    messageSnapshot.documents.forEach { messageDoc ->
                                        val messageText =
                                            messageDoc.getString("message") ?: "No message"
                                        val timestamp =
                                            messageDoc.getString("timestamp") ?: "No Timestamp"

                                        Log.d(
                                            "fetchAllMessagesForDoctor",
                                            "Message: $messageText, Timestamp: $timestamp"
                                        )

                                    }
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e(
                                    "fetchAllMessagesForDoctor",
                                    "Error fetching messages for patientId: $patientId",
                                    exception
                                )
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e(
                        "fetchAllMessagesForDoctor",
                        "Error fetching assigned patients for doctor",
                        exception
                    )
                }
        }
    }


    private val handler = Handler(Looper.getMainLooper())
    private var searchRunnable: Runnable? = null

    private fun filterMessages(query: String) {
        val loadingProgressBar = view?.findViewById<ProgressBar>(R.id.loadingProgressBar)
        loadingProgressBar?.visibility = View.VISIBLE
        if (query.isNotEmpty()) {
            searchRunnable?.let { handler.removeCallbacks(it) }

            searchRunnable = Runnable {
                getActualDoctorUID { doctorUid ->
                    if (doctorUid == null) {
                        Log.d("filterMessages", "Doctor UID is null, cannot filter messages.")
                        return@getActualDoctorUID
                    }

                    val db = FirebaseFirestore.getInstance()

                    db.collection("assigned_patient")
                        .whereEqualTo("doctorUid", doctorUid)
                        .get()
                        .addOnSuccessListener { result ->
                            val filteredChats = mutableListOf<Chat>()

                            result.forEach { document ->
                                val patientId = document.getString("patientId") ?: return@forEach

                                db.collection("users_patient_email")
                                    .whereEqualTo("userId", patientId)
                                    .get()
                                    .addOnSuccessListener { patientEmails ->
                                        val patientDoc = patientEmails.documents.firstOrNull()
                                        patientDoc?.let {
                                            val patientName =
                                                it.getString("fullName") ?: "Unknown Patient"
                                            val patientPhone =
                                                it.getString("phoneNumber") ?: "No Phone"

                                            db.collection("patient_photoprofile")
                                                .document(patientId)
                                                .get()
                                                .addOnSuccessListener { photoDoc ->
                                                    val profileImageUrl =
                                                        photoDoc.getString("photoUrl") ?: ""

                                                    db.collection("chats").document(doctorUid)
                                                        .collection("patients")
                                                        .document(patientId)
                                                        .collection("messages")
                                                        .get()
                                                        .addOnSuccessListener { messageSnapshot ->
                                                            if (messageSnapshot.isEmpty) {
                                                                Log.d(
                                                                    "filterMessages",
                                                                    "No messages found for patientId: $patientId"
                                                                )
                                                            } else {
                                                                messageSnapshot.documents.forEach { messageDoc ->
                                                                    val messageText =
                                                                        messageDoc.getString("message")
                                                                            ?: "No message"
                                                                    val timestamp =
                                                                        messageDoc.getString("timestamp")
                                                                            ?: "No Timestamp"

                                                                    if (messageText.contains(
                                                                            query,
                                                                            ignoreCase = true
                                                                        )
                                                                    ) {
                                                                        val chat = Chat(
                                                                            doctorName = patientName,
                                                                            message = messageText,
                                                                            date = timestamp,
                                                                            doctorPhoneNumber = patientPhone,
                                                                            doctorUid = doctorUid,
                                                                            unreadCount = 0,
                                                                            isUnread = false,
                                                                            doctorEmail = "",
                                                                            profileImage = profileImageUrl,
                                                                            patientId = patientId,
                                                                            patientName = patientName
                                                                        )
                                                                        filteredChats.add(chat)
                                                                    }
                                                                }
                                                            }
                                                            chatList.clear()
                                                            chatList.addAll(filteredChats)
                                                            chatAdapter.notifyDataSetChanged()

                                                            loadingProgressBar?.visibility =
                                                                View.GONE
                                                        }
                                                        .addOnFailureListener { exception ->
                                                            Log.e(
                                                                "filterMessages",
                                                                "Error fetching messages: ",
                                                                exception
                                                            )
                                                        }
                                                }
                                                .addOnFailureListener { exception ->
                                                    Log.e(
                                                        "filterMessages",
                                                        "Error fetching profile image: ",
                                                        exception
                                                    )
                                                }
                                        }
                                    }
                                    .addOnFailureListener { exception ->
                                        Log.e(
                                            "filterMessages",
                                            "Error fetching patient details: ",
                                            exception
                                        )
                                    }
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("filterMessages", "Error fetching assigned patients: ", exception)
                        }
                }
            }

            handler.postDelayed(searchRunnable!!, 100)
        } else {

            chatList.clear()
            chatAdapter.notifyDataSetChanged()

            loadingProgressBar?.visibility = View.GONE
        }
    }


    override fun onResume() {
        super.onResume()

        chatList.clear()
        chatAdapter.notifyDataSetChanged()

        fetchDoctorsData()
        fetchAllMessagesForDoctor()
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
        bundle.putString("receiverUid", patientId)
        bundle.putString("doctorUid", doctorUid)
        bundle.putString("patientName", patientName)
        bundle.putString("profileImage", profileImage)

        val roomChatFragment = RoomChatDoctorFragment()
        roomChatFragment.arguments = bundle

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.frame, roomChatFragment)
            .addToBackStack(null)
            .commit()
    }


}