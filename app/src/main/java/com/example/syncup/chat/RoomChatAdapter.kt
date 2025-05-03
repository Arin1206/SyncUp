package com.example.syncup.chat

import android.graphics.Color
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import java.text.SimpleDateFormat
import java.util.*

class RoomChatAdapter(
    private val messageList: List<Message>,
    private val currentUserUid: String
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {

        private const val TYPE_DATE_LABEL = 0
        private const val TYPE_MESSAGE = 1
    }

    // Build unified list with header and item
    private val chatItemList: MutableList<ChatItem> = mutableListOf()


    override fun getItemCount(): Int = chatItemList.size

    override fun getItemViewType(position: Int): Int {
        return when (chatItemList[position]) {
            is ChatItem.DateLabel -> TYPE_DATE_LABEL
            is ChatItem.ChatMessage -> TYPE_MESSAGE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            TYPE_DATE_LABEL -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_date_label, parent, false)
                DateLabelViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_message, parent, false)
                MessageViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = chatItemList[position]) {
            is ChatItem.DateLabel -> (holder as DateLabelViewHolder).bind(item)
            is ChatItem.ChatMessage -> (holder as MessageViewHolder).bind(item.message, currentUserUid)
        }
    }

    private fun buildChatItemList(): List<ChatItem> {
        val result = mutableListOf<ChatItem>()
        var lastDateLabel: String? = null

        val sortedMessages = messageList.sortedBy {
            try {
                SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(it.timestamp)
            } catch (e: Exception) {
                null // Return null for invalid timestamps
            }
        }

        for (message in sortedMessages) {
            val label = getMessageDateLabel(message.timestamp)
            if (label != lastDateLabel) {
                result.add(ChatItem.DateLabel(label))
                lastDateLabel = label
            }
            result.add(ChatItem.ChatMessage(message))
        }
        return result
    }


    private fun getMessageDateLabel(timestamp: String): String {
        if (timestamp.isEmpty()) {
            return "Unknown Date"
        }

        val parsedDate = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(timestamp)
        val currentDate = Calendar.getInstance()
        val messageDate = Calendar.getInstance().apply { time = parsedDate }

        return when {
            currentDate.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) &&
                    currentDate.get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR) -> "Today"

            currentDate.apply { add(Calendar.DATE, -1) }
                .get(Calendar.DAY_OF_YEAR) == messageDate.get(Calendar.DAY_OF_YEAR) &&
                    currentDate.get(Calendar.YEAR) == messageDate.get(Calendar.YEAR) -> "Yesterday"

            else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(parsedDate)
        }
    }


    // ViewHolder untuk pesan
    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val messageLayout: ConstraintLayout = itemView.findViewById(R.id.message_layout)
        private val messageText: TextView = itemView.findViewById(R.id.message_text)
        private val timestampText: TextView = itemView.findViewById(R.id.timestamp_text)

        fun bind(message: Message, currentUserUid: String) {
            messageText.text = message.message
            val timestamp = message.timestamp

            // Check if timestamp is not empty before parsing
            if (timestamp.isNotEmpty()) {
                try {
                    val parsedDate = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(timestamp)
                    val formattedTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(parsedDate)
                    timestampText.text = formattedTime
                } catch (e: Exception) {
                    // Handle the exception if parsing fails
                    timestampText.text = "Invalid Date"
                }
            } else {
                timestampText.text = "No Timestamp"  // Fallback text when no timestamp is available
            }

            if (message.senderUid == currentUserUid) {
                messageLayout.setBackgroundResource(R.drawable.bg_red_box_message)
                messageText.setTextColor(Color.BLACK)
                timestampText.setTextColor(Color.BLACK)
                messageLayout.layoutParams = (messageLayout.layoutParams as ConstraintLayout.LayoutParams).apply {
                    endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                    startToStart = -1
                }
                messageText.gravity = Gravity.END
            } else {
                messageLayout.setBackgroundResource(R.drawable.bg_green_box_message)
                messageText.setTextColor(Color.BLACK)
                messageLayout.layoutParams = (messageLayout.layoutParams as ConstraintLayout.LayoutParams).apply {
                    startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                    endToEnd = -1
                }
                timestampText.setTextColor(Color.BLACK)
                messageText.gravity = Gravity.START
            }
        }

    }

    // ViewHolder untuk header (label tanggal)
    class DateLabelViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val dateLabel: TextView = itemView.findViewById(R.id.date_label)

        fun bind(item: ChatItem.DateLabel) {
            dateLabel.text = item.dateText
        }
    }
    fun updateMessages(newMessages: List<Message>) {
        val sortedMessages = newMessages.sortedBy {
            SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale.getDefault()).parse(it.timestamp)
        }

        val newChatItemList = mutableListOf<ChatItem>()
        var lastLabel: String? = null
        for (message in sortedMessages) {
            val label = getMessageDateLabel(message.timestamp)
            if (label != lastLabel) {
                newChatItemList.add(ChatItem.DateLabel(label))
                lastLabel = label
            }
            newChatItemList.add(ChatItem.ChatMessage(message))
        }

        (chatItemList as MutableList).apply {
            clear()
            addAll(newChatItemList)
        }

        notifyDataSetChanged()
    }

}