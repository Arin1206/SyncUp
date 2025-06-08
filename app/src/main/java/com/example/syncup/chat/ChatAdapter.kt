package com.example.syncup.chat

import android.graphics.Typeface
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.CircleCrop
import com.example.syncup.R


class ChatAdapter(
    private var chatList: List<Chat>,
    private val onItemClick: (String, String, String, String, String, String) -> Unit
) : RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat, parent, false)
        return ChatViewHolder(view)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val chat = chatList[position]
        holder.bind(chat)

        // Set the click listener on the item view
        holder.itemView.setOnClickListener {
            chat.profileImage?.let { it1 ->
                onItemClick(chat.doctorName, chat.doctorPhoneNumber, chat.doctorUid, chat.patientId, chat.patientName,
                    it1
                )
            }  // Pass doctorUid
        }
    }

    override fun getItemCount(): Int {
        return chatList.size
    }
    fun updateList(newList: List<Chat>) {
        chatList = newList
        notifyDataSetChanged()
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val doctorName: TextView = itemView.findViewById(R.id.doctor_name)
        private val chatMessage: TextView = itemView.findViewById(R.id.chat_message)
        private val chatDate: TextView = itemView.findViewById(R.id.chat_date)
        private val profileImage: ImageView = itemView.findViewById(R.id.profile_image)
        val unreadBadge: TextView = itemView.findViewById(R.id.unread_badge)

        fun bind(chat: Chat) {
            doctorName.text = chat.doctorName  // Displaying patient's name

            if (chat.message == "Start Message Now") {
                chatMessage.text = "Start Message Now"
                chatDate.visibility = View.GONE
            } else {
                chatMessage.text = chat.message
                chatDate.text = chat.date
                chatDate.visibility = View.VISIBLE
            }

            if (chat.isUnread) {
                chatMessage.setTypeface(null, Typeface.BOLD)
                chatMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.purple_dark))
                unreadBadge.visibility = View.VISIBLE
                unreadBadge.text = chat.unreadCount.toString()
            } else {
                chatMessage.setTypeface(null, Typeface.NORMAL)
                chatMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.black))
                unreadBadge.visibility = View.GONE
            }

            // Set default profile image
            Glide.with(itemView.context)
                .load(chat.profileImage)  // Load profile image URL
                .placeholder(R.drawable.account_circle)  // Placeholder image
                .transform(CircleCrop())  // Apply circular crop
                .into(profileImage)
        }
    }
}


