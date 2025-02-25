package com.example.syncup.adapter

import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.syncup.R
import com.example.syncup.model.MonthHealthItem
import java.text.SimpleDateFormat
import java.util.*

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
        private val indicatorStatus: View = view.findViewById(R.id.indicator_status)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)

        private val handler = Handler(Looper.getMainLooper()) // **Handler untuk update waktu live**
        private val timeUpdateRunnable = object : Runnable {
            override fun run() {
                tvTime.text = getCurrentTime() // **Perbarui waktu setiap detik**
                handler.postDelayed(this, 1000) // **Jalankan ulang setiap 1 detik**
            }
        }

        fun bind(item: MonthHealthItem.MonthData) {
            avgHeartRate.text = item.avgHeartRate.toString()
            avgBloodPressure.text = item.avgBloodPressure
            avgBattery.text = "${item.avgBattery}%"

            // **Set indikator warna berdasarkan heart rate**
            val statusColor = if (item.avgHeartRate in 60..100) {
                Color.GREEN  // **Healthy**
            } else {
                Color.RED  // **Danger**
            }
            indicatorStatus.setBackgroundColor(statusColor)

            // **Mulai update waktu secara live**
            handler.removeCallbacks(timeUpdateRunnable) // **Pastikan handler tidak dobel**
            handler.post(timeUpdateRunnable)
        }

        // **Fungsi untuk mendapatkan waktu lokal perangkat dalam format HH:MM:SS**
        private fun getCurrentTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
