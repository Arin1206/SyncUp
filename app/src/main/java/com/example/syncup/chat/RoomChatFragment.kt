package com.example.syncup.chat

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import com.google.firebase.firestore.Query

class RoomChatFragment : Fragment() {

    private lateinit var doctorNameTextView: TextView
    private lateinit var doctorName: String
    private lateinit var doctorPhoneNumber: String
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageView
    private lateinit var arrow: ImageView
    private var receiverUid: String? = null
    private lateinit var recyclerViewMessages: RecyclerView
    private val messageList = mutableListOf<Message>()
    private lateinit var chatAdapter: RoomChatAdapter
    private var userName: String? = null
    private var patientid: String? = null
    private var doctoruid: String? = null
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
        doctorName = arguments?.getString("doctor_name") ?: "Unknown"
        doctorPhoneNumber = arguments?.getString("doctor_phone_number") ?: "Unknown"
        receiverUid = arguments?.getString("patientId")
        patientid = arguments?.getString("patientId")
        doctoruid = arguments?.getString("receiverUid")
        profileImageUrl = arguments?.getString("profileImage")

        Glide.with(this)
            .load(profileImageUrl)
            .placeholder(R.drawable.account_circle)
            .circleCrop()
            .into(profileImageView)

        doctorNameTextView.text = doctorName

        chatAdapter = RoomChatAdapter(messageList, patientid ?: "Unknown")
        recyclerViewMessages.layoutManager = LinearLayoutManager(requireContext())
        recyclerViewMessages.adapter = chatAdapter

        fetchUserData()


        listenForMessages()
        arrow.setOnClickListener {
            requireActivity().supportFragmentManager.popBackStack()
        }
        activity?.window?.statusBarColor = resources.getColor(R.color.purple_dark, null)

        buttonSend.setOnClickListener {
            val message = editTextMessage.text.toString()
            if (message.isNotEmpty() && userName != null) {
                sendMessageToFirestore(message)
            } else {

                showToast("Please wait for user data to load.")
            }
        }

        val bottomNavLayout = activity?.findViewById<RelativeLayout>(R.id.bottom_navigation)
        bottomNavLayout?.visibility = View.GONE

        return view
    }

    private fun formatNomorTelepon(phone: String): String {
        return if (phone.startsWith("+62")) {
            "0" + phone.substring(3) // Replace +62 with 0
        } else {
            phone.replace("-", "").trim() // Clean up any hyphens and trim the number
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
                if (!documents.isEmpty) {
                    val doc = documents.first()
                    this.userName = doc.getString("fullName") ?: "Unknown Name"
                    this.patientid = doc.getString("userId") ?: ""
                    Log.d("RoomChatFragment", "Fetched userName: $userName, patientId: $patientid")
                }
            }
    }



    fun showNotification(context: Context, title: String, message: String) {
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

    private fun sendMessageToFirestore(message: String) {
        if (userName.isNullOrEmpty() || patientid.isNullOrEmpty() || doctoruid.isNullOrEmpty()) {
            showToast("Data belum lengkap. Coba lagi.")
            return
        }
        val timestamp = System.currentTimeMillis()
        val dateFormat =
            java.text.SimpleDateFormat("dd MMM yyyy, HH:mm:ss", java.util.Locale.getDefault())
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
        doctoruid?.let { doctorUid ->
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
                                    if (message.message.contains("Latitude") && message.message.contains(
                                            "Longitude"
                                        )
                                    ) {
                                        showLocationOnMap(message.message)
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

    private fun showLocationOnMap(locationMessage: String) {
        val regex = "Latitude: ([-+]?\\d*\\.\\d+), Longitude: ([-+]?\\d*\\.\\d+)".toRegex()
        val matchResult = regex.find(locationMessage)

        if (matchResult != null) {
            val lat = matchResult.groupValues[1]
            val lon = matchResult.groupValues[2]

            val uri = Uri.parse("geo:$lat,$lon")
            val intent = Intent(Intent.ACTION_VIEW, uri)
            intent.setPackage("com.google.android.apps.maps")
            startActivity(intent)
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
