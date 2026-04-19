package com.example.shieldblock.ui

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class CoreOscilloscopeView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF86FEA7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val dataPoints = FloatArray(50) { 0f }
    private var head = 0

    fun addValue(value: Float) {
        dataPoints[head] = value.coerceIn(0f, 1f)
        head = (head + 1) % dataPoints.size
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val path = Path()
        val step = w / (dataPoints.size - 1)

        for (i in 0 until dataPoints.size) {
            val index = (head + i) % dataPoints.size
            val x = i * step
            val y = h - (dataPoints[index] * h * 0.8f) - (h * 0.1f)

            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }

        canvas.drawPath(path, linePaint)

        // Draw fill
        path.lineTo(w, h)
        path.lineTo(0f, h)
        path.close()

        val gradient = LinearGradient(0f, 0f, 0f, h, 0x4486FEA7.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        fillPaint.shader = gradient
        canvas.drawPath(path, fillPaint)
    }
}
