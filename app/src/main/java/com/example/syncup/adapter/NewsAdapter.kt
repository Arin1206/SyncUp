package com.example.syncup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.model.News

class NewsAdapter(private val newsList: List<News>) :
    RecyclerView.Adapter<NewsAdapter.NewsViewHolder>() {

    inner class NewsViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val newsImage: ImageView = view.findViewById(R.id.news_image)
        val newsTitle: TextView = view.findViewById(R.id.news_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NewsViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_news_home, parent, false)
        return NewsViewHolder(view)
    }

    override fun onBindViewHolder(holder: NewsViewHolder, position: Int) {
        val news = newsList[position]
        holder.newsTitle.text = news.title
        holder.newsImage.setImageResource(news.imageRes)
    }

    override fun getItemCount(): Int = newsList.size
}
