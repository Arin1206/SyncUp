package com.example.syncup.chat

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query


class RoomChatDoctorFragment : Fragment() {

    private lateinit var doctorNameTextView: TextView
    private lateinit var sendMessageLayout: ConstraintLayout
    private lateinit var doctorName: String
    private lateinit var patientName: String
    private lateinit var doctorPhoneNumber: String
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageView
    private lateinit var arrow: ImageView
    private var receiverUid: String? = null
    private lateinit var recyclerViewMessages: RecyclerView
    private val chatList = mutableListOf<Chat>()
    private val messageList = mutableListOf<Message>()
    private lateinit var chatAdapter: RoomChatAdapter
    private var doctorUid: String? = null
    private lateinit var profileImageView: ImageView
    private var profileImageUrl: String? = null

    @RequiresApi(Build.VERSION_CODES.R)
    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_room_chat_doctor, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(0, 0, 0, bottom)
            insets
        }


        doctorUid = arguments?.getString("doctorUid") ?: "Unknown"

        doctorNameTextView = view.findViewById(R.id.doctor_name)
        editTextMessage = view.findViewById(R.id.editTextMessage)
        buttonSend = view.findViewById(R.id.buttonSend)
        recyclerViewMessages = view.findViewById(R.id.recyclerViewMessages)
        arrow = view.findViewById(R.id.arrow)
        profileImageView = view.findViewById(R.id.profile_image)

        doctorName = arguments?.getString("doctor_name") ?: "Unknown"
        patientName = arguments?.getString("patientName") ?: "Unknown"
        doctorPhoneNumber = arguments?.getString("doctor_phone_number") ?: "Unknown"
        receiverUid = arguments?.getString("receiverUid")
        profileImageUrl = arguments?.getString("profileImage")

        Glide.with(this)
            .load(profileImageUrl)
            .placeholder(R.drawable.account_circle)
            .circleCrop()
            .into(profileImageView)

        doctorNameTextView.text = patientName

        chatAdapter = RoomChatAdapter(messageList, doctorUid ?: "Unknown")
        recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewMessages.adapter = chatAdapter



        listenForMessages()
        arrow.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        activity?.window?.statusBarColor = resources.getColor(R.color.purple_dark, null)

        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString()
            if (message.isNotEmpty() && receiverUid != null) {
                sendMessageToFirestore(message)
            } else {

                showToast("Please wait for user data to load.")
            }
        }

        fetchDoctorsData()
        sendMessageLayout = view.findViewById(R.id.sendMessageLayout)

        val bottomNavView = activity?.findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNavView?.visibility = View.GONE

        val scan = activity?.findViewById<FrameLayout>(R.id.scanButtonContainer)
        scan?.visibility = View.GONE

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
                return@getActualDoctorUID
            }

            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUid)
                .get()
                .addOnSuccessListener { result ->
                    result.forEach { document ->
                        val patientId = document.getString("patientId") ?: return@forEach

                        db.collection("users_patient_email").whereEqualTo("userId", patientId).get()
                            .addOnSuccessListener { patientEmails ->
                                val patientDoc = patientEmails.documents.firstOrNull()
                                patientDoc?.let {
                                    val patientName = it.getString("fullName") ?: "Unknown Patient"
                                    val patientPhone = it.getString("phoneNumber") ?: "No Phone"

                                    db.collection("doctor_photoprofile")
                                        .document(doctorUid)
                                        .get()
                                        .addOnSuccessListener { photoDoc ->
                                            val profileImageUrl =
                                                photoDoc.getString("photoUrl") ?: ""

                                            db.collection("chats")
                                                .document(doctorUid)
                                                .collection("patients")
                                                .document(patientId)
                                                .collection("messages")
                                                .get()
                                                .addOnSuccessListener { chatDoc ->
                                                    val latestMessage =
                                                        chatDoc.documents.firstOrNull()
                                                            ?.getString("message")
                                                            ?: "Start Message Now"
                                                    val latestDate = chatDoc.documents.firstOrNull()
                                                        ?.getString("timestamp") ?: ""

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
                                }
                            }
                    }
                }
                .addOnFailureListener { /* Handle error */ }
        }
    }


    private fun sendMessageToFirestore(message: String) {
        if (doctorUid == null || receiverUid == null) {
            showToast("Please wait for user data to load.")
            Log.d("Chatfragment", "Doctor UID: $doctorUid, Receiver UID: $receiverUid")
            return
        }
        val doctorName = this.doctorName
        val patientName = this.patientName
        val timestamp = System.currentTimeMillis()
        val dateFormat =
            java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(java.util.Date(timestamp))
        val messageData = mapOf(
            "senderName" to doctorName,
            "receiverName" to patientName,
            "message" to message,
            "timestamp" to formattedDate,
            "senderUid" to doctorUid,
            "receiverUid" to receiverUid
        )
        doctorUid?.let {
            FirebaseFirestore.getInstance().collection("chats")
                .document(it)
                .collection("patients")
                .document(receiverUid!!)
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener {
                    editTextMessage.text.clear()
                }
                .addOnFailureListener {
                    showToast("Failed to send message.")
                }
        }
    }


    private fun showNotification(context: Context, title: String, message: String) {
        val channelId = "chat_notifications"
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_assigned)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun listenForMessages() {
        val db = FirebaseFirestore.getInstance()

        doctorUid?.let { doctorUid ->
            receiverUid?.let { patientId ->
                db.collection("chats")
                    .document(doctorUid)
                    .collection("patients")
                    .document(patientId)
                    .collection("messages")
                    .orderBy("timestamp", Query.Direction.ASCENDING)
                    .addSnapshotListener { snapshots, e ->
                        if (e != null) {
                            Log.w("RoomChatFragment", "Listen failed.", e)
                            return@addSnapshotListener
                        }

                        if (snapshots != null && !snapshots.isEmpty) {
                            val newMessages = mutableListOf<Message>()

                            for (dc in snapshots.documentChanges) {
                                val message = dc.document.toObject(Message::class.java)

                                if (dc.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                                    newMessages.add(message)

                                    if (message.senderUid != doctorUid) {
                                        val safeContext = context ?: return@addSnapshotListener

                                        showNotification(
                                            safeContext,
                                            "Pesan baru dari ${message.senderName}",
                                            message.message
                                        )

                                    }
                                }
                            }

                            if (newMessages.isNotEmpty()) {
                                messageList.addAll(newMessages)
                                chatAdapter.updateMessages(messageList)
                                recyclerViewMessages.post {
                                    recyclerViewMessages.scrollToPosition(chatAdapter.itemCount - 1)
                                }
                            }
                        }
                    }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()

        activity?.window?.statusBarColor = resources.getColor(android.R.color.transparent, null)

        val bottomNavLayout = activity?.findViewById<RelativeLayout>(R.id.bottom_navigation)
        bottomNavLayout?.visibility = View.VISIBLE
    }

    private fun showToast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
}