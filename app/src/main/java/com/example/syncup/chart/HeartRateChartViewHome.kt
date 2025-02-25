package com.example.syncup.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import com.example.syncup.R

class HeartRateChartViewHome @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val heartRateData = mutableListOf<Int>()
    private val highlightedIndices = mutableSetOf<Int>() // Indeks data baru yang di-highlight

    private val handler = Handler(Looper.getMainLooper())

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

    private val axisPaint = Paint().apply {
        color = Color.BLACK
        textSize = 30f
        textAlign = Paint.Align.RIGHT
    }

    private val gridPaint = Paint().apply {
        color = Color.LTGRAY
        strokeWidth = 2f
    }

    private val greenPaint = Paint().apply {
        color = Color.GREEN
    }

    private val redPaint = Paint().apply {
        color = Color.RED
    }

    private val maxDataPoints = 20
    private val minRate = 0f
    private val maxRate = 150f

    // **Konversi 80dp ke pixel**
    private val yAxisHeight = 100 * resources.displayMetrics.density

    fun addHeartRate(value: Int) {
        if (value != -1) {
            heartRateData.add(value)

            if (heartRateData.size > maxDataPoints) {
                heartRateData.removeAt(0)
            }

            val newIndex = heartRateData.size - 1
            highlightedIndices.add(newIndex) // Tandai sebagai data baru

            // **Setelah 1 detik, ubah ke warna merah**
            handler.postDelayed({
                highlightedIndices.remove(newIndex)
                invalidate()
            }, 1000)

            invalidate()
        }
    }

    fun clearChart() {
        heartRateData.clear()
        highlightedIndices.clear()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (heartRateData.isEmpty()) {
            val emptyDrawable = context.getDrawable(R.drawable.empty_image)
            val sizePx = (100 * resources.displayMetrics.density).toInt()
            val left = (width - sizePx) / 2
            val top = (height - sizePx) / 2
            emptyDrawable?.setBounds(left, top, left + sizePx, top + sizePx)
            emptyDrawable?.draw(canvas)
        } else {
            val leftPadding = 60f
            val chartWidth = width - leftPadding

            val topPadding = height - yAxisHeight

            val yTicks = listOf(0, 50, 100, 150)

            for (tick in yTicks) {
                val y = topPadding + ((maxRate - tick) / (maxRate - minRate) * yAxisHeight)
                canvas.drawLine(leftPadding, y, width.toFloat(), y, gridPaint)
                canvas.drawText(tick.toString(), leftPadding - 10, y + 10, axisPaint)
            }

            val spacing = chartWidth / maxDataPoints.toFloat()
            val barWidth = spacing * 0.8f

            for (i in heartRateData.indices) {
                val value = heartRateData[i].toFloat()
                val barHeight = ((value - minRate) / (maxRate - minRate)) * yAxisHeight
                val x = leftPadding + i * spacing

                // **Jika indeks ada di highlightedIndices, warna hijau, jika tidak merah**
                val paint = if (highlightedIndices.contains(i)) greenPaint else redPaint

                canvas.drawRect(x, topPadding + yAxisHeight - barHeight, x + barWidth, topPadding + yAxisHeight, paint)
            }
        }
    }
}
