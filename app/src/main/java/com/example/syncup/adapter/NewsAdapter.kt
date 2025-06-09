package com.example.syncup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.model.News
import com.example.syncup.popup.NewsDialogFragment


class NewsAdapter(private val newsList: List<News>, private val activity: FragmentActivity) :
    RecyclerView.Adapter<NewsAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val newsImage: ImageView = view.findViewById(R.id.news_image)
        val newsTitle: TextView = view.findViewById(R.id.news_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_home, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val news = newsList[position]
        holder.newsImage.setImageResource(news.imageRes)
        holder.newsTitle.text = news.title

        holder.itemView.setOnClickListener {
            NewsDialogFragment(news).show(activity.supportFragmentManager, "NewsDialog")
        }
    }

    override fun getItemCount(): Int = newsList.size
}
