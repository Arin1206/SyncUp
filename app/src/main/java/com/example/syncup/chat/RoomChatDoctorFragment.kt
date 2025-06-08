package com.example.syncup.chat

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Point
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.syncup.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query


class RoomChatDoctorFragment : Fragment() {

    private lateinit var doctorNameTextView: TextView
    private lateinit var messageTextView: TextView
    private lateinit var sendMessageLayout: ConstraintLayout
    private lateinit var doctorName: String
    private lateinit var patientName: String
    private lateinit var doctorPhoneNumber: String
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageView
    private lateinit var arrow: ImageView
    private var receiverUid: String? = null
    private var senderUid: String? = null
    private lateinit var recyclerViewMessages: RecyclerView
    private val chatList = mutableListOf<Chat>()
    private val messageList = mutableListOf<Message>()
    private lateinit var chatAdapter: RoomChatAdapter
    private var currentUserUid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var userName: String? = null // Make this nullable to avoid accessing before initialization
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

        // Adjust padding based on window insets, but only for the bottom (not top)
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

        // Get doctor name and phone number from arguments
        doctorName = arguments?.getString("doctor_name") ?: "Unknown"
        patientName = arguments?.getString("patientName") ?: "Unknown"
        doctorPhoneNumber = arguments?.getString("doctor_phone_number") ?: "Unknown"
        receiverUid = arguments?.getString("receiverUid")
        profileImageUrl = arguments?.getString("profileImage")

// Load image ke ImageView
        Glide.with(this)
            .load(profileImageUrl)
            .placeholder(R.drawable.account_circle)
            .circleCrop() // 👉 ini bikin gambarnya bulat
            .into(profileImageView)
        // Set doctor name
        doctorNameTextView.text = doctorName

        chatAdapter = RoomChatAdapter(messageList, doctorUid ?: "Unknown")
        recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewMessages.adapter = chatAdapter



        listenForMessages()
        arrow.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        activity?.window?.statusBarColor = resources.getColor(R.color.purple_dark, null)

        // Set up send message button click listener
        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString()
            if (message.isNotEmpty() && receiverUid != null) {
                sendMessageToFirestore(message)
            } else {
                // Handle case where userName is still null (not fetched yet)
                showToast("Please wait for user data to load.")
            }
        }

        fetchDoctorsData()
        sendMessageLayout = view.findViewById(R.id.sendMessageLayout)

        // Hide Bottom Navigation when this fragment is loaded
        val bottomNavLayout = activity?.findViewById<RelativeLayout>(R.id.bottom_navigation)
        bottomNavLayout?.visibility = View.GONE

        return view
    }

    private fun formatNomorTelepon(phone: String): String {
        // Implement your phone number formatting logic here, if necessary
        return phone.replace("-", "").trim()
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
        val currentUserUid = FirebaseAuth.getInstance().currentUser?.uid ?: return

        chatList.clear()  // Clear previous chat data
        chatAdapter.notifyDataSetChanged()

        // Fetch the doctor's assigned patients (from assigned_patient_ids)
        getActualDoctorUID { doctorUid ->
            if (doctorUid == null) {
                return@getActualDoctorUID
            }

            // Get list of patient IDs assigned to the doctor
            db.collection("assigned_patient")
                .whereEqualTo("doctorUid", doctorUid)
                .get()
                .addOnSuccessListener { result ->
                    result.forEach { document ->
                        val patientId = document.getString("patientId") ?: return@forEach

                        // Fetch the patient details from "users_patient_email" or "users_patient_phonenumber"
                        db.collection("users_patient_email").whereEqualTo("userId", patientId).get()
                            .addOnSuccessListener { patientEmails ->
                                val patientDoc = patientEmails.documents.firstOrNull()
                                patientDoc?.let {
                                    val patientName = it.getString("fullName") ?: "Unknown Patient"
                                    val patientPhone = it.getString("phoneNumber") ?: "No Phone"

                                    // Fetch profile image URL from the patient_photoprofile collection
                                    db.collection("doctor_photoprofile")
                                        .document(doctorUid)
                                        .get()
                                        .addOnSuccessListener { photoDoc ->
                                            val profileImageUrl = photoDoc.getString("photoUrl") ?: ""

                                            // Check if a chat exists for this patient and doctor
                                            db.collection("chats")
                                                .document(doctorUid)
                                                .collection("patients")
                                                .document(patientId)
                                                .collection("messages")
                                                .get()
                                                .addOnSuccessListener { chatDoc ->
                                                    val latestMessage = chatDoc.documents.firstOrNull()
                                                        ?.getString("message") ?: "Start Message Now"
                                                    val latestDate = chatDoc.documents.firstOrNull()
                                                        ?.getString("timestamp") ?: ""

                                                    val chat = Chat(
                                                        doctorName = patientName,  // Now showing patient's name
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
        // Ensure doctorUid and receiverUid (patientId) are set
        if (doctorUid == null || receiverUid == null) {
            showToast("Please wait for user data to load.")
            Log.d("Chatfragment", "Doctor UID: $doctorUid, Receiver UID: $receiverUid")
            return
        }

        // Assuming userName is set to doctorName when fetched earlier
        val doctorName = this.doctorName
        val patientName = this.patientName ?: "Unknown Patient"  // Assuming userName is fetched from the patient data
        val timestamp = System.currentTimeMillis()

        // Format the timestamp to "dd MMM yyyy, HH:mm:ss"
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(java.util.Date(timestamp))

        val currentUser = FirebaseAuth.getInstance().currentUser
        val senderUid = currentUser?.uid ?: "Unknown" // Doctor's Firebase UID

        // Create the message data including sender and receiver info
        val messageData = mapOf(
            "senderName" to doctorName,  // Doctor's name as sender
            "receiverName" to patientName,  // Patient's name as receiver
            "message" to message,  // The message content
            "timestamp" to formattedDate,  // Formatted timestamp
            "senderUid" to doctorUid,  // Sender's UID (Doctor's UID)
            "receiverUid" to receiverUid  // Receiver's UID (Patient's ID)
        )

        // Send message to Firestore in the correct chat collection
        doctorUid?.let {
            FirebaseFirestore.getInstance().collection("chats")
                .document(it)  // Use doctorUid to identify chat for the specific doctor
                .collection("patients")
                .document(receiverUid!!)  // Use patientId as the document ID
                .collection("messages")
                .add(messageData)
                .addOnSuccessListener {
                    // Clear the message field after successfully sending the message
                    editTextMessage.text.clear()
                }
                .addOnFailureListener {
                    // Handle error if message sending fails
                    showToast("Failed to send message.")
                }
        }
    }


    fun showNotification(context: Context, title: String, message: String) {
        val channelId = "chat_notifications"
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Chat Messages",
                NotificationManager.IMPORTANCE_HIGH
            )
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_assigned) // sesuaikan icon kamu
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

                                    // Notifikasi hanya untuk pesan yang bukan dari dokter (dokter menerima pesan dari pasien)
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



    private fun showEmptyRoomChat() {
        // You can display a placeholder or show a message to indicate that the chat is empty
        val emptyMessage = Message(
            senderName = "System",
            receiverName = doctorName,
            message = "Start the conversation now!",
            timestamp = "",
            senderUid = "system",
            receiverUid = receiverUid ?: ""
        )
        // Add an empty message to the chat view or show a placeholder text
        val emptyMessageList = listOf(emptyMessage)
        chatAdapter.updateMessages(emptyMessageList)
        recyclerViewMessages.post {
            recyclerViewMessages.scrollToPosition(chatAdapter.itemCount - 1)
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