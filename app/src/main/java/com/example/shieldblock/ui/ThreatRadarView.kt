package com.example.shieldblock.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class ThreatRadarView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val radarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF86FEA7.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    private val scanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val blipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var rotation = 0f
    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 4000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotation = it.animatedValue as Float
            invalidate()
        }
    }

    private val threats = mutableListOf<ThreatPoint>()

    data class ThreatPoint(val x: Float, val y: Float, var alpha: Int = 255, val isBlocked: Boolean, var radius: Float = 5f)

    init {
        animator.start()
    }

    fun addThreat(isBlocked: Boolean) {
        val angle = Math.random() * 2 * Math.PI
        val dist = 0.1 + Math.random() * 0.7
        val x = 0.5f + (Math.cos(angle) * dist * 0.5f).toFloat()
        val y = 0.5f + (Math.sin(angle) * dist * 0.5f).toFloat()
        threats.add(ThreatPoint(x, y, 255, isBlocked))
        if (threats.size > 30) threats.removeAt(0)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val maxRadius = Math.min(cx, cy) * 0.9f

        // Draw background circles with glow
        for (i in 1..3) {
            radarPaint.alpha = 40 / i
            canvas.drawCircle(cx, cy, maxRadius * (i / 3f), radarPaint)
        }

        // Draw crosshair
        radarPaint.alpha = 30
        canvas.drawLine(cx - maxRadius, cy, cx + maxRadius, cy, radarPaint)
        canvas.drawLine(cx, cy - maxRadius, cx, cy + maxRadius, radarPaint)

        // Draw sweep with better gradient
        val sweepGradient = SweepGradient(cx, cy, intArrayOf(
            0x0086FEA7.toInt(),
            0x2286FEA7.toInt(),
            0x8886FEA7.toInt()
        ), floatArrayOf(0f, 0.95f, 1f))
        val matrix = Matrix()
        matrix.setRotate(rotation - 10, cx, cy)
        sweepGradient.setLocalMatrix(matrix)
        scanPaint.shader = sweepGradient
        canvas.drawCircle(cx, cy, maxRadius, scanPaint)

        // Draw scan line head
        val headX = cx + (Math.cos(Math.toRadians(rotation.toDouble())).toFloat() * maxRadius)
        val headY = cy + (Math.sin(Math.toRadians(rotation.toDouble())).toFloat() * maxRadius)
        radarPaint.alpha = 150
        canvas.drawLine(cx, cy, headX, headY, radarPaint)

        // Draw threats with expanding effect
        val iterator = threats.iterator()
        while (iterator.hasNext()) {
            val t = iterator.next()
            val tx = width * t.x
            val ty = height * t.y

            blipPaint.color = if (t.isBlocked) 0xFFFF5555.toInt() else 0xFF86FEA7.toInt()
            blipPaint.alpha = t.alpha

            // Draw main blip
            canvas.drawCircle(tx, ty, t.radius, blipPaint)

            // Draw outer ring for "pinged" threats
            if (t.alpha > 200) {
                blipPaint.style = Paint.Style.STROKE
                blipPaint.strokeWidth = 2f
                canvas.drawCircle(tx, ty, t.radius * 2, blipPaint)
                blipPaint.style = Paint.Style.FILL
            }

            t.alpha -= 4
            t.radius += 0.1f
            if (t.alpha <= 0) iterator.remove()
        }
    }
}
