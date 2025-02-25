package com.example.syncup.adapter

import HealthData
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.model.WeekHealthItem

class WeekHealthAdapter(private var items: List<WeekHealthItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is WeekHealthItem.WeekHeader -> VIEW_TYPE_HEADER
            is WeekHealthItem.DataItem -> VIEW_TYPE_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_week_header, parent, false)
                WeekHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_health_data, parent, false)
                DataViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is WeekHealthItem.WeekHeader -> (holder as WeekHeaderViewHolder).bind(item)
            is WeekHealthItem.DataItem -> (holder as DataViewHolder).bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<WeekHealthItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    class WeekHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val weekTitleTextView: TextView = view.findViewById(R.id.tv_week_header)

        fun bind(item: WeekHealthItem.WeekHeader) {
            weekTitleTextView.text = item.weekTitle
        }
    }

    class DataViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val heartRateTextView: TextView = view.findViewById(R.id.tv_heart_rate)
        private val bloodPressureTextView: TextView = view.findViewById(R.id.tv_blood_pressure)
        private val batteryTextView: TextView = view.findViewById(R.id.tv_battery)

        fun bind(item: WeekHealthItem.DataItem) {
            val healthData = item.healthData
            heartRateTextView.text = "${healthData.heartRate} BPM"
            bloodPressureTextView.text = healthData.bloodPressure
            batteryTextView.text = "${healthData.batteryLevel}%"
        }
    }
}
