package com.example.syncup.model

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R

class HealthAdapter(private var healthItemList: List<HealthItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_DATE_HEADER = 0
        private const val VIEW_TYPE_DATA_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return when (healthItemList[position]) {
            is HealthItem.DateHeader -> VIEW_TYPE_DATE_HEADER
            is HealthItem.DataItem -> VIEW_TYPE_DATA_ITEM
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_DATE_HEADER) {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_date_header, parent, false)
            DateHeaderViewHolder(view)
        } else {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_health_data, parent, false)
            DataViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = healthItemList[position]
        if (holder is DateHeaderViewHolder && item is HealthItem.DateHeader) {
            holder.dateHeader.text = item.date
        } else if (holder is DataViewHolder && item is HealthItem.DataItem) {
            holder.heartRate.text = "${item.healthData.heartRate} BPM"
            holder.bloodPressure.text = item.healthData.bloodPressure
            holder.batteryLevel.text = "${item.healthData.batteryLevel}%"
            holder.time.text = item.healthData.timestamp
        }
    }

    override fun getItemCount(): Int = healthItemList.size

    fun updateData(newList: List<HealthItem>) {
        healthItemList = newList
        notifyDataSetChanged()
    }

    class DateHeaderViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dateHeader: TextView = itemView.findViewById(R.id.tv_date_header)
    }

    class DataViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val heartRate: TextView = itemView.findViewById(R.id.tv_heart_rate)
        val bloodPressure: TextView = itemView.findViewById(R.id.tv_blood_pressure)
        val batteryLevel: TextView = itemView.findViewById(R.id.tv_battery)
        val time: TextView = itemView.findViewById(R.id.tv_time)
    }
}
