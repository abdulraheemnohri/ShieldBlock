package com.example.shieldblock.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class AuraBackgroundView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var phase = 0f
    private var pulseSpeed = 10000L
    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = pulseSpeed
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            phase = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    fun setPulseSpeed(active: Boolean) {
        pulseSpeed = if (active) 4000L else 12000L
        animator.duration = pulseSpeed
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w == 0f || h == 0f) return

        val radius = w.coerceAtLeast(h) * 1.5f
        val centerX = w / 2f + (Math.sin(phase.toDouble() * 2 * Math.PI).toFloat() * 80f)
        val centerY = h / 2f + (Math.cos(phase.toDouble() * 2 * Math.PI).toFloat() * 80f)

        val gradient = RadialGradient(
            centerX, centerY, radius,
            intArrayOf(0xFF142F30.toInt(), 0xFF060E20.toInt()),
            null, Shader.TileMode.CLAMP
        )
        paint.shader = gradient
        canvas.drawRect(0f, 0f, w, h, paint)

        drawOrb(canvas, phase, 0.3f, 0.2f, 0.6f, 0xFF0F2A20)
        drawOrb(canvas, phase, 0.7f, 0.8f, 0.5f, 0xFF0A1F30)
    }

    private fun drawOrb(canvas: Canvas, p: Float, ox: Float, oy: Float, size: Float, color: Long) {
        val x = width * ox + (Math.sin((p + ox).toDouble() * 2 * Math.PI).toFloat() * 100f)
        val y = height * oy + (Math.cos((p + oy).toDouble() * 2 * Math.PI).toFloat() * 100f)
        val r = width * size
        val g = RadialGradient(x, y, r, color.toInt(), 0x00000000, Shader.TileMode.CLAMP)
        val pnt = Paint(Paint.ANTI_ALIAS_FLAG).apply { shader = g }
        canvas.drawCircle(x, y, r, pnt)
    }
}
