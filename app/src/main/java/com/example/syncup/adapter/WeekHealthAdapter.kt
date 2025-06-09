package com.example.syncup.adapter

import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.model.WeekHealthItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)
        if (holder is DataViewHolder) {
            holder.stopUpdatingTime()
        }
    }

    class DataViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val heartRateTextView: TextView = view.findViewById(R.id.tv_heart_rate)
        private val bloodPressureTextView: TextView = view.findViewById(R.id.tv_blood_pressure)
        private val batteryTextView: TextView = view.findViewById(R.id.tv_battery)
        private val timeTextView: TextView = view.findViewById(R.id.tv_time)
        private val indicatorStatus: View = view.findViewById(R.id.indicator_status)

        private val handler = Handler(Looper.getMainLooper())
        private val updateTimeRunnable = object : Runnable {
            override fun run() {
                timeTextView.text = formatTime()
                handler.postDelayed(this, 1000)
            }
        }

        fun bind(item: WeekHealthItem.DataItem) {
            val healthData = item.healthData
            heartRateTextView.text = "${healthData.heartRate} BPM"
            bloodPressureTextView.text = healthData.bloodPressure
            batteryTextView.text = "${healthData.batteryLevel}%"

            handler.removeCallbacks(updateTimeRunnable)
            handler.post(updateTimeRunnable)

            val context = indicatorStatus.context
            val statusColor = if (healthData.heartRate in 60..100) {
                ContextCompat.getColor(context, R.color.green)  // **Healthy**
            } else {
                ContextCompat.getColor(context, R.color.red)  // **Danger**
            }
            indicatorStatus.setBackgroundColor(statusColor)
        }

        fun stopUpdatingTime() {
            handler.removeCallbacks(updateTimeRunnable)
        }

        private fun formatTime(): String {
            return try {
                val currentTime = System.currentTimeMillis()
                val outputFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                outputFormat.format(Date(currentTime))
            } catch (e: Exception) {
                Log.e("WeekHealthAdapter", "Error getting current time", e)
                "Invalid Time"
            }
        }


    }
}
