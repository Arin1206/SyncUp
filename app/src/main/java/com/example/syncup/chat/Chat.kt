package com.example.syncup.chat

data class Chat(
    val doctorName: String,
    val message: String,
    val date: String,
    val profileImage: String? = null, // You can add an image URL here
    val doctorPhoneNumber: String,
    val doctorUid : String
)
