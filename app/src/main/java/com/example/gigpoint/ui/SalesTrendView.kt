package com.example.gigpoint.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import com.example.gigpoint.R
import kotlin.math.max

class SalesTrendView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.chart_grid)
        strokeWidth = resources.displayMetrics.density
    }

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.primary)
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.text_muted)
        textSize = 12f * resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val values = mutableListOf<Float>()
    private var labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

    fun setData(newValues: List<Float>, newLabels: List<String> = labels) {
        values.clear()
        values.addAll(newValues.map { max(0f, it) })
        if (newLabels.isNotEmpty()) labels = newLabels
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val d = resources.displayMetrics.density
        val left = 14f * d
        val right = width - 14f * d
        val top = 14f * d
        val bottom = height - 30f * d

        if (right <= left || bottom <= top) return

        for (i in 0..3) {
            val y = top + (bottom - top) * i / 3f
            canvas.drawLine(left, y, right, y, gridPaint)
        }

        if (values.size < 2) {
            canvas.drawText(
                "Sales history will appear here",
                width / 2f,
                (top + bottom) / 2f,
                textPaint
            )
            return
        }

        val maxValue = values.maxOrNull()?.takeIf { it > 0f } ?: 1f
        val stepX = (right - left) / (values.size - 1)
        val path = Path()

        values.forEachIndexed { i, value ->
            val x = left + stepX * i
            val y = bottom - (value / maxValue) * (bottom - top)

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)

            canvas.drawCircle(x, y, 3.5f * d, pointPaint)

            labels.getOrNull(i)?.takeIf { it.isNotBlank() }?.let {
                canvas.drawText(it, x, height - 8f * d, textPaint)
            }
        }

        canvas.drawPath(path, linePaint)
    }
}
