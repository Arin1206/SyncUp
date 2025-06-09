package com.example.syncup.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import java.util.Calendar

class YearChartView @JvmOverloads constructor(
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
        textSize = 12f
        textAlign = Paint.Align.CENTER
    }

    private val labelPaint = Paint().apply {
        color = Color.BLACK
        textSize = 10f
        textAlign = Paint.Align.CENTER
    }

    private var yearData: MutableMap<String, Int?> = mutableMapOf()

    fun setData(data: Map<String, Int>) {
        val currentYear = getCurrentYear().toInt()

        val availableYears = data.keys.map { it.toInt() }.filter { it <= currentYear }.sorted()

        val allYears = if (availableYears.isEmpty()) {
            listOf(currentYear.toString())
        } else {
            availableYears.map { it.toString() }
        }

        yearData = allYears.associateWith { year -> data[year] }.toMutableMap()

        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (yearData.isEmpty()) return

        val maxHeartRate = yearData.values.filterNotNull().maxOrNull()?.toFloat() ?: 150f
        val minHeartRate = 0f
        val chartWidth = width.toFloat() - 50f
        val chartHeight = height.toFloat() - 20f
        val axisY = chartHeight - 40f

        val barCount = yearData.size
        val barWidth = 30f
        val barSpacing = 10f

        val totalChartWidth = (barCount * (barWidth + barSpacing))
        val startX = (chartWidth - totalChartWidth) / 2 + 25f

        canvas.drawLine(startX, axisY, startX + totalChartWidth, axisY, axisPaint)

        for (i in 0..3) {
            val y = axisY - (i * (axisY / 3.5f))
            canvas.drawLine(startX, y, startX + totalChartWidth, y, gridPaint)
            canvas.drawText("${i * 50}", startX - 15f, y + 5f, textPaint)
        }

        var xPosition = startX

        yearData.forEach { (year, avgHeartRate) ->
            val barHeight = if (avgHeartRate != null) {
                ((avgHeartRate - minHeartRate) / (maxHeartRate - minHeartRate)) * (axisY - 50)
            } else {
                0f
            }

            val rect = RectF(xPosition, axisY - barHeight, xPosition + barWidth, axisY)
            val paint = if (avgHeartRate != null) barPaint else nullBarPaint

            canvas.drawRect(rect, paint)
            canvas.drawText(year, xPosition + barWidth / 2, axisY + 15, textPaint)

            if (avgHeartRate != null) {

                canvas.drawText(
                    "$avgHeartRate bpm",
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

    private fun getCurrentYear(): String {
        return Calendar.getInstance().get(Calendar.YEAR).toString()
    }
}
