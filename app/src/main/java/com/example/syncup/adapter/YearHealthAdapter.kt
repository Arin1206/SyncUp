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
import com.example.syncup.model.YearHealthItem
import java.text.SimpleDateFormat
import java.util.*

class YearHealthAdapter(private var yearData: List<YearHealthItem>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_ITEM = 1
    }

    override fun getItemViewType(position: Int): Int {
        return if (yearData[position] is YearHealthItem.YearHeader) VIEW_TYPE_HEADER else VIEW_TYPE_ITEM
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when (viewType) {
            VIEW_TYPE_HEADER -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_year_header, parent, false)
                YearHeaderViewHolder(view)
            }
            else -> {
                val view = LayoutInflater.from(parent.context)
                    .inflate(R.layout.item_health_data, parent, false)
                YearViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val data = yearData[position]
        when (holder) {
            is YearHeaderViewHolder -> holder.bind(data as YearHealthItem.YearHeader)
            is YearViewHolder -> holder.bind(data as YearHealthItem.YearData)
        }
    }

    override fun getItemCount(): Int {
        return yearData.size
    }

    fun updateData(newData: List<YearHealthItem>) {
        yearData = newData
        notifyDataSetChanged()
    }

    class YearHeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val yearHeader: TextView = view.findViewById(R.id.tv_year_header)
        fun bind(item: YearHealthItem.YearHeader) {
            yearHeader.text = item.year
        }
    }

    class YearViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val avgHeartRate: TextView = view.findViewById(R.id.tv_heart_rate)
        private val avgBloodPressure: TextView = view.findViewById(R.id.tv_blood_pressure)
        private val avgBattery: TextView = view.findViewById(R.id.tv_battery)
        private val indicatorStatus: View = view.findViewById(R.id.indicator_status)
        private val tvTime: TextView = view.findViewById(R.id.tv_time)

        private val handler = Handler(Looper.getMainLooper())
        private val timeUpdateRunnable = object : Runnable {
            override fun run() {
                tvTime.text = getCurrentTime() // **Perbarui waktu setiap detik**
                handler.postDelayed(this, 1000) // **Update setiap 1 detik**
            }
        }

        fun bind(item: YearHealthItem.YearData) {
            avgHeartRate.text = "${item.avgHeartRate} BPM"  // **Tambahkan "bpm" setelah nilai heart rate**
            avgBloodPressure.text = item.avgBloodPressure
            avgBattery.text = "${item.avgBattery}%"  // **Tambahkan "%" setelah nilai battery**

            // **Set indikator warna berdasarkan heart rate**
            val statusColor = if (item.avgHeartRate in 60..100) Color.GREEN else Color.RED
            indicatorStatus.setBackgroundColor(statusColor)

            // **Mulai update waktu secara live**
            handler.removeCallbacks(timeUpdateRunnable) // **Pastikan tidak ada handler dobel**
            handler.post(timeUpdateRunnable)
        }

        // **Fungsi untuk mendapatkan waktu lokal perangkat dalam format HH:mm:ss (Locale Inggris)**
        private fun getCurrentTime(): String {
            val sdf = SimpleDateFormat("HH:mm:ss", Locale.ENGLISH)
            return sdf.format(Date())
        }
    }
}
