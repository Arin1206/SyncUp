package com.example.syncup.chat

data class Chat(
    val doctorName: String,
    var message: String,
    var date: String,
    val doctorEmail: String,
    val profileImage: String? = null,
    val doctorPhoneNumber: String,
    val doctorUid: String,
    var unreadCount: Int = 0,
    val isUnread: Boolean = false,
    var patientId: String,
    var patientName: String
) : java.io.Serializable
