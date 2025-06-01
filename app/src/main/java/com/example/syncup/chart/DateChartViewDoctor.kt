package com.example.syncup.chart


import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.syncup.search.PatientData

class DateChartViewDoctor(context: Context, attrs: AttributeSet?) : View(context, attrs) {

    private var data: List<PatientData> = emptyList()
    private var viewWidth = 1f
    private var viewHeight = 1f

    private val paintBar = Paint().apply {
        color = Color.parseColor("#4CAF50") // Hijau
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val paintText = Paint().apply {
        color = Color.BLACK
        textSize = 12f
        isAntiAlias = true
        textAlign = Paint.Align.CENTER
    }

    private val paintAxis = Paint().apply {
        color = Color.BLACK
        strokeWidth = 2f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    /**
     * Meng-update data dan me-refresh tampilan.
     */
    fun setData(newData: List<PatientData>) {
        data = newData
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w.toFloat().coerceAtLeast(1f)
        viewHeight = h.toFloat().coerceAtLeast(1f)
        postInvalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val leftMargin = 50f

        // Gambar sumbu-Y
        canvas.drawLine(leftMargin, 0f, leftMargin, viewHeight, paintAxis)
        // Gambar sumbu-X
        canvas.drawLine(leftMargin, viewHeight, viewWidth, viewHeight, paintAxis)

        // Label sumbu-Y (misalnya 0, 50, 100, 150)
        val paintAxisText = Paint(paintText).apply { textAlign = Paint.Align.RIGHT }
        val yValues = listOf(0, 50, 100, 150)
        yValues.forEach { value ->
            val yPos = viewHeight - (value / 150f) * viewHeight
            canvas.drawText(value.toString(), leftMargin - 10f, yPos, paintAxisText)
        }

        if (data.isEmpty()) return

        val availableWidth = viewWidth - leftMargin
        val barWidth = availableWidth / data.size

        data.forEachIndexed { index, item ->
            val heartRate = item.heartRate.toIntOrNull() ?: 0
            val clampedHeartRate = heartRate.coerceAtMost(150)
            val barHeight = (clampedHeartRate / 150f) * viewHeight

            val left = leftMargin + index * barWidth
            val right = left + barWidth * 0.8f
            val bottom = viewHeight
            val top = bottom - barHeight

            canvas.drawRect(left, top, right, bottom, paintBar)

            // Tampilkan nilai heart rate
            canvas.drawText(heartRate.toString(), left + (right - left) / 2, top - 4f, paintText)

            // Tampilkan nama pasien di bawah bar
            val label = item.name.split(" ").firstOrNull() ?: "Pasien"
            canvas.drawText(label, left + (right - left) / 2, bottom + 20f, paintText)
        }
    }
}
