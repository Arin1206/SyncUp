package com.example.syncup.chart

import HealthData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import java.text.SimpleDateFormat
import java.util.Locale

class HeartRateChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var data: List<HealthData> = emptyList()
    private var viewWidth = 1f
    private var viewHeight = 1f

    // Paint untuk menggambar bar (diisi dengan warna merah)
    private val paintBar = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    // Paint untuk menampilkan teks (nilai heart rate dan label waktu)
    private val paintText = Paint().apply {
        color = Color.BLACK
        textSize = 12f  // Ukuran teks kecil
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    // Paint untuk menggambar garis sumbu X dan Y
    private val paintAxis = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    /**
     * Meng-update data yang akan digambar dan refresh tampilan.
     */
    fun setData(newData: List<HealthData>) {
        data = newData
        invalidate()  // Refresh tampilan
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat().coerceAtLeast(1f)
        viewHeight = h.toFloat().coerceAtLeast(1f)
        postInvalidate()  // Pastikan tampilan diperbarui setelah perubahan ukuran
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Tentukan margin kiri agar ada ruang untuk menampilkan tulisan rentang sumbu-Y
        val leftMargin = 50f

        // Gambar sumbu-Y (garis vertikal di sisi kiri, setelah margin)
        canvas.drawLine(leftMargin, 0f, leftMargin, viewHeight, paintAxis)
        // Gambar sumbu-X (garis horizontal di bagian bawah, mulai dari margin kiri)
        canvas.drawLine(leftMargin, viewHeight, viewWidth, viewHeight, paintAxis)

        // Gambar tulisan rentang pada sumbu-Y (misal: 0, 50, 100, 150)
        val paintAxisText = Paint(paintText).apply {
            textAlign = Paint.Align.RIGHT
        }
        val yValues = listOf(0, 50, 100, 150)
        yValues.forEach { value ->
            // Hitung posisi y untuk tiap nilai, dengan sumbu-Y 0 di atas dan 150 di bawah
            val yPos = viewHeight - (value / 150f) * viewHeight
            canvas.drawText(value.toString(), leftMargin - 10f, yPos, paintAxisText)
        }

        if (data.isEmpty()) return

        // Tentukan format label sumbu-X berdasarkan jumlah data:
        // jika data kurang dari 5, tampilkan "HH:mm"; jika tidak, tampilkan "HH".
        val labelFormat = if (data.size < 3) "HH:mm" else "HH"
        val timeFormatter = SimpleDateFormat(labelFormat, Locale.getDefault())
        // Parser untuk timestamp dengan format "yyyy-MM-dd HH:mm:ss"
        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        // Urutkan data dari yang paling awal ke terbaru berdasarkan timestamp
        val sortedData = data.sortedBy { it.timestamp }

        // Hitung lebar tiap bar berdasarkan sisa lebar setelah margin
        val availableWidth = viewWidth - leftMargin
        val barWidth = availableWidth / sortedData.size

        // Sumbu-Y tetap dari 0 sampai 150 (heart rate)
        sortedData.forEachIndexed { index, item ->
            // Parsing timestamp; jika gagal, label kosong
            val date = try {
                parser.parse(item.timestamp)
            } catch (e: Exception) {
                null
            }
            val timeLabel = date?.let { timeFormatter.format(it) } ?: ""

            // Clamp nilai heart rate maksimum ke 150 (jika data melebihi 150, tetap 150)
            val heartRate = item.heartRate.coerceAtMost(150)
            // Hitung tinggi bar berdasarkan proporsi nilai heart rate (0-150) terhadap tinggi view
            val barHeight = (heartRate / 150f) * viewHeight

            // Hitung koordinat bar (x disesuaikan dengan leftMargin)
            val left = leftMargin + index * barWidth
            // Gunakan 80% dari barWidth agar ada margin antar bar
            val right = left + barWidth * 0.8f
            val bottom = viewHeight
            val top = bottom - barHeight

            // Gambar bar dengan warna merah
            canvas.drawRect(left, top, right, bottom, paintBar)

            // Gambar nilai heart rate di atas bar (offset 4px di atas bar)
            canvas.drawText(item.heartRate.toString(), left + (right - left) / 2, top - 4f, paintText)

            // Gambar label waktu di bawah bar (misalnya, 20px di bawah sumbu-X)
            canvas.drawText(timeLabel, left + (right - left) / 2, bottom + 20f, paintText)
        }
    }
}
