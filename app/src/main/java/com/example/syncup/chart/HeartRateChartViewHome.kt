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
            // Ambil drawable empty state (pastikan R.drawable.empty_image ada)
            val emptyDrawable = context.getDrawable(R.drawable.empty_image)
            // Konversi 60dp ke pixel (atau gunakan ukuran sesuai kebutuhan, misalnya 60dp)
            val sizePx = (100 * resources.displayMetrics.density).toInt()
            // Tempatkan gambar di tengah view
            val left = (width - sizePx) / 2
            val top = (height - sizePx) / 2
            emptyDrawable?.setBounds(left, top, left + sizePx, top + sizePx)
            emptyDrawable?.draw(canvas)
        } else {
            // Siapkan area untuk sumbu Y (misalnya, padding kiri)
            val leftPadding = 60f  // ruang untuk label sumbu Y
            val chartWidth = width - leftPadding

            // Gambar sumbu Y dan grid dengan tick di 0, 50, 100, 150
            val axisPaint = Paint().apply {
                color = Color.BLACK
                textSize = 30f
                textAlign = Paint.Align.RIGHT
            }
            val gridPaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 2f
            }
            val yTicks = listOf(0, 50, 100, 150)
            val minRate = 0f
            val maxRate = 150f
            for (tick in yTicks) {
                val y = height - ((tick - minRate) / (maxRate - minRate) * height)
                // Gambar garis grid horizontal
                canvas.drawLine(leftPadding, y, width.toFloat(), y, gridPaint)
                // Gambar label di sebelah kiri
                canvas.drawText(tick.toString(), leftPadding - 10, y + 10, axisPaint)
            }

            // Gambar diagram batang
            // Kita asumsikan jumlah maksimal data adalah maxDataPoints (50)
            val spacing = chartWidth / maxDataPoints.toFloat()
            // Tentukan lebar batang (misal 80% dari spacing)
            val barWidth = spacing * 0.8f

            // Siapkan Paint untuk batang
            val barPaint = Paint().apply {
                color = Color.RED
            }

            for (i in heartRateData.indices) {
                val value = heartRateData[i].toFloat()
                // Hitung tinggi batang sesuai skala (0 hingga 150)
                val barHeight = ((value - minRate) / (maxRate - minRate)) * height
                // Hitung posisi X: mulai dari leftPadding
                val x = leftPadding + i * spacing
                // Gambar batang dari (x, height - barHeight) ke (x + barWidth, height)
                canvas.drawRect(x, height - barHeight, x + barWidth, height.toFloat(), barPaint)
            }
        }
    }


}
