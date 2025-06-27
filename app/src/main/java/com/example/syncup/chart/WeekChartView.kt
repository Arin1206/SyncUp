package com.example.syncup.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class WeekChartView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint().apply {
        color = Color.parseColor("#484848")
        style = Paint.Style.FILL
    }

    private val nullBarPaint = Paint().apply {
        color = Color.GRAY
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
        textSize = 18f
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint().apply {
        color = Color.BLACK
        textSize = 16f
        textAlign = Paint.Align.CENTER
    }

    private var weekData: MutableMap<String, Int?> = mutableMapOf()

    fun setData(data: Map<String, Int>) {
        val filteredData = data.mapKeys { extractWeekNumber(it.key) }
            .mapValues { (_, value) ->
                if (value in 0..150) value else null
            }

        val allWeeks = (1..5).map { "Week $it" }
        weekData = allWeeks.associateWith { week -> filteredData[week] }.toMutableMap()

        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (weekData.isEmpty()) return

        val maxHeartRate = 150f
        val minHeartRate = 0f
        val chartWidth = width.toFloat() - 80f
        val chartHeight = height.toFloat() - 30f
        val axisY = chartHeight - 40f

        val barCount = 5
        val barWidth = 40f
        val barSpacing = 30f

        val totalChartWidth = (barCount * (barWidth + barSpacing))
        val startX = (chartWidth - totalChartWidth) / 2 + 50f
        canvas.drawLine(startX, axisY, startX + totalChartWidth, axisY, axisPaint)

        for (i in 0..3) {
            val y = axisY - (i * (axisY / 3.5f))
            canvas.drawLine(startX, y, startX + totalChartWidth, y, gridPaint)
            canvas.drawText("${i * 50}", startX - 20f, y + 5f, textPaint)
        }

        var xPosition = startX

        weekData.forEach { (week, avgHeartRate) ->
            val barHeight = if (avgHeartRate != null && avgHeartRate in minHeartRate.toInt()..maxHeartRate.toInt()) {
                ((avgHeartRate - minHeartRate) / (maxHeartRate - minHeartRate)) * (axisY - 50)
            } else {
                0f
            }

            val rect = RectF(xPosition, axisY - barHeight, xPosition + barWidth, axisY)
            val paint = if (avgHeartRate != null) barPaint else nullBarPaint

            canvas.drawRect(rect, paint)
            canvas.drawText(week, xPosition + barWidth / 2, axisY + 25, textPaint)

            if (avgHeartRate != null) {
                canvas.drawText("Avg", xPosition + barWidth / 2, axisY - barHeight - 25, labelPaint)
                canvas.drawText(
                    "$avgHeartRate",
                    xPosition + barWidth / 2,
                    axisY - barHeight - 5,
                    labelPaint
                )
            } else {
                canvas.drawText("null", xPosition + barWidth / 2, axisY - 10, textPaint)
            }

            xPosition += (barWidth + barSpacing)
        }
    }

    private fun extractWeekNumber(weekText: String): String {
        return weekText.substringBefore(" (")
    }
}
