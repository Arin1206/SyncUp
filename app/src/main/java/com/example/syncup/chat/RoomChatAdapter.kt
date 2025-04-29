package com.example.syncup.chat

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import java.text.SimpleDateFormat
import java.util.*

class RoomChatAdapter(
    private val messageList: List<Message>,  // The list of messages to display
    private val currentUserUid: String // To differentiate between sender and receiver
) : RecyclerView.Adapter<RoomChatAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messageList[position]

        // Set the message text and timestamp
        holder.messageText.text = message.message

        // Format the timestamp to show only the hour and minute (HH:mm)
        val timestamp = message.timestamp
        val parsedDate = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(timestamp)
        val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsedDate)
        holder.timestampText.text = formattedTime

        // Adjust the width of the message layout dynamically based on content length, capped at 150dp max
        holder.messageText.layoutParams.width = ViewGroup.LayoutParams.WRAP_CONTENT

        // Align and set background color depending on whether the message is from the current user or the receiver
        if (message.senderUid == currentUserUid) {
            // Sent message - Align right and set red background
            holder.messageLayout.setBackgroundResource(R.drawable.bg_red_box_message) // Background for sender
            holder.messageText.setTextColor(Color.BLACK)
            holder.timestampText.setTextColor(Color.BLACK)

            holder.messageLayout.layoutParams = (holder.messageLayout.layoutParams as ConstraintLayout.LayoutParams).apply {
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID  // Align message to the right (entire message layout)
                startToStart = -1  // Disable left alignment
            }

            holder.messageText.gravity = Gravity.END // Ensure the text is aligned to the right
        } else {
            // Received message - Align left and set green background
            holder.messageLayout.setBackgroundResource(R.drawable.bg_green_box_message) // Background for receiver
            holder.messageText.setTextColor(Color.BLACK)

            holder.messageLayout.layoutParams = (holder.messageLayout.layoutParams as ConstraintLayout.LayoutParams).apply {
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID // Align message to the left
                endToEnd = -1 // Disable right alignment
            }
        }
    }

    override fun getItemCount(): Int {
        return messageList.size
    }

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val messageLayout: ConstraintLayout = itemView.findViewById(R.id.message_layout)
        val messageText: TextView = itemView.findViewById(R.id.message_text)
        val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)
    }
}
