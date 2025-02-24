package com.example.syncup.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.syncup.R

class HeartRateChartViewHome @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val heartRateData = mutableListOf<Int>()
    private val linePaint = Paint().apply {
        color = Color.RED
        strokeWidth = 5f
        style = Paint.Style.STROKE
    }
    private val emptyTextPaint = Paint().apply {
        color = Color.GRAY
        textSize = 40f
        textAlign = Paint.Align.CENTER
    }

    // Jumlah data maksimum yang ingin ditampilkan
    private val maxDataPoints = 50

    // Fungsi untuk menambahkan data heart rate
    fun addHeartRate(value: Int) {
        // Hanya tambahkan jika nilai valid (misalnya, bukan -1)
        if (value != -1) {
            heartRateData.add(value)
            if (heartRateData.size > maxDataPoints) {
                heartRateData.removeAt(0)
            }
            invalidate()
        }
    }

    // Fungsi untuk mengosongkan data chart (dipanggil saat GATT disconnected)
    fun clearChart() {
        heartRateData.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (heartRateData.isEmpty()) {
            val emptyDrawable = context.getDrawable(R.drawable.empty_image)
            // Konversi 60dp ke pixel
            val sizePx = (100 * resources.displayMetrics.density).toInt()
            // Tempatkan di tengah view
            val left = (width - sizePx) / 2
            val top = (height - sizePx) / 2
            emptyDrawable?.setBounds(left, top, left + sizePx, top + sizePx)
            emptyDrawable?.draw(canvas)
        } else {
            val spacing = width.toFloat() / (maxDataPoints - 1)
            val minRate = 30f
            val maxRate = 200f
            val scaleY = height.toFloat() / (maxRate - minRate)
            for (i in 0 until heartRateData.size - 1) {
                val x1 = i * spacing
                val y1 = height - ((heartRateData[i] - minRate) * scaleY)
                val x2 = (i + 1) * spacing
                val y2 = height - ((heartRateData[i + 1] - minRate) * scaleY)
                canvas.drawLine(x1, y1, x2, y2, linePaint)
            }
        }
    }


}
