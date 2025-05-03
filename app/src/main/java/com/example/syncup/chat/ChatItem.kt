package com.example.syncup.chat

sealed class ChatItem {
    data class DateLabel(val dateText: String) : ChatItem()
    data class ChatMessage(val message: Message) : ChatItem()
}
