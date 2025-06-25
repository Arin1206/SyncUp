package com.example.syncup.chart

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.example.syncup.R

class BarChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barPaint = Paint().apply {
        color = Color.parseColor("#D3D3D3")
        style = Paint.Style.FILL
    }


    var values: List<Float> = emptyList()
        private set

    fun setData(newValues: List<Float>) {
        values = newValues
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (values.isEmpty()) {
            val emptyDrawable = context.getDrawable(R.drawable.empty_image)
            val sizePx = (100 * resources.displayMetrics.density).toInt()
            val left = (width - sizePx) / 2
            val top = (height - sizePx) / 2
            emptyDrawable?.setBounds(left, top, left + sizePx, top + sizePx)
            emptyDrawable?.draw(canvas)
            return
        }


        val leftMargin = 50f
        val paintAxis = Paint().apply {
            color = Color.BLACK
            strokeWidth = 4f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }
        canvas.drawLine(leftMargin, 0f, leftMargin, height.toFloat(), paintAxis)
        canvas.drawLine(leftMargin, height.toFloat(), width.toFloat(), height.toFloat(), paintAxis)

        val maxVal = values.maxOrNull() ?: return
        val maxY = (maxVal.coerceAtLeast(140f) / 50).toInt() * 50

        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 28f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val steps = maxY / 50
        val heightWithoutMargin = height.toFloat()
        for (i in 0..steps) {
            val value = i * 50
            val yPos = heightWithoutMargin - (value / maxY.toFloat()) * heightWithoutMargin
            canvas.drawText(value.toString(), leftMargin - 10f, yPos + 10f, labelPaint)
            val gridPaint = Paint().apply {
                color = Color.LTGRAY
                strokeWidth = 1f
            }
            canvas.drawLine(leftMargin, yPos, width.toFloat(), yPos, gridPaint)
        }

        val availableWidth = width - leftMargin
        val spacing = availableWidth / values.size.toFloat()
        val barWidth = spacing * 0.8f

        val heightRatio = heightWithoutMargin / maxY

        for ((i, value) in values.withIndex()) {
            val left = leftMargin + i * spacing
            val top = heightWithoutMargin - value * heightRatio
            val right = left + barWidth

            canvas.drawRect(left, top, right, heightWithoutMargin, barPaint)

            canvas.drawText("P${i + 1}", (left + right) / 2, heightWithoutMargin + 40f, labelPaint)
        }
    }


}

