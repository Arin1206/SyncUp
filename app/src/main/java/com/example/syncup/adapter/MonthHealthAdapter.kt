package com.example.syncup.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.model.MonthHealthItem

class MonthHealthAdapter(private var monthData: List<MonthHealthItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (monthData[position] is MonthHealthItem.MonthHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_month_header, parent, false)
                MonthHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_health_data, parent, false)
                MonthViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = monthData[position]
        when (holder) {
            is MonthHeaderViewHolder -> holder.bind(data as MonthHealthItem.MonthHeader)
            is MonthViewHolder -> holder.bind(data as MonthHealthItem.MonthData)
        }
    }

    override fun getItemCount(): Int {
        return monthData.size
    }

    fun updateData(newData: List<MonthHealthItem>) {
        monthData = newData
        notifyDataSetChanged()
    }

    class MonthHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val monthHeader: TextView = view.findViewById(R.id.tv_month_header)
        fun bind(item: MonthHealthItem.MonthHeader) {
            monthHeader.text = item.month
        }
    }

    class MonthViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avgHeartRate: TextView = view.findViewById(R.id.tv_heart_rate)
        private val avgBloodPressure: TextView = view.findViewById(R.id.tv_blood_pressure)
        private val avgBattery: TextView = view.findViewById(R.id.tv_battery)

        fun bind(item: MonthHealthItem.MonthData) {
            avgHeartRate.text = item.avgHeartRate.toString()
            avgBloodPressure.text = item.avgBloodPressure
            avgBattery.text = "${item.avgBattery}%"
        }
    }
}
