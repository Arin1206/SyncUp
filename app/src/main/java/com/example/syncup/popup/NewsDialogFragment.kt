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
        val closeButton = view.findViewById<TextView>(R.id.closeButton)

        // Set data
        newsImage.setImageResource(news.imageRes)
        newsTitle.text = news.title
        // Set deskripsi sesuai indeks
        val descriptions = listOf(
            "Penelitian terbaru mengungkap bahwa penyintas Covid-19 memiliki risiko lebih tinggi mengalami kerusakan jantung, bahkan setelah sembuh. Studi ini menunjukkan adanya perubahan pada struktur dan fungsi jantung yang dapat berdampak jangka panjang.",
            "Menjaga kesehatan jantung bisa dilakukan dengan menerapkan pola hidup sehat. Mulai dari konsumsi makanan bergizi, rutin berolahraga, hingga mengelola stres. Simak tips dan kebiasaan sehat untuk menjaga jantung tetap prima.",
            "Dengan kemajuan teknologi medis, deteksi dini penyakit jantung semakin mudah. Berbagai inovasi seperti wearable devices dan AI dalam diagnosis jantung membantu tenaga medis memberikan perawatan yang lebih efektif dan cepat."
        )

        newsDescription.text = descriptions[news.index]


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
