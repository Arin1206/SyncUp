package com.example.syncup.inbox

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

class InboxDoctorFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: NotificationAdapter
    private val notifications = mutableListOf<Notification>()

    @SuppressLint("MissingInflatedId")
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_inbox_doctor, container, false)
        recyclerView = view.findViewById(R.id.recyclerView)

        adapter = NotificationAdapter(notifications)
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        // 🔽 Panggil fungsi untuk dengarkan perubahan data Firestore
        listenToNotifications()

        return view
    }

    private fun listenToNotifications() {
        val db = FirebaseFirestore.getInstance()
        val currentUser = FirebaseAuth.getInstance().currentUser
        val userId = currentUser?.uid ?: return

        db.collection("notifications")
            .whereEqualTo("userId", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Toast.makeText(requireContext(), "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                    return@addSnapshotListener
                }

                notifications.clear()

                if (snapshots != null && !snapshots.isEmpty) {
                    for (doc in snapshots) {
                        notifications.add(
                            Notification(
                                title = "Notifikasi Email",
                                message = "Thank you for contacting us! We’ve successfully received your email and are currently reviewing your inquiry. One of our team members will get back to you as soon as possible. Check your inbox Gmail soon"
                            )
                        )
                    }
                } else {

                }

                adapter.updateData(notifications)
            }
    }


}