package com.example.shieldblock.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import android.view.animation.LinearInterpolator

class AegisCoreView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFF86FEA7.toInt()
    }

    private var rotation = 0f
    private var isActive = false
    private var flashAlpha = 0f

    private val animator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 3000
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            rotation = it.animatedValue as Float
            invalidate()
        }
    }

    fun triggerFlash() {
        ValueAnimator.ofFloat(1f, 0f).apply {
            duration = 500
            addUpdateListener { flashAlpha = it.animatedValue as Float; invalidate() }
            start()
        }
    }

    fun setActive(active: Boolean) {
        isActive = active
        if (active) animator.start() else animator.cancel()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val radius = Math.min(cx, cy) * 0.8f

        if (isActive) {
            canvas.save()
            canvas.rotate(rotation, cx, cy)
            drawRings(canvas, cx, cy, radius)
            canvas.restore()

            canvas.save()
            canvas.rotate(-rotation * 1.5f, cx, cy)
            drawRings(canvas, cx, cy, radius * 0.7f)
            canvas.restore()
        } else {
            ringPaint.alpha = 50
            canvas.drawCircle(cx, cy, radius, ringPaint)
        }

        if (flashAlpha > 0) {
            val flashPaint = Paint().apply {
                color = 0xFF86FEA7.toInt()
                alpha = (flashAlpha * 100).toInt()
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, radius * 1.2f, flashPaint)
        }
    }

    private fun drawRings(canvas: Canvas, cx: Float, cy: Float, r: Float) {
        ringPaint.alpha = 200
        val path = Path()
        for (i in 0..3) {
            val angle = i * 90f
            path.addArc(cx - r, cy - r, cx + r, cy + r, angle, 60f)
        }
        canvas.drawPath(path, ringPaint)
    }
}
