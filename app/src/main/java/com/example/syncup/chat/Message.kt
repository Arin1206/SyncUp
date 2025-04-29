package com.example.syncup.chat

data class Message(
    val senderName: String,
    val receiverName: String,
    val message: String,
    val timestamp: String,
    val senderUid: String,
    val receiverUid: String
)
