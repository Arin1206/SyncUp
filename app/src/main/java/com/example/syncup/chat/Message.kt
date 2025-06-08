package com.example.syncup.chat

import com.google.firebase.firestore.IgnoreExtraProperties

@IgnoreExtraProperties
data class Message(
    val senderName: String = "",       // Default empty string for Firebase deserialization
    val receiverName: String = "",     // Default empty string for Firebase deserialization
    val message: String = "",          // Default empty string for Firebase gdeserialization
    val timestamp: String = "",       // Default empty strin for Firebase deserialization
    val senderUid: String = "",       // Default empty string for Firebase deserialization
    val receiverUid: String = ""      // Default empty string for Firebase deserialization
) {
    // No-argument constructor needed for Firebase
    constructor() : this("", "", "", "", "", "")
}
