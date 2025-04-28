package com.example.syncup.chat

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R

class ChatAdapter(
    private val chatList: List<Chat>,
    private val onItemClick: (String, String) -> Unit  // Function to handle item click
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
            // When an item is clicked, navigate to RoomChatFragment
            onItemClick(chat.doctorName, chat.doctorPhoneNumber)
        }
    }

    override fun getItemCount(): Int {
        return chatList.size
    }

    inner class ChatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val doctorName: TextView = itemView.findViewById(R.id.doctor_name)
        private val chatMessage: TextView = itemView.findViewById(R.id.chat_message)
        private val chatDate: TextView = itemView.findViewById(R.id.chat_date)
        private val profileImage: ImageView = itemView.findViewById(R.id.profile_image)

        fun bind(chat: Chat) {
            doctorName.text = chat.doctorName

            // Jika message adalah "Start Message Now", sembunyikan tanggal
            if (chat.message == "Start Message Now") {
                chatMessage.text = "Start Message Now"
                chatDate.visibility = View.GONE  // <-- Sembunyikan tanggal
            } else {
                chatMessage.text = chat.message
                chatDate.text = chat.date
                chatDate.visibility = View.VISIBLE  // <-- Tampilkan tanggal kalau ada pesan beneran
            }

            // Set the profile image
            profileImage.setImageResource(R.drawable.empty_image)
        }

    }
}
