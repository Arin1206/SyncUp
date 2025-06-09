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

    private val paintBar = Paint().apply {
        color = Color.GRAY
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

    fun setData(newData: List<HealthData>) {
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


        canvas.drawLine(leftMargin, 0f, leftMargin, viewHeight, paintAxis)

        canvas.drawLine(leftMargin, viewHeight, viewWidth, viewHeight, paintAxis)

        val paintAxisText = Paint(paintText).apply {
            textAlign = Paint.Align.RIGHT
        }
        val yValues = listOf(0, 50, 100, 150)
        yValues.forEach { value ->

            val yPos = viewHeight - (value / 150f) * viewHeight
            canvas.drawText(value.toString(), leftMargin - 10f, yPos, paintAxisText)
        }

        if (data.isEmpty()) return


        val labelFormat = if (data.size < 3) "HH:mm" else "HH"
        val timeFormatter = SimpleDateFormat(labelFormat, Locale.getDefault())

        val parser = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())


        val sortedData = data.sortedBy { it.timestamp }


        val availableWidth = viewWidth - leftMargin
        val barWidth = availableWidth / sortedData.size


        sortedData.forEachIndexed { index, item ->
            val date = try {
                parser.parse(item.timestamp)
            } catch (e: Exception) {
                null
            }
            val timeLabel = date?.let { timeFormatter.format(it) } ?: ""

            val heartRate = item.heartRate.coerceAtMost(150)

            val barHeight = (heartRate / 150f) * viewHeight

            val left = leftMargin + index * barWidth

            val right = left + barWidth * 0.8f
            val bottom = viewHeight
            val top = bottom - barHeight


            canvas.drawRect(left, top, right, bottom, paintBar)

            canvas.drawText(
                item.heartRate.toString(),
                left + (right - left) / 2,
                top - 4f,
                paintText
            )

            canvas.drawText(timeLabel, left + (right - left) / 2, bottom + 20f, paintText)
        }
    }
}
