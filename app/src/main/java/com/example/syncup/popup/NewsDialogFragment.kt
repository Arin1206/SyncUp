package com.example.syncup.popup

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.example.syncup.R
import com.example.syncup.model.News

class NewsDialogFragment(private val news: News) : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = Dialog(requireContext())
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_news_detail, null)
        dialog.setContentView(view)

        // Bind UI elements
        val newsImage = view.findViewById<ImageView>(R.id.newsImage)
        val newsTitle = view.findViewById<TextView>(R.id.newsTitle)
        val newsDescription = view.findViewById<TextView>(R.id.newsDescription)
        val closeButton = view.findViewById<Button>(R.id.closeButton)

        // Set data
        newsImage.setImageResource(news.imageRes)
        newsTitle.text = news.title
        newsDescription.text = "Ini adalah deskripsi detail dari berita ini. Tambahkan informasi lengkap di sini."

        // Close button listener
        closeButton.setOnClickListener {
            dismiss()
        }

        // Buat pop-up full rounded corner
        dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog)
        dialog.window?.setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        return dialog
    }
}
