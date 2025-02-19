package com.example.syncup.chart

import HealthData
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View

class HeartRateChartView(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var data: List<HealthData> = emptyList()
    private var viewWidth = 1f
    private var viewHeight = 1f

    private val paintLine = Paint().apply {
        color = Color.RED
        strokeWidth = 3f  // Lebih kecil agar lebih cocok untuk 100dp x 100dp
        isAntiAlias = true
    }
    private val paintPoint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun setData(newData: List<HealthData>) {
        data = newData
        invalidate()  // Refresh tampilan
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat().coerceAtLeast(1f)
        viewHeight = h.toFloat().coerceAtLeast(1f)
        postInvalidate()  // Memastikan tampilan diperbarui setelah perubahan ukuran
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (data.isEmpty()) return

        val widthStep = viewWidth / (data.size - 1).coerceAtLeast(1)
        val maxHeartRate = data.maxOf { it.heartRate }
        val minHeartRate = data.minOf { it.heartRate }
        val heightRange = (maxHeartRate - minHeartRate).coerceAtLeast(1)

        for (i in 0 until data.size - 1) {
            val x1 = i * widthStep
            val x2 = (i + 1) * widthStep

            val y1 = viewHeight - ((data[i].heartRate - minHeartRate) / heightRange.toFloat() * viewHeight)
            val y2 = viewHeight - ((data[i + 1].heartRate - minHeartRate) / heightRange.toFloat() * viewHeight)

            canvas.drawLine(x1, y1, x2, y2, paintLine)
            canvas.drawCircle(x1, y1, 3f, paintPoint)  // Ukuran titik lebih kecil
        }
    }
}
