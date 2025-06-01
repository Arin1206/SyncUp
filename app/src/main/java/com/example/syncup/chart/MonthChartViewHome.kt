package com.example.syncup.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import java.util.Locale

class MonthChartViewHome @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint().apply {
        color = Color.parseColor("#484848") // Warna ungu Telkom
        style = Paint.Style.FILL
    }

    private val nullBarPaint = Paint().apply {
        color = Color.GRAY // Warna abu-abu untuk bulan yang kosong
        style = Paint.Style.FILL
    }

    private val axisPaint = Paint().apply {
        color = Color.BLACK
        strokeWidth = 3f
        style = Paint.Style.STROKE
    }

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 1.5f
        style = Paint.Style.STROKE
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 12f // **Ukuran teks diperkecil agar semua muat**
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint().apply {
        color = Color.BLACK
        textSize = 10f // **Ukuran label lebih kecil**
        textAlign = Paint.Align.CENTER
    }

    private var monthData: MutableMap<String, Int?> = mutableMapOf()

    fun setData(data: Map<String, Int?>) {
        val filteredData = data.mapKeys { convertMonthToEnglish(it.key) }

        val calendar = Calendar.getInstance()
        val currentYear = calendar.get(Calendar.YEAR) // Tahun berjalan

        val last4Months = mutableListOf<String>()
        for (i in 0 until 4) {
            val tempCalendar = Calendar.getInstance().apply {
                time = calendar.time
                add(Calendar.MONTH, -i) // Mundur dari bulan sekarang
            }
            last4Months.add(getMonthNameByIndex(tempCalendar.get(Calendar.MONTH)))
        }

// Urutkan dari yang paling lama ke terbaru
        monthData = last4Months.reversed().associateWith { month -> filteredData[month] ?: null }.toMutableMap()

        invalidate() // Refresh tampilan grafik
    }




    private fun getMonthNameByIndex(index: Int): String {
        return listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")[index]
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val maxChartWidth = (130 * resources.displayMetrics.density).toInt() // Maksimum 130dp dalam pixel
        val maxChartHeight = (121 * resources.displayMetrics.density).toInt() // **Ubah maksimum tinggi menjadi 101dp**

        val calculatedWidth = (monthData.size * 50 * resources.displayMetrics.density).toInt() // Lebar menyesuaikan jumlah data
        val calculatedHeight = (150 * resources.displayMetrics.density).toInt() // Pastikan tinggi cukup untuk label

        val width = maxChartWidth // **Selalu gunakan max width 130dp**
        val height = minOf(maxChartHeight, calculatedHeight) // **Batas maksimal 101dp**

        setMeasuredDimension(width, height)
    }


    private fun getMonthIndex(month: String): Int {
        return when (month) {
            "Jan" -> 0
            "Feb" -> 1
            "Mar" -> 2
            "Apr" -> 3
            "May" -> 4
            "Jun" -> 5
            "Jul" -> 6
            "Aug" -> 7
            "Sep" -> 8
            "Oct" -> 9
            "Nov" -> 10
            "Dec" -> 11
            else -> -1 // Jika bulan tidak ditemukan
        }
    }



    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (monthData.isEmpty()) return

        val maxHeartRate = (monthData.values.filterNotNull().maxOrNull()?.toFloat() ?: 120f).coerceAtLeast(100f)
        val minHeartRate = 0f
        val leftPadding = 20f // **Tambahkan padding kiri agar angka sumbu Y tidak kepotong**
        val chartWidth = width.toFloat() - 20f  // **Tetap gunakan full width (130dp)**
        val chartHeight = height.toFloat() - 40f // **Tambahkan lebih banyak ruang di atas**
        val axisY = chartHeight - 40f // **Geser sumbu Y lebih ke bawah untuk memberi ruang label**
        val availableHeight = axisY - 10f // **Tambahkan batas atas agar 100 terlihat dengan jelas**

        val barCount = monthData.size
        val barWidth: Float
        val barSpacing: Float

        if (barCount < 4) {
            barWidth = (chartWidth / 4) - 15f
            barSpacing = 15f
        } else {
            val minBarWidth = 20f
            val maxBarWidth = 50f
            val totalSpacing = (barCount - 1) * 10f
            val availableWidth = chartWidth - totalSpacing
            barWidth = minOf(maxBarWidth, maxOf(minBarWidth, availableWidth / barCount))
            barSpacing = 10f
        }

        val totalChartWidth = (barCount * (barWidth + barSpacing))
        val startX = (chartWidth - totalChartWidth) / 2 + leftPadding // **Geser ke kanan dengan padding**

        // **Gambar Garis Sumbu X sepanjang max width**
        canvas.drawLine(leftPadding, axisY, width - 10f, axisY, axisPaint)

        // **Tambahkan lebih banyak grid pada sumbu Y**
        for (i in 0..4) {
            val y = axisY - (i * (availableHeight / 4)) // **Gunakan availableHeight untuk skala lebih presisi**
            canvas.drawLine(leftPadding, y, width - 10f, y, gridPaint) // **Garis grid juga sepanjang width**

            // **Pastikan label tidak terpotong di bagian atas**
            val labelY = if (i == 4) y - 3f else y + 5f
            canvas.drawText("${(i * maxHeartRate / 4).toInt()}", leftPadding - 10f, labelY, textPaint)
        }

        var xPosition = startX
        monthData.forEach { (month, avgHeartRate) ->
            val barHeight = if (avgHeartRate != null) {
                ((avgHeartRate - minHeartRate) / (maxHeartRate - minHeartRate)) * availableHeight
            } else {
                0f
            }

            val rect = RectF(xPosition, axisY - barHeight, xPosition + barWidth, axisY)
            val paint = if (avgHeartRate != null) barPaint else nullBarPaint

            canvas.drawRect(rect, paint)
            canvas.drawText(month, xPosition + barWidth / 2, axisY + 15, textPaint)

            if (avgHeartRate != null) {
                canvas.drawText("$avgHeartRate", xPosition + barWidth / 2, axisY - barHeight - 5, labelPaint)
            } else {
                canvas.drawText("null", xPosition + barWidth / 2, axisY - 10, textPaint)
            }

            xPosition += (barWidth + barSpacing)
        }
    }






    private fun convertMonthToEnglish(month: String): String {
        return when (month.lowercase(Locale.ENGLISH)) {
            "januari", "january" -> "Jan"
            "februari", "february" -> "Feb"
            "maret", "march" -> "Mar"
            "april" -> "Apr"
            "mei", "may" -> "May"
            "juni", "june" -> "Jun"
            "juli", "july" -> "Jul"
            "agustus", "august" -> "Aug"
            "september" -> "Sep"
            "oktober", "october" -> "Oct"
            "november" -> "Nov"
            "desember", "december" -> "Dec"
            else -> month // **Jika sudah dalam bahasa Inggris, biarkan tetap**
        }
    }
}
