package com.example.syncup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R

class DotIndicatorAdapter(private val itemCount: Int, private var selectedIndex: Int) :
    RecyclerView.Adapter<DotIndicatorAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dotImage: ImageView = view.findViewById(R.id.dotImage)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dot_indicator, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (position == selectedIndex) {
            holder.dotImage.setImageResource(R.drawable.dot_indicator_active)
        } else {
            holder.dotImage.setImageResource(R.drawable.dot_indicator)
        }
    }

    override fun getItemCount(): Int = itemCount

    fun updateSelectedIndex(newIndex: Int) {
        selectedIndex = newIndex
        notifyDataSetChanged()
    }
}
