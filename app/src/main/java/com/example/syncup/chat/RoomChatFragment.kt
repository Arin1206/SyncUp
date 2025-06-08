package com.example.syncup.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
import androidx.core.app.NotificationCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
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
    private lateinit var arrow: ImageView
    private var receiverUid: String? = null
    private var senderUid: String? = null
    private lateinit var recyclerViewMessages: RecyclerView
    private val messageList = mutableListOf<Message>()
    private lateinit var chatAdapter: RoomChatAdapter
    private var currentUserUid: String? = FirebaseAuth.getInstance().currentUser?.uid
    private var userName: String? = null // Make this nullable to avoid accessing before initialization
    private var patientid: String? = null // Make this nullable to avoid accessing before initialization
    private var doctoruid:  String? = null // Make this nullable to avoid accessing before initialization
    private lateinit var profileImageView: ImageView
    private var profileImageUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_room_chat, container, false)

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(0, 0, 0, bottom)
            insets
        }
        doctorNameTextView = view.findViewById(R.id.doctor_name)
        editTextMessage = view.findViewById(R.id.editTextMessage)
        buttonSend = view.findViewById(R.id.buttonSend)
        profileImageView = view.findViewById(R.id.profile_image)
        recyclerViewMessages = view.findViewById(R.id.recyclerViewMessages)
        arrow = view.findViewById(R.id.arrow)

        // Get doctor name and phone number from arguments
        doctorName = arguments?.getString("doctor_name") ?: "Unknown"
        doctorPhoneNumber = arguments?.getString("doctor_phone_number") ?: "Unknown" // Get phone number
        receiverUid = arguments?.getString("patientId")
        patientid = arguments?.getString("patientId")
        doctoruid = arguments?.getString("receiverUid")
        profileImageUrl = arguments?.getString("profileImage")

// Load image ke ImageView
        Glide.with(this)
            .load(profileImageUrl)
            .placeholder(R.drawable.account_circle)
            .circleCrop() // 👉 ini bikin gambarnya bulat
            .into(profileImageView)

        // Set doctor name
        doctorNameTextView.text = doctorName

        chatAdapter = RoomChatAdapter(messageList, patientid ?: "Unknown")
        recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewMessages.adapter = chatAdapter

        // Fetch logged-in user's full name using userId
        fetchUserData()

        listenForMessages()
        arrow.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
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
    private fun fetchUserData() {
        val currentUser = FirebaseAuth.getInstance().currentUser ?: return

        val userEmail = currentUser.email
        val userPhone = currentUser.phoneNumber?.let { formatNomorTelepon(it) }

        val (collection, field, identifier) = when {
            !userEmail.isNullOrEmpty() -> Triple("users_patient_email", "email", userEmail)
            !userPhone.isNullOrEmpty() -> Triple("users_patient_phonenumber", "phoneNumber", userPhone)
            else -> return
        }

        FirebaseFirestore.getInstance()
            .collection(collection)
            .whereEqualTo(field, identifier)
            .get()
            .addOnSuccessListener { documents ->
                if (!documents.isEmpty) {  // ✅ INI BENAR
                    val doc = documents.first()
                    this.userName = doc.getString("fullName") ?: "Unknown Name"
                    this.patientid = doc.getString("userId") ?: ""
                    Log.d("RoomChatFragment", "Fetched userName: $userName, patientId: $patientid")
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
    private fun sendMessageToFirestore(message: String) {
        if (userName.isNullOrEmpty() || patientid.isNullOrEmpty() || doctoruid.isNullOrEmpty()) {
            showToast("Data belum lengkap. Coba lagi.")
            return
        }

        val timestamp = System.currentTimeMillis()
        val dateFormat = java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
        val formattedDate = dateFormat.format(java.util.Date(timestamp))

        val messageData = mapOf(
            "senderName" to userName,
            "receiverName" to doctorName,
            "message" to message,
            "timestamp" to formattedDate,
            "senderUid" to patientid!!,
            "receiverUid" to doctoruid!!
        )

        FirebaseFirestore.getInstance()
            .collection("chats")
            .document(doctoruid!!)
            .collection("patients")
            .document(patientid!!)
            .collection("messages")
            .add(messageData)
            .addOnSuccessListener {
                editTextMessage.text.clear()
            }
            .addOnFailureListener {
                showToast("Failed to send message.")
            }
    }


    private fun listenForMessages() {
        val db = FirebaseFirestore.getInstance()

        // Fetch the actual patient UID
        getActualPatientUID { currentPatientUid ->
            if (currentPatientUid == null || doctoruid == null) {
                Log.e("RoomChatFragment", "patientId or doctorUid (receiverUid) is null")
                return@getActualPatientUID
            }

            Log.d("RoomChatfragment", "Doctor $doctoruid")

            // Listen for messages in the specific chat between the patient and the doctor
            db.collection("chats")                // "chats" is the collection
                .document(doctoruid!!)            // Document for a specific doctor
                .collection("patients")           // Subcollection for patients under the doctor
                .document(patientid!!)            // Document for the specific patient
                .collection("messages")           // Messages subcollection for the patient
                .orderBy("timestamp")             // Order messages by timestamp
                .addSnapshotListener { snapshot, e ->
                    if (e != null) {
                        Log.w("RoomChatFragment", "Listen failed.", e)
                        return@addSnapshotListener
                    }

                    if (snapshot != null && !snapshot.isEmpty) {
                        val newMessages = mutableListOf<Message>()

                        for (doc in snapshot.documents) {
                            val message = doc.toObject(Message::class.java)
                            if (message != null) {
                                newMessages.add(message)

                                // Only show notification if the message sender is not the current patient
                                if (isAdded && context != null && message.senderUid != currentPatientUid) {
                                    showNotification(requireContext(), "New Message", message.message)
                                }
                            }
                        }

                        // Update the adapter with new messages
                        chatAdapter.updateMessages(newMessages)
                        recyclerViewMessages.post {
                            recyclerViewMessages.scrollToPosition(chatAdapter.itemCount - 1)
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
